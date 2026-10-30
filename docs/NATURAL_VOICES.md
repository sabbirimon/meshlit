# Natural voice choices
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-31: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

2026-10-31. Source increment; each execution path has separate evidence.

## Available paths

- **Installed Android voice / Piper:** existing local phone execution.
- **Kokoro / Pocket — free local host:** OpenLive synthesizes on an owner-selected
  computer; Android receives PCM and plays it. The chat LLM can stay on the phone.
  No speech API fee; CPU, storage and electricity are still used. This is not
  native Kokoro/Pocket execution on Android.
- **ElevenLabs — free-tier / paid:** a dedicated API adapter using `xi-api-key`,
  voice discovery and PCM output. It requires the owner's account/key. No real
  ElevenLabs call was made during implementation.
- **Other compatible speech providers:** the existing OpenAI-format speech
  connection remains available. A configured endpoint is not proof of a free plan.

The desktop Voice and vision dashboard reads installed Kokoro/Pocket variants and
voices from OpenLive and plays real local previews, with Stop. Models are installed
explicitly in OpenLive Settings. The pinned OpenLive source is documented in
[voice setup](VOICE_AND_VISION.md). Keep its separate model licences: the tested
Kokoro English INT8 variant reports Apache-2.0; Pocket's current ONNX archive
reports CC-BY-4.0 with non-commercial export terms. Weights are external optional
installs, not included in the APK, Git tree or an Apache-only redistribution.

## Android setup

1. Run the pinned OpenLive source through the Meshlit development companion:
   `node companions/voice/serve.cjs /absolute/openlive /absolute/node`.
   It binds only loopback, uses `~/.openlive` for persisted settings/models and
   installs nothing automatically. Ctrl+C stops its owned processes.
   For a USB-only development connection:
   `adb -s YOUR_SERIAL reverse tcp:47834 tcp:47834` while the web service listens
   on host loopback port 47834. For other devices use your authenticated HTTPS
   endpoint/tunnel; do not expose the unauthenticated development server on LAN.
2. In Online providers, create a **dedicated speech connection** using the existing
   compatible-profile credential store. Use `http://127.0.0.1:47834/api/voice` for
   that USB tunnel, model `kokoro-en-v0_19-int8`, and no API key only for local
   unauthenticated loopback. Enable the profile. This is a speech connection,
   not an OpenAI chat server; do not select it for chat or model catalog checks.
3. Keep recognition on an installed offline Whisper pack or Android recognizer.
   These new connections are synthesis-only; the single existing speech connection
   field does not yet support a separate online STT provider at the same time.
   Chat → microphone → tune icon → Voice reply → Kokoro / Pocket. Select the
   connection, explicitly allow network audio, and load engines/voices. Only
   server-reported installed/runnable engines are selectable. Review the licence,
   select a voice and play the real sample. Local speed tuning is supported.
4. For ElevenLabs, instead create a dedicated profile with base
   `https://api.elevenlabs.io/v1`, the desired real TTS model ID, and your API key.
   Select ElevenLabs in voice settings, load your account's voices and select one.
   The profile's compatible-protocol field is only reused for encrypted credential
   storage; the selected speech adapter defines the actual wire protocol.

**Free-tier-only is on by default for ElevenLabs.** Before each synthesis request,
Meshlit reads the provider's subscription, requires tier `free`, and checks its
reported character count/limit. Missing account-read permission, unknown allowance,
paid tier or exhausted allowance blocks synthesis. This is not a billing cap or
reservation against concurrent apps, plan changes or model-specific credit costs.
Turning the setting off explicitly permits your configured paid account to be used.
There is no automatic paid fallback. Keys stay in encrypted provider storage;
voices, model choices and the free-only preference persist locally. These speech
calls remain outside the chat token ledger.

Official references: [ElevenLabs free API offering](https://join.elevenlabs.io/api/developer-api),
[subscription schema](https://elevenlabs.io/docs/api-reference/user/subscription/get),
[speech endpoint and PCM formats](https://elevenlabs.io/docs/api-reference/text-to-speech/convert).
Other vendors' free credits are not unlimited: [HF pricing](https://huggingface.co/docs/inference-providers/en/pricing)
and [Google TTS pricing](https://cloud.google.com/text-to-speech/pricing). Those
native speech protocols have not been added in this increment.

## Validation

The local-host qualification tool installs the two explicit reviewed variants,
generates known-text samples, validates finite Float32 audio and writes WAVs:

```sh
node companions/studio-runtime/qualify-natural-voices.cjs /absolute/openlive /absolute/data-home /absolute/output
```

The Android NaturalVoiceDeviceTest uses the owner USB tunnel to exercise real
Kokoro/Pocket HTTP synthesis and Android playback. It does not use a microphone,
change provider credentials, call an LLM or contact a paid provider. Direct
Android-native Kokoro/Pocket generation and ElevenLabs account qualification
remain separate gates. [Executed evidence](desktop/evidence/natural-voices-20261031.json): 802 core/app
tests and 37 Studio tests passed. Both debug APK variants assembled; full lints
have zero errors, with existing warnings. Samsung SM-A207F/API30 fetched real
Kokoro/Pocket synthesis through the USB tunnel and completed Android playback.
Host execution was CPU; acceleration benchmark timeouts are retained in the
evidence and do not qualify GPU or realtime latency. The development launcher
started both installed engines' catalog successfully and shut down cleanly.
The final V2 ARM64 APK was installed with owner data preserved. Temporary test
servers and the reverse port mapping were removed. ElevenLabs was contract-tested
without contacting a real account; live verification needs the owner's key.
