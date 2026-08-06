// vt_parser.h — Native VT byte-pump + dispatcher.
//
// Designed to be a drop-in replacement for the Kotlin Parser/Dispatch
// pair. Output is a packed `int32_t` action buffer that the JVM side
// decodes in a tight loop. The hot path allocates nothing.
//
// Action layout (little-endian int32 words):
//
//   word 0  : kind   (one of ActionKind)
//   word 1  : aux0   (kind-specific)
//   word 2  : aux1
//   word 3  : aux2
//   word 4  : n_params   (number of valid ints in the params tail)
//   word 5  : param[0]
//   word 6  : param[1]
//   ...
//   word N  : Color::END_MARKER  (terminator)
//
// For CsiAction: aux0 = final byte, aux1 = intermediate[0]
//   (aux2 reserved for extended modes), params = numeric CSI args.
// For OscAction: aux0 = cmd, params = UTF-16LE code units of text.
// For DcsAction: aux0 = final byte, aux1 = intermediate[0],
//                params = numeric DCS args, then UTF-16 text.
// For EscAction: aux0 = final byte, aux1 = intermediate[0].
//
// The END_MARKER is `0x7FFFFFFF`. The Kotlin decoder treats any int
// matching that as a terminator.

#pragma once

#include <cstdint>
#include <cstring>

namespace vt {

// Action kinds — must match the Kotlin values.
enum class ActionKind : int32_t {
    CSI = 1,
    OSC = 2,
    DCS = 3,
    ESC = 4,
};

// Output buffer grows on demand. The parser calls `push_*` helpers
// which auto-grow when capacity is reached. When `borrowed` is true
// the buffer was constructed with an external pointer (e.g. a JNI
// DirectByteBuffer); `reserve()` will fall back to a heap copy if
// the caller-supplied capacity is exceeded.
struct ActionBuffer {
    int32_t* data = nullptr;
    int32_t  capacity = 0;
    int32_t  length = 0;
    bool     borrowed = false;

    ActionBuffer() = default;
    explicit ActionBuffer(int32_t* external, int32_t external_cap)
        : data(external), capacity(external_cap), length(0), borrowed(true) {}

    void reserve(int32_t extra) {
        if (length + extra <= capacity) return;
        int32_t new_cap = capacity == 0 ? 64 : capacity;
        while (new_cap < length + extra) new_cap *= 2;
        int32_t* grown = new int32_t[new_cap];
        if (data != nullptr) {
            std::memcpy(grown, data, length * sizeof(int32_t));
        }
        if (!borrowed && data != nullptr) {
            delete[] data;
        }
        data = grown;
        capacity = new_cap;
        borrowed = false;
    }

    void push_int(int32_t v) {
        reserve(1);
        data[length++] = v;
    }

    void push_kind(ActionKind k) {
        push_int(static_cast<int32_t>(k));
    }

    void push_terminator() {
        push_int(0x7FFFFFFF);
    }
};

// Parse a stream of bytes. Appends zero or more actions to `out`.
// `out` should be empty on entry; the parser flushes `push_terminator()`
// at the end of every action.
void parse(const uint8_t* bytes, int32_t length, ActionBuffer& out);

}  // namespace vt