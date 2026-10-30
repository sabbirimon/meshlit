# Meshlit voice companion
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-31: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../../docs/DOCUMENTATION_STATUS.md).
Scope: optional host/schema/reference; not a bundled qualified service. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

The current companion uses pinned OpenLive source, installed outside this tree.
See [setup and the Studio tuning dashboard](../../docs/VOICE_AND_VISION.md).

Run an isolated real-runtime smoke test after installing dependencies and
preparing VAD assets:

```sh
node companions/studio-runtime/qualify-voice.cjs /absolute/path/to/openlive /absolute/path/to/node /tmp/meshlit-voice-evidence.json
```

The test starts loopback web/agent processes with a disposable `OPENLIVE_HOME`,
reads providers/settings, saves and reads back tuning, requests the web page,
stops both owned process groups and checks that the services are gone. It
never requests model generation, media capture or speech model downloads.
Real audio/vision quality and browser permission behavior require separate checks.

Natural voices: [Kokoro/Pocket and free-tier provider setup](../../docs/NATURAL_VOICES.md).
To run the pinned development companion without the workbench:

```sh
node companions/voice/serve.cjs /absolute/openlive /absolute/node
```

It uses `~/.openlive`, remains on loopback, starts no downloads and stops owned
servers with Ctrl+C. This is a development launcher, not a packaged installer.
