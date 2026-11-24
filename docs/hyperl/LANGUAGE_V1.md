# HyperL v1 implementation and compatibility

HyperL is original Apache-2.0 code in `core-gpu`, focused on AI/security/vision
compute foundations. `hyperl/1` is a small versioned kernel IR, not yet a complete
CUDA substitute, tensor compiler, cryptographic library or industry standard.

JSON programs contain `format`, `inputs`, ordered `instructions`, and `output`.
See `examples/hyperl/elementwise.json`. Names are validated ASCII identifiers,
inputs must be defined before use, outputs are single-assignment, and unknown
operations/versions fail. CPU semantics: finite f32 one-dimensional vectors;
add/multiply require equal lengths, ReLU clamps at zero, sum reduces in input
order. There is no implicit broadcasting, fast-math, recursion, host I/O or
arbitrary code. Eight inputs, 64 instructions, 262,144 elements per input and a
conservative 1,048,576-element retained vector budget bound execution. Temporary
copies/output also consume memory; the vector budget is not total process RSS.
Cancellation is checked at instruction boundaries and every 1024 elements.

`HyperLRuntime` executes only explicitly registered backends. The Kotlin CPU
reference is the only installed executor. The source emitter generates fused
elementwise C99, CUDA C++, HIP C++ and OpenCL C (not a SPIR-V binary). Symbols
are sanitized, kernel names/argument bindings are fixed, inputs are sorted by
name, and generated pointwise intermediates stay scalar inside the loop/thread.
Sum lowering is explicitly unavailable. Each generated intermediate has a finite-range
check: a rejected intermediate is written to the output as a nonfinite marker and
that element stops. This prevents fusion hiding overflow behind a later ReLU. Native
callers must reject the entire result if any marker is present; partial outputs must
not be accepted. These checks may affect optimization and require measured profiling.
Callers must validate f32 inputs, shapes,
allocation/aliasing, numerical outputs and cancellation before integrating a
native executor. The host test compiles generated C with installed Clang and
runs an actual executable; it does not qualify NVIDIA/AMD or Android hardware.

Proposed LTS rule: retain v1 decoding/semantics and conformance fixtures; use a
new major format for breaking changes and never accept an unknown version by
silent approximation. SDK/compiler/runtime revisions and binary ABI are separate
from language version. A versioned format is not a funded multi-year support
commitment. StableHLO's own compatibility policy does not transfer to HyperL.

Security: established AES-GCM/ChaCha20-Poly1305/TLS/auth providers belong behind
key-isolating adapters. Existing Meshlit Android credentials use Keystore-backed
encrypted preferences; hardware key backing is device-dependent. Keys must never
be submitted in ordinary HyperL JSON buffers, model prompts, logs or gateway
results. Post-quantum/hybrid support needs separately qualified libraries and
protocol interoperability; it is not implemented by these arithmetic kernels.

Future native backend admission needs artifact hash/signature, license review,
owner enablement, exact OS/ABI/driver/runtime compatibility and conformance evidence.
A descriptor predicate is not an executable plugin loader. Proprietary SDK use
is permitted only by its license; no automatic download, root elevation or driver
installation is provided. GPU/NPU/FPGA speed and low footprint require actual
measurements. ARM/x86-64/RISC-V are portability targets; installers and execution
proof remain platform-specific.

The current Gradle harness is the Meshlit Android base (`core-gpu`); the HyperL IR,
reference and emitter classes are pure Kotlin but no independent distribution has
been packaged. Standalone compiler/runtime extraction is an explicit next milestone.
C99 host execution evidence is distinct from the Kotlin reference and Android app.
