# Third-party notices for current additions

- SSH: `com.github.mwiede:jsch:2.28.7`, maintained JSch fork.
  Repository https://github.com/mwiede/jsch ; BSD-style notice retained at
  `app/src/main/assets/licenses/JSch-BSD.txt`. No Termius application source copied.
- Bundled SmolLM2 135M Instruct Q4_K_M: publisher/revision/content hash and
  Apache-2.0 notices in `app/src/main/assets/models/`.
- Offline CodeMirror: notices in `app/src/main/assets/ide/THIRD_PARTY_NOTICES.txt`.
- RunAnywhere, llama.cpp, OpenClaw, browser and crawler companions: preserve
  upstream notices and consult `docs/runanywhere-browser-and-llama.md`,
  `docs/openclaw-and-android-autonomy.md` and companion READMEs before distribution.
- SDR/DSP/Reticulum and proprietary vendor backends listed in current plans have
  not been bundled. Review pinned dependency licenses before implementing them.

These additions do not replace each dependency's full license or upstream NOTICE.
Do not copy GPL application/source into this Apache tree without resolving the
project licensing policy. Build outputs/model binaries remain excluded from Git.

Soup 0.75.0 is an optional, owner-installed Apache-2.0 host dependency; no upstream
Soup training code or weights are bundled in this APK. The SSH supervisor is
original Meshlit source. Uncensored-AI/Heretic (AGPL-3.0), the referenced Flutter
app and USB launcher are research references only; none of their code is copied.
See `docs/local-model-behavior-and-training.md`.

## CommonMark reply parsing

`org.commonmark:commonmark` and `commonmark-ext-gfm-tables` are pinned to 0.30.0.
Upstream: https://github.com/commonmark/commonmark-java, BSD-2-Clause,
Copyright (c) 2015, Robin Stocker. The unmodified license is distributed in
`app/src/main/assets/licenses/commonmark-java-BSD-2-Clause.txt`.
Meshlit renders parsed nodes as native Compose text/blocks; HTML, scripts and
remote images are not executed or automatically fetched.

- HyperL development alpha.6, pinned commit
  c03a8590d959f5480c54efb4e3c3f87af6532e39: newly covered rights in `core-hyperl`
  are HyperL Community and Enterprise License 1.0 (source-available), with earlier
  Apache-2.0 grants preserved. Complete licence, NOTICE, prior Apache text and
  scope history ship offline in APK assets. See core-hyperl/MODIFICATIONS.md and
  docs/hyperl/ALPHA6_PROVENANCE.json. No desktop runtime or vendor SDK is bundled.

### Apache MINA SSHD 2.20.0

`core-ssh` uses sshd-core and sshd-common for the optional API 26+ embedded SSH node transport. Copyright The Apache Software Foundation; Apache License 2.0. Upstream: https://github.com/apache/mina-sshd. Licence and NOTICE copies are retained in app assets under `node-ssh`. This dependency does not certify Android or guest-runtime support.

### Offline host map

The Android host map bundles unmodified Leaflet 1.9.4 JavaScript/CSS, BSD-2-Clause, copyright Volodymyr Agafonkin and contributors. Its licence is in `app/src/main/assets/map/LEAFLET_LICENSE.txt`. Land geometry comes from Natural Earth 1:110m land version 4.0.0, public domain, converted from the published shapefile to GeoJSON. Source URLs and processing are recorded in `app/src/main/assets/map/SOURCE.txt`. The offline layer makes no network requests. When the owner enables the optional street layer, tiles load from OpenStreetMap with visible contributor attribution and Meshlit's identifying user agent.


### Optional DeepSeek Harness host

DeepSeek Harness, copyright (c) 2026 DeepSeek, MIT license. Reviewed source
`f3803c96974ad4a94195fb97e5c637e9c2a3afe3`; npm `@deepseek-ai/dsh`
`0.2.2-alpha.2`. The full upstream MIT text is retained in
`companions/deepseek-harness/UPSTREAM-LICENSE`. Source and artifact provenance
and the transitive integrity lock accompany that directory. Upstream dependencies
retain their individual licenses in the installed packages and upstream third-party
notices. Meshlit connects to this separately installed runtime; no Harness runtime
or upstream web bundle is redistributed in the APK or Studio app.

## Agency Agents role templates

MIT License, Copyright (c) 2025 AgentLand Contributors. Imported as role-description data from https://github.com/msitarzewski/agency-agents at b37b5bb33b78f2c1d6567d98e25ca818e3259219. Complete license and provenance are retained in shared-workspace/src/jvmMain/resources/agency/. Upstream installers are not executed or included.

### Connected service API references (2026-11-02)

OpenViking (volcengine/OpenViking, main server AGPLv3), Google Artemis
(google/artemis, Apache-2.0, copyright 2026 Google LLC) and Strata
(Niko1221/Strata, MIT with separate component/model licenses) are optional
service compatibility references. Meshlit's API adapters are independent code;
upstream server/engine implementations, models, assets, installers and global
agent rules are not bundled by these adapters. Reviewed revisions and protocol
sources are recorded in `docs/CONNECTED_CAPABILITIES_2026-11-02.md` and
`shared-workspace/src/jvmMain/resources/integrations/catalog.json`.
