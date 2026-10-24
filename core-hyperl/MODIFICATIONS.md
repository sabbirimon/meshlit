# Android integration boundary

Pinned upstream: `sabbirimon/HyperL` at
`c03a8590d959f5480c54efb4e3c3f87af6532e39` (development alpha.6).
Newly covered rights in this module use HyperL Community and Enterprise License
1.0. Earlier Apache grants remain available; root Meshlit Apache code is unchanged
by that licence. Read LICENSE, NOTICE, Apache-2.0.txt and LICENSE_HISTORY.md.
The APK bundles these texts offline; enabling this workbench requires an explicit
installation-local opt-in, independently of the main Meshlit agreement.

Modifications: Android namespace; Java-8-compatible IO and API 26 NIO dataset
boundary; bounded SAF streaming import and independent verification; Android
C99/JNI packaging and cancellation bridge. Pinned native C99 CPU/ABI sources are
unmodified. `docs/hyperl/ALPHA6_PROVENANCE.json` records import and adapted hashes.
The old Apache `core-gpu` port and its provenance remain intact.

The app does not bundle the standalone desktop workbench, fonts, Python wheels,
OpenJDK, OpenCL/Metal runtime bridges, vendor SDKs or CUDA/HIP executors. Preserved
upstream NOTICE identifies that upstream distribution's components; it does not
mean every component is included in Android. This module performs preprocessing
and encrypted datasets, not LLM inference or distributed model execution.
