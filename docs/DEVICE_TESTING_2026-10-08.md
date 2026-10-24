# Samsung single-phone qualification — 2026-10-08

Device: Galaxy A20s SM-A207F, Android 11/API 30, ARM64, Adreno 506,
Vulkan 1.1.128. Non-root testing. Both USB and owner-enabled TLS wireless ADB
reach the same device. Device serials, network addresses, pairing codes, model
weights, APKs and private screenshots are excluded from this source record.

This record distinguishes app execution from the standalone HyperL GPU runner.
One phone cannot establish distributed layer execution or failover. Debug-build
measurements are observations on this device, not production performance claims.

## Transport evidence

USB and TLS wireless each passed one warmup and five scoped ADB shell
request/response checks. Measurements include Mac subprocess creation, ADB and
Android shell handling; they are not pure Wi-Fi latency or model performance.
The wireless path uses an explicitly selected network endpoint rather than
implicitly selecting the attached USB transport. Cable-removal, network-loss
and pairing-revocation acceptance remain separate checks.

The five host-to-shell samples had median wall times of 89.748 ms over USB and
172.200 ms over TLS wireless (ranges 87.761–100.726 and 120.764–296.520 ms).
These include host overhead during concurrent builds and do not measure pure
network round-trip time. V2 installed successfully over USB and V1 over TLS Wi-Fi.

## Actual app evidence and retained failures

| Check | Observed result | Scope |
| --- | --- | --- |
| Earlier V2 app-UID HyperL CPU run | Passed, 1 instrumentation test in 4.267 seconds | All 12 recipes match exact reference values; one warmup and five samples per size |
| Earlier V2 workbench UI run | Partial; failed at reduction-source message capitalization | Drawer, settings search, validation, CPU weighted ReLU/sum and Metal/Vulkan source were reached; assertion corrected before rerun |
| Earlier SDK/native benchmark | Failed at memory admission before measured generation | Harness incorrectly reloaded a resident model; now waits for startup and unloads before admission. Admission thresholds are preserved |
| Earlier native smoke attempt | Could not start: V2 target package absent | No native/checkpoint/TLS success is inferred; V1 remained installed |
| Earlier activity launch timing | `am start -W` timed out even though the visible app opened | Not a valid successful startup benchmark or proof of an app crash |
| Build 18 UI/engine repeats | Failed/incomplete | The device had securely locked/aslept; UI found no usable Compose hierarchy and engine wait timed out. These are not successful acceptance evidence |
| Actual V2 model download/Load/Send/Unload over USB | Passed, 1 instrumentation test in 87.464 seconds | Fresh HTTPS transfer, complete GGUF size/hash verification and non-empty actual SDK reply; wireless repeat passed |

The earlier HyperL samples time JSON parsing, graph planning and CPU execution
together. They use weighted ReLU, compact JSON input and a 16 MiB array budget.
They are not GPU timings, compiler timings or isolated kernel throughput.

| Elements | Samples | Median wall time | Range |
| ---: | ---: | ---: | ---: |
| 3 | 5 | 8.334 ms | 6.909–8.501 ms |
| 257 | 5 | 8.613 ms | 6.256–14.355 ms |
| 4,096 | 5 | 30.937 ms | 30.695–83.041 ms |

During that run the app process PSS was approximately 258 MiB at the initial
sample, available device RAM approximately 923 MiB, battery 80%, charging,
battery temperature 38°C and Android thermal status 0. Battery temperature is
not a GPU temperature sensor. App PSS excludes a separate owned native process.

The separate standalone ADB-shell Vulkan runner passed eleven finite-output
cases and two expected overflow rejections. That proves those runner cases on
Adreno 506; Meshlit's HyperL screen currently executes CPU reference recipes
and exports kernel source. It does not dispatch mobile GPU inference.

The standalone runner also repeated all thirteen cases successfully over each
transport with temporary-file cleanup. For 65,536 elements, USB native pipeline
creation/submit-wait were 16.6934/2.58333 ms and TLS wireless 17.0516/2.72365 ms.
ADB command wall times were 240.884/686.041 ms respectively. These are individual
observations; native compute is separate from ADB overhead and GPU timestamps.

The updated V2 USB model UI run used ARM64 Full debug APK SHA-256
`2cd52bf8cc72f91eda937c525be9ba9ed1692075aaf18be384823ae10649759f`.
It downloaded and verified all 105,454,432 bytes, loaded that downloaded path
with RunAnywhere, generated 20 non-empty output characters, and unloaded it.
Download/validation/install took 64,262.274 ms, UI load 4,004.961 ms,
Send-through-completion 928.277 ms and unload 2,601.397 ms. These are one
observation per action, not steady-state latency or measured token throughput.
The phone's HTTPS download uses its own internet connection independently of
the selected ADB transport. Report SHA-256:
`e88f5bbead8e000ab0abe1566c698325142d55dff316bd892009fe8a6e894481`.

## Reproduce app checks

Build the real bundled model and Full debug APK/test APK. Install with
`adb -s PHONE_TARGET install --no-incremental -r APK_PATH`, preserving the
original signing identity. Have the owner review and accept the current app
Terms and Privacy in its UI. Instrumentation does not accept them for the owner.

Use a USB target or a verified paired wireless target with the same command:

```sh
adb -s PHONE_TARGET shell am instrument -w -r \
  -e class com.meshlit.qualification.DeviceCoreBenchmarkTest \
  com.meshlit.v2.debug.test/androidx.test.runner.AndroidJUnitRunner

adb -s PHONE_TARGET shell am instrument -w -r \
  -e class com.meshlit.qualification.WorkbenchUiDeviceTest \
  com.meshlit.v2.debug.test/androidx.test.runner.AndroidJUnitRunner

adb -s PHONE_TARGET shell am instrument -w -r \
  -e class com.meshlit.pipeline.NativeAndroidSmokeTest \
  com.meshlit.v2.debug.test/androidx.test.runner.AndroidJUnitRunner

# Set TRANSPORT to usb or wireless to label the report; PHONE_TARGET must select
# that actual ADB transport. Downloads use the phone's own internet connection.
adb -s PHONE_TARGET shell am instrument -w -r \
  -e transport TRANSPORT \
  -e class com.meshlit.qualification.ModelDownloadUiDeviceTest \
  com.meshlit.v2.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The engine harness uses the actual bundled artifact/hash, context 512 and
16 output tokens: one warmup plus three measured requests for each available
SDK/native CPU engine. It checks cancellation after an actual text callback
and performs a recovery request. Text callbacks/characters are not token counts.
Unknown SDK token counts remain null. No chat output is broadcast or exported.

The UI suite checks navigation, editors, Validate, Run CPU, source modes,
reduction rejection, invalid numeric input/budget, Back, empty settings search,
permission Manage buttons, administration-button visibility and Models/Monitor/
Chat navigation. An idle disabled Stop button does not establish cancellation
of an active operation. Permission assertions do not grant OS access or remove
personal apps.

The model UI test starts a new transfer of SmolLM2 135M Instruct Q4_K_M from
the pinned public Hugging Face revision. It verifies the complete 105,454,432-byte
GGUF and SHA-256 `2e8040ceae7815abe0dcb3540b9995eaa1fa0d2ca9e797d0a635ae4433c68c2d`
before pressing Load. The actual RunAnywhere SDK must load that downloaded path;
the Chat Send button must produce a non-empty assistant response, then Unload
must release the model. Its 16-token request limit is not a measured token count.
The test leaves its downloaded artifact available, pauses only its own transfer
on failure, and restores the owner-selected chat/model in cleanup. Test-specific
16-token options are not selected for ordinary follow-up chat. Each transport's report includes
download/validation, loading, completion and unloading wall times. A new transfer
is used for each run; a bundled/cached model cannot satisfy download evidence.

Private reports are stored under the app's `files/qualification` directory
and extracted to an owner-controlled local output folder. They contain bounded
measurement metadata, not prompts, generated text, credentials or a device serial.

## Changes under test and remaining acceptance

Permission Manage controls remain visible for granted features; Android 11
notifications explicitly report that no runtime prompt is required. Local chat
now distinguishes on-device execution from internet connectivity and offers
default-off bounded web-page/phone tools. APK/install/removal/other-app permission
requests open Android human confirmation/settings; silent administration and an
in-app wireless ADB client are not implemented. See
[setup, privacy and limits](LOCAL_MODEL_TOOLS.md).

Remaining: larger tool-capable model planning quality; configured HTTPS crawler
retrieval/citations; consented Accessibility action/Stop/revoke; disposable APK
install/remove completion and expired file grants; sustained thermal/battery
measurements; actual two-phone sharding and network-loss recovery; live cloud/SSH
accounts; signed production/store distribution and independent security review.


## Completed model and native checks; chat restoration

The build-13 TLS wireless model UI repeat passed one test in 89.760 seconds.
Download/verification took 64,773.603 ms, Load 4,377.197 ms, Send/completion
883.583 ms and Unload 2,682.119 ms. Its full report SHA-256 is
`30744ad0856f665c3220e784f4276123b0213b464ad2b992c40bc88a3516ed15`.
Both transport runs used the same exact pinned weights and actual RunAnywhere
CPU execution. They do not establish GPU model inference or generation quality.

Native Android smoke passed separately over USB (18.837s) and TLS wireless
(19.126s). Listed cases cover actual packaged CPU generation, pinned/authenticated
TLS worker admission including a wrong token, context/KV configuration, cache reuse,
local encrypted checkpoint restore and rejection of wrong context/tampering.
These single-phone checks do not establish distributed failover.

The UI qualification's 16-token/history-zero chat had remained selected, explaining
the unexpectedly short subsequent replies. The source now persists owner selection
and restores the selected owner chat/model after model qualification. Normal chat
defaults remain 1,024 requested output tokens and ten history messages. The owner
removed V2, requested reinstall, accepted the notices manually and confirmed it
works. That fresh build-13 local chat produced 1,009 assistant characters with
normal defaults. Length is not correctness or a model-quality score; no cloud
profile, route or web/phone tool was selected for that reply. The previous recent
chat surface/font/composer was retained; the old Android Studio reply mechanism
was not restored.

The build-13 combined SDK/native benchmark failed after SDK samples: a raw native
completion was empty. Its revised harness uses the known smoke prompt but has not
yet completed a successful combined run. Build-18 CPU recipes passed again, while
its engine wait timed out on the locked device. No complete engine benchmark or
steady-state token rate is published from those failures.

## New optional features under qualification

Source now includes a searchable phone/wide-window workspace, themes/cards/rows,
restored four phone tabs, selected-model capability details, scoped client keys,
encrypted optional memory/personality/native retry, and separate offline/online
speech adapters. Required final lint/Play Review and actual unlocked-device checks
are recorded independently. Offline speech packs are verified free/research
upstream artifacts, not APK-bundled weights; no prerecorded audio check establishes
a live microphone conversation. Real provider audio accounts are unavailable.

Private outputs retain exact APK/report hashes. Model weights, APKs, reference
audio, phone addresses, screenshots and serials are not added to source. The
Samsung is currently securely locked; the owner has been asked to unlock it.
No lockscreen or legal permission bypass is attempted.

The build-24 prerecorded speech test reached verified import of the actual
Whisper/Piper packs, then failed at recognition load with `no registered backend
serves the requested primitive`. The packaged ONNX libraries had not been
registered alongside the existing llama.cpp text engine. Registration is now
explicit in the separate speech runtime; subsequent runs below qualify that
correction. The failed run is retained and is not transcription/synthesis evidence.
The owner-selected chat model was not deliberately replaced, and the test did
not access the microphone, a speech service or a hosted audio account.

Build 25 passed the real prerecorded recognition assertion, then failed while
validating synthesis output. The SDK returned its generic float PCM format
instead of the requested signed PCM16. Build 27 also returned `AUDIO_FORMAT_PCM`
at 22,050 Hz despite an explicit WAV request (283,648 bytes). The pinned 0.20.12
adapter now requests the actual float PCM primitive and converts finite,
little-endian samples into bounded signed PCM16 before playback. New contracts
check signed samples, clipped peaks, non-finite/partial samples, rates and duration.
A strengthened check also requires actual local LLM generation while both speech
models are resident, followed by successful speech cleanup. Failed builds 25/27
remain separate from the subsequent successful run.

## Recorded offline speech and local LLM coexistence — build 28

The V2 app-UID USB test passed one test in **25.803 seconds** with actual checked
Whisper Tiny English INT8 and Piper Lessac medium ONNX packs. It recognized the
upstream 6.625-second, 16 kHz mono reference recording and synthesized a new
22,050 Hz mono reply: 136,858 valid signed PCM16 bytes with nonzero energy.
The exact already-loaded local LLM produced a non-empty reply while both speech
models were resident; separate speech unload preserved its loaded path. A
successful report was written only after cleanup.

| Action | One observed wall time |
| --- | ---: |
| Recognition model load | 3,752.811 ms |
| Recorded audio transcription | 5,243.436 ms |
| Synthesis model load | 8,475.593 ms |
| Short reply synthesis | 2,876.407 ms |
| Local LLM request with both speech models loaded | 2,039.229 ms |

These are individual debug-build observations, not sustained latency, microphone
latency, token throughput, voice quality or GPU inference evidence. The test
explicitly records microphone/live conversation/provider testing as false.
V2 APK SHA-256: `9d23bbbe5c6fd6edbb84edc18faf894d6e7d476fc7b38b12ebf5c9158d0a511e`;
test APK: `db83d5c2516ceeb4fc9a53ecfbc0a431c1ac394e6b32d052232a7bb3b834b471`;
private report: `c416e7d7d96c1f00de52aa58e17f52078db76ab4576bfafcf14efe0cd3df5e3b`.
The retained voice packs remain available to the owner. Their commercial licensing,
other languages/speakers, actual mic/playback/Stop/UI and hosted audio remain
separate acceptance gates.

## Final local checks — build 29

The required core MCP/sandbox/network/inference and both Full app unit suites,
both Full APKs, V2 instrumentation APK, Full lint and Play Review APK/lint passed
in **18m11s**. Total targeted JVM evidence is 832 cases with zero failures, errors
or skips; unchanged common/GPU suites retain their prior results. Full lint has
zero fatal/errors, 364 warnings and 18 hints per flavor. Play Review lint has
zero fatal/errors, 367 warnings and 18 hints. The Full APK hashes are identical
to retained build 28, including the V2 app used for the recorded speech test.

Review static checks pass: target SDK 36, forbidden permissions/services absent,
policy assets present, and all 23 ARM64 native ELF libraries aligned to 16 KiB.
This does not establish runtime 16 KiB, production signing or Play approval.
Twelve benchmark-wire checks and nine crawler checks pass; the crawler run uses
the existing project virtual environment. The initial system Python attempt
lacked FastAPI and failed. Feature-map and six-file HyperL provenance checks pass.

USB remains connected, but the Samsung is securely locked/asleep. The owner has
been asked to unlock it. The earlier paired wireless endpoint currently refuses
connections and discovery returns no service; no new wireless speech result is
claimed. Live microphone, audible playback quality, Stop/background/revocation
and new interactive UI acceptance remain pending. No secure lockscreen bypass or
automatic microphone grant was performed.

## Reply presentation and token controls — builds 32–35

The owner unlocked the Samsung for the next tests. The shared phone/wide UI now
removes the four app bottom tabs; Android's system navigation remains. Monitor,
Networking, SSH, Labs, Models and other destinations remain in the sidebar.
Assistant replies use native Markdown blocks, readable theme typography, tables,
selectable code with Copy/Wrap/Scroll, explicit data charts and a full response
reader with outline/search. These rendering changes do not replace the model,
its system prompt, history or local inference engine.

Build 35 passed both Full app unit suites/APKs, V2 instrumentation APK, Full lint
and Play Review APK/lint in **19m33s**. The retained targeted JVM set totals 856
cases with zero failures, errors or skips; unchanged common/GPU/core results are
retained separately. The installed V2 APK SHA-256 is
`500b94afd80c91b8ad58ee4348ae962b4de83a8ba0c283a647c2977b1dd18176`;
test APK is `faaab547a1aa9e14eaf1fd5e31e290f60c88dd1bccc507037126cccb097ad483`.
Bundled pinned GGUF and offline HTML guide equality were verified in both Full
APKs.

Two actual app-UID USB tests passed in **27.701 seconds**: ten presentation/settings
checks and six native renderer checks. The settings test exercises Cancel,
Save, persisted output ceiling/speed-display changes, reopening and restoration
of the owner's settings. Unknown SDK context capacity is displayed as unknown,
rather than zero or a guessed capacity. The renderer uses an explicitly labelled
Markdown/chart fixture to test UI formatting, Copy, Wrap/Scroll, table, chart,
outline and reader navigation; fixture values are not live weather/model evidence.
Screenshots were pulled and visually inspected. An earlier build-33 UI test failed
because its switch selector matched four controls; that failure does not establish
Save/persistence success. Build 35 uses unique selectors and verifies persistence.

## Fresh real model download and generation — build 34

A separate USB UI test passed in **92.336 seconds**, pressing Add URL, Download,
Load, Send and Unload. The phone freshly downloaded all **105,454,432 bytes** of
SmolLM2 135M Instruct Q4_K_M from pinned Hugging Face revision
`09816acd5d99df7be770d85ea30822623dab342c`, then verified full SHA-256
`2e8040ceae7815abe0dcb3540b9995eaa1fa0d2ca9e797d0a635ae4433c68c2d`.
It generated a non-empty local reply. The test restored the selected chat/model
and options; its real qualification chat remains inspectable.

| Action | One observed wall time |
| --- | ---: |
| Download, checksum validation and installation | 67,961.512 ms |
| UI Load through completion | 4,220.591 ms |
| UI Send through completion | 879.225 ms |
| UI Unload through completion | 2,539.884 ms |

The output ceiling was 16 tokens. The report records 20 output **characters**,
which do not establish generated-token counts or tokens/second. These are single
debug-build wall times, not sustained throughput, quality, thermal or GPU inference
benchmarks. Download uses the phone's Internet independently of the ADB transport.
Previously recorded wireless tests remain historical; no fresh wireless result is
implied by this USB test. Live microphone, hosted speech/search and a physical
second-phone cluster still require separate acceptance evidence.

## Final search, reply and model checks — build 37

The final affected-source build passed in **27m25s**: both Full app unit suites,
APKs and lint, V2 instrumentation APK, and V2 Play Review APK/lint. The retained
targeted JVM set is **887 cases, zero failures/errors/skips**; unchanged common/GPU
results remain prior evidence, rather than newly rerun tests. Full lint has zero
errors, 366 warnings and 18 hints per flavor; V2 Review has zero errors, 369
warnings and 18 hints. The Review static check passes for target SDK 36, legal
asset equality, removed forbidden permissions/services and 23 ARM64 native ELF
libraries aligned to 16 KiB. Production signing, runtime 16 KiB and Play acceptance
are still separate gates.

Three actual app-UID USB UI tests pass in **41.072 seconds**: ten presentation/
settings checks, six labelled renderer checks, and eleven local search checks.
Global search opens the actual token setting, shows Manual fallback for an
unmeasured Automatic cluster, searches workspace destinations and displays every
category. Chat search uses the owner's saved conversation, exercises Previous/
Next and preserves the selected chat/options. Web access remains gated; no key,
credential, web request or human grant was changed. Screenshots were pulled and
visually inspected. Build 36 failed because Web was clipped outside a horizontal
category row; build 37 wraps categories and verifies each one is displayed.

V1 APK SHA-256: `7a2a2d013d65652552be605e5b9f7867187ec669adc6eec204204c2f87743d42`;
V2: `d29f083e4f515dbe10bd17afce54c9106015fef19802cbeed52ab6198b5045fe`;
instrumentation: `d9a81806b0b191f01c13f1dbc54cd2220e80853d4bbd088083895e0baff58637`;
ARM64 Review: `03650c05f6b63dcd968dc71b29b2351b98a52f602ea7fc201841e3c14687a5e5`.
The V2 debug certificate SHA-256 remains
`1e9e5cf146c9a4cb7035d79b1eced1c0b298b0ca0d9080a887c8ab085f1ddf60`.
Both Full APKs retain the pinned model and byte-identical offline HTML guide.

A fresh build-37 USB model UI test passes in **87.193 seconds**, downloading and
hashing the full pinned 105,454,432-byte GGUF, then Load, Send and Unload. One
observation: download/validation/install **63,329.948 ms**, Load **4,600.956 ms**,
Send-to-completion **925.263 ms**, Unload **2,768.615 ms**. The qualification ceiling
is 16 tokens and the report records 20 characters, not an authoritative token
count. No tokens/second is inferred. The owner's selected model/chat/options are
restored; the temporary stay-awake preference is returned to its original value.

Wireless discovery currently exposes no service, so this increment has USB
evidence only. Live Brave/provider execution, approved second-device settings,
physical multi-phone throughput and live microphone/playback remain unrun here.
Fixture provider/remote replies and cluster-policy tests do not establish them.
