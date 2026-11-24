# Physical Android acceptance: Samsung and phone clusters

The user authorized ADB testing of a connected Samsung on 2026-10-06. This is
independent of consent to root, optional permissions, autonomy or OS-wide changes.
Do not uninstall existing apps, clear data, bypass a signing conflict or silently
alter the user's policy. Inspect hardware and installed app identity first.

## Current connection evidence

macOS USB identifies `SAMSUNG_Android`, Samsung vendor `04e8`, product `6860`;
a vendor interface with class 255/subclass 66/protocol 1 is present. ADB 37.0.1
currently lists only the API35 emulator. The actual Mac ADB server log reports:
`usb_osx.cpp:168 Unable to create an interface plug-in (e00002be)`.
No Samsung model/OS/RAM has been read through ADB and no Samsung generation test
is claimed. USB visibility is not an authorized usable ADB transport.

The owner was asked to restart ADB from a normal macOS/Android Studio terminal
and accept its phone authorization prompt. An alternative is an owner-provided
wireless-debugging IP/port with normal Android pairing. See
[Android's official ADB guide](https://developer.android.com/tools/adb).
Do not bypass the managed tool environment to access USB.

## Test sequence once connected

1. Select the explicit device ID, verify Samsung manufacturer/model, Android API,
   supported ABIs, chipset/board, `/proc/meminfo`, app-storage free space, battery
   and thermal readings. Exclude hardware serial numbers and account data from
   exported evidence. Compare the APK's min API24 and native ABI before install.
2. Inspect whether `com.meshlit.debug` is installed. Use `adb install -r` with
   the matching APK. A signing conflict is a stop condition, not authorization
   to uninstall/clear data. Install the matching V1 test APK separately.
3. Run the actual bundled test:
   `am instrument -w -r -e class com.meshlit.models.InstalledModelSmokeTest#bundledModelLoadsAndGeneratesRealTokens com.meshlit.debug.test/androidx.test.runner.AndroidJUnitRunner`.
   Capture runner results and app-only native logs: the pinned model checksum,
   actual load, generated nonempty text and unload. A timeout/blocked policy is
   failure evidence. A skip cannot count as a pass.
4. For two-model routing, explicitly copy the actual public TinyStories test
   artifact into this debug app's managed `files/imported-models/smoke-test.gguf`,
   preserving any existing different file. SHA-256 must be
   `66967fbece6dbe97886593fdbb73589584927e29119ec31f08090732d1861739`.
   Then run `configuredRouterRunsTwoRealLocalModels`. This is **sequential model
   routing on one phone**, not layer sharding. Record both actual model IDs.
5. Launch the app and inspect only Meshlit's screens: boot/optional setup,
   model status, chat/stop, Models import/download/load/unload, settings search,
   dynamic palette, router editor, local behavior and fine-tuning unavailable
   state. Persist/relaunch settings and verify real backend behavior.
6. Use explicit public model transfers for interruption/resume, hash mismatch,
   low-storage and duplicate-import checks. Preserve user-owned files. Measure
   real render/input response, app memory, battery/thermal readings and startup.
   No fabricated speed, power or benchmark scores.
7. Optional microphone/camera, QR, Bluetooth and Accessibility tests each require
   their normal platform grants. Agent, root, VM and paid/cloud settings remain
   opt-in; do not enable them merely to make a smoke test pass.
8. Layer-sharding acceptance requires at least **two physical compute devices**,
   reviewed native workers and actual allocations; a genuinely oversized model
   must execute across them. Match deterministic token output to a suitable
   baseline, then test stop, loss/rejoin and thermal/resource failures.
9. Replicated recovery needs committed journals, fenced coordinator epochs and
   actual checkpoint/KV compatibility. Demonstrate one/two-node crash recovery;
   restarting a whole request or repeating a prompt is not recovered KV state.

Record results in `PROGRESS.md` and the external validation directory. Keep
emulator, desktop RPC, physical-phone and actual Soup training evidence separate.
