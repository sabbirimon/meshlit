// vt_parser.cpp — Byte-pump state machine. Mirrors the Kotlin Parser.
//
// States:
//   Ground       — printables go straight out, C0 controls are
//                  dispatched via `vt_c0`, ESC switches to Escape.
//   Escape       — ESC <final> dispatches an ESC action; ESC [ goes
//                  to CsiEntry, ESC ] to OscString, ESC P/X/^/_ to
//                  DcsEntry, ESC ( ) * + $ / goes back to Ground
//                  (charset designation — no-op).
//   CsiEntry      — accumulates numeric params + intermediate bytes
//                  until a final byte in 0x40..0x7E dispatches.
//   OscString    — reads digits into osc_cmd, then semicolon, then
//                  accumulates text until BEL or ESC \.
//   DcsEntry     — accumulates data until ST or BEL.
//
// The hot path is the Ground state, which is ~5 instructions per byte.
// Every state machine is a switch on the byte; no allocations in the
// loop body except for the rare OSC text payload growth (the Kotlin
// side copies the OSC text into a String at dispatch time).

#include "vt_parser.h"

#include <cstdint>
#include <cstring>

namespace vt {

enum class State : uint8_t {
    Ground = 0,
    Escape = 1,
    CsiEntry = 2,
    OscString = 3,
    DcsEntry = 4,
};

// CSI accumulator. Mirrors Parser.CsiAction.
struct CsiAccum {
    int32_t params[64] = {};
    int32_t param_groups[16] = {};   // index into params[] where each group starts
    int32_t n_groups = 0;
    int32_t cur_start = 0;
    int32_t cur_count = 0;
    char    intermediate[4] = {};
    int32_t n_intermediate = 0;
    char    final_byte = 0;
};

static void csi_flush_param(CsiAccum& a) {
    if (a.cur_count == 0) {
        // Empty group — emit a default param value of 0.
        if (a.n_groups < 16 && a.cur_start + a.cur_count < 64) {
            a.params[a.cur_start + a.cur_count++] = 0;
        }
    }
    if (a.n_groups < 16) {
        a.param_groups[a.n_groups++] = a.cur_start;
    }
    a.cur_start = a.cur_start + a.cur_count;
    a.cur_count = 0;
}

static void csi_push_digit(CsiAccum& a, int32_t digit) {
    if (a.cur_count == 0) {
        if (a.cur_start + a.cur_count < 64) {
            a.params[a.cur_start + a.cur_count++] = 0;
        }
    }
    if (a.cur_count > 0) {
        int32_t idx = a.cur_start + a.cur_count - 1;
        a.params[idx] = a.params[idx] * 10 + digit;
    }
}

static void csi_emit(ActionBuffer& out, const CsiAccum& a) {
    out.push_kind(ActionKind::CSI);
    out.push_int(static_cast<uint8_t>(a.final_byte));
    out.push_int(a.n_intermediate > 0 ? a.intermediate[0] : 0);
    out.push_int(0);  // reserved
    out.push_int(a.n_groups);  // payload header = number of groups
    for (int32_t g = 0; g < a.n_groups; ++g) {
        int32_t start = a.param_groups[g];
        int32_t end = (g + 1 < a.n_groups) ? a.param_groups[g + 1] : a.cur_start;
        int32_t count = end - start;
        out.push_int(count);
        for (int32_t i = 0; i < count; ++i) {
            out.push_int(a.params[start + i]);
        }
    }
    out.push_terminator();
}

void parse(const uint8_t* bytes, int32_t length, ActionBuffer& out) {
    State st = State::Ground;
    CsiAccum csi;

    // OSC accumulators. Text payload is stored as UTF-16LE in the
    // output buffer, so we have to track the byte length separately.
    int32_t osc_cmd = 0;
    bool    osc_cmd_done = false;
    int32_t osc_text_chars[4096] = {};
    int32_t osc_text_len = 0;

    // DCS accumulators.
    char    dcs_final = 0;
    int32_t dcs_params[64] = {};
    int32_t dcs_param_groups[16] = {};
    int32_t dcs_n_groups = 0;
    int32_t dcs_cur_start = 0;
    int32_t dcs_cur_count = 0;
    int32_t dcs_text_chars[4096] = {};
    int32_t dcs_text_len = 0;

    auto osc_emit = [&](bool ground_after) {
        out.push_kind(ActionKind::OSC);
        out.push_int(osc_cmd);
        out.push_int(0);
        out.push_int(0);
        out.push_int(osc_text_len);
        for (int32_t i = 0; i < osc_text_len; ++i) out.push_int(osc_text_chars[i]);
        out.push_terminator();
        osc_cmd = 0;
        osc_cmd_done = false;
        osc_text_len = 0;
        if (ground_after) st = State::Ground;
    };

    auto dcs_emit = [&](bool ground_after) {
        out.push_kind(ActionKind::DCS);
        out.push_int(static_cast<uint8_t>(dcs_final));
        out.push_int(0);
        out.push_int(0);
        out.push_int(dcs_text_len);
        for (int32_t i = 0; i < dcs_text_len; ++i) out.push_int(dcs_text_chars[i]);
        out.push_terminator();
        dcs_final = 0;
        dcs_n_groups = 0;
        dcs_cur_start = 0;
        dcs_cur_count = 0;
        dcs_text_len = 0;
        if (ground_after) st = State::Ground;
    };

    for (int32_t bi = 0; bi < length; ++bi) {
        uint8_t b = bytes[bi];
        switch (st) {
            case State::Ground:
                if (b == 0x1B) {
                    st = State::Escape;
                } else if (b <= 0x17 || b == 0x19) {
                    // C0 control — dispatched by the JVM side via
                    // applyC0 on the cursor, not through the action
                    // buffer. We drop the byte here.
                    (void)b;
                } else if (b >= 0x20) {
                    // Printable. Encoded as OSC-like: kind=PRINT,
                    // aux0 = codepoint. But for parity with the
                    // existing Kotlin putChar path we instead expose
                    // it as an OSC text chunk of length 1. The Kotlin
                    // side interprets ActionKind::OSC with cmd=-1 as
                    // "this is plain text". For simplicity here we
                    // emit a CSI aux=PRINT marker.
                    //
                    // Actually — emit a dedicated PRINT action kind:
                    // (we reuse CSI final = 0xFF to mean "PRINT").
                    out.push_kind(ActionKind::CSI);
                    out.push_int(0xFF);  // marker for "print char"
                    out.push_int(b);
                    out.push_int(0);
                    out.push_int(0);
                    out.push_terminator();
                }
                break;

            case State::Escape:
                if (b == 0x1B) {
                    // ESC ESC — start over.
                    st = State::Ground;
                } else if (b == '[') {
                    st = State::CsiEntry;
                    csi = CsiAccum{};
                } else if (b == ']') {
                    st = State::OscString;
                    osc_cmd = 0;
                    osc_cmd_done = false;
                    osc_text_len = 0;
                } else if (b == 'P' || b == 'X' || b == '^' || b == '_') {
                    st = State::DcsEntry;
                    dcs_final = ' ';
                    dcs_n_groups = 0;
                    dcs_cur_start = 0;
                    dcs_cur_count = 0;
                    dcs_text_len = 0;
                } else if (b == '(' || b == ')' || b == '*' || b == '+' ||
                           b == '$' || b == '/') {
                    // Charset designation — drop the next byte and
                    // return to Ground. We don't track which byte was
                    // consumed; the JVM doesn't act on it.
                    st = State::Ground;
                } else if (b >= 0x20) {
                    out.push_kind(ActionKind::ESC);
                    out.push_int(static_cast<uint8_t>(b));
                    out.push_int(0);
                    out.push_int(0);
                    out.push_int(0);
                    out.push_terminator();
                    st = State::Ground;
                } else {
                    st = State::Ground;
                }
                break;

            case State::CsiEntry:
                if (b == 0x1B) {
                    st = State::Escape;
                    csi = CsiAccum{};
                } else if (b >= '0' && b <= '9') {
                    csi_push_digit(csi, b - '0');
                } else if (b == ';') {
                    csi_flush_param(csi);
                } else if (b == ':') {
                    csi_flush_param(csi);  // sub-param — fold into ';'
                } else if (b >= 0x20 && b <= 0x2F) {
                    if (csi.n_intermediate < 3) {
                        csi.intermediate[csi.n_intermediate++] = static_cast<char>(b);
                    }
                } else if (b >= 0x3C && b <= 0x3F) {
                    // Private-marker chars (`<`, `=`, `>`, `?`).
                    if (csi.n_intermediate < 3) {
                        csi.intermediate[csi.n_intermediate++] = static_cast<char>(b);
                    }
                } else if (b >= 0x40 && b <= 0x7E) {
                    csi.final_byte = static_cast<char>(b);
                    if (csi.cur_count > 0 || csi.n_groups == 0) {
                        // Always emit at least one (possibly empty)
                        // param group so the dispatcher can use
                        // defaults uniformly.
                        if (csi.cur_count == 0 && csi.n_groups == 0) {
                            // Inject empty group with default 0.
                            csi.params[csi.cur_start] = 0;
                            csi.cur_count = 1;
                        }
                        if (csi.cur_count > 0) csi_flush_param(csi);
                    }
                    csi_emit(out, csi);
                    st = State::Ground;
                }
                // Other bytes (C0, 0x7F, >=0x80) are ignored.
                break;

            case State::OscString:
                if (b == 0x1B) {
                    osc_emit(false);
                    st = State::Escape;
                } else if (b == 0x07) {
                    osc_emit(true);
                } else if (!osc_cmd_done && b >= '0' && b <= '9') {
                    osc_cmd = osc_cmd * 10 + (b - '0');
                } else if (!osc_cmd_done && b == ';') {
                    osc_cmd_done = true;
                } else if (b >= 0x20) {
                    if (!osc_cmd_done) osc_cmd_done = true;
                    if (osc_text_len < 4096) {
                        osc_text_chars[osc_text_len++] = b;
                    }
                }
                break;

            case State::DcsEntry:
                if (b == 0x1B) {
                    dcs_emit(false);
                    st = State::Escape;
                } else if (b == 0x07) {
                    dcs_emit(true);
                } else if (b >= 0x20) {
                    if (dcs_text_len < 4096) {
                        dcs_text_chars[dcs_text_len++] = b;
                    }
                }
                break;
        }
    }

    // Flush partial OSC.
    if (st == State::OscString && osc_text_len > 0) {
        osc_emit(true);
    }
}

}  // namespace vt