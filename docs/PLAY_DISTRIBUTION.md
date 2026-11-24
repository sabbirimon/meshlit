# Google Play distribution preparation

Maintainer: **IMON**. Reviewed 2026-10-08. This is an engineering review candidate,
**not a Google Play approval or a claim of full policy compliance**.

## Two distribution paths

| Build | Purpose | Signing and package | Capabilities |
|---|---|---|---|
| `meshlitV2Debug` | GitHub experimental beta | Debug certificate; `com.meshlit.v2.debug` | Optional Android autonomy, SMS and external shell integrations retain independent grants. Human-confirmed APK installation/removal requests use Android UI. Legacy built-in VPN is disabled; external PCAPdroid capture requires its own consent |
| `meshlitV2PlayReview` | Installable store preparation candidate and AAB inspection | Debug certificate, non-debuggable; `com.meshlit.v2.playreview` | Manifest removes Accessibility service, VPN capture service, SMS, all-files/broad-media storage, battery-exemption request, APK install/removal request and Termux command permissions |
| Future production distribution | Play submission | Operator upload key, fixed production package, Play App Signing | Requires the blockers below to be closed and actual Play Console review |

Local models, user-granted file import/export, chat, model management, credential
vaults and opt-in audit metadata remain available in the review candidate. Runtime
settings cannot re-enable the removed Accessibility service; its automation flags
are forced off, the autonomy controls are omitted, package administration is
disabled, and live packet capture is unavailable. Local phone-tool selection is
disabled; web-page tools retain their separate configured companion and grants.
Bounded classic-PCAP import remains available for offline inspection.
Battery settings use app settings instead of requesting an exemption. The separate
application ID prevents review testing from replacing the GitHub beta's data.

Both builds show versioned Terms and Privacy acceptance before first use. SDK
initialization, startup model loading, bootstrap and audit startup wait for the
agreement. Acceptance never grants Android permissions or enables optional audit
collection. Policies are available offline in Settings → Terms and privacy. Source
and bundled policy copies must match; preference backups must not restore consent.

## Build the review artifacts

Use JDK 21 and the configured Android SDK (compile SDK 37; target API 36):

```sh
python3 scripts/prepare-bundled-model.py
./gradlew :app:assembleMeshlitV2PlayReview :app:bundleMeshlitV2PlayReview :app:lintMeshlitV2PlayReview
```

APK splits are in `app/build/outputs/apk/meshlitV2/playReview/`; the AAB is in
`app/build/outputs/bundle/meshlitV2PlayReview/`. The APK is for review installation.
The AAB is for inspecting packaging and preparing the submission pipeline. **Do
not upload these debug-signed artifacts to Play as production releases.**

Production `release` signing now uses the real operator file
`~/.gradle/meshlit-release.properties` or an explicit
`-Pmeshlit.signingProperties=/absolute/operator/path.properties`. No signing key is
created here. Missing keys leave release output unsigned; release no longer falls
back to a debug certificate. The existing release variant is not automatically a
Play-compatible variant: its manifest still contains the broader sideload features.
Create a dedicated signed production variant from the reviewed restrictions after
closing the remaining gates, rather than submitting the broad release variant.

## Blockers before store submission

1. **AI safety and reporting:** implement a functional in-app offensive-content
   reporting channel to the developer, with user review of the submitted content,
   and verify restricted-content prevention across local, custom and hosted models.
   The existing copy/share actions and public issue link do not satisfy this.
2. **Privacy and SDK behavior:** resolve/review RunAnywhere's development telemetry
   attempts, document actual third-party data categories/destinations/retention,
   and complete accurate Data safety answers. Optional OTLP and provider requests
   must be reflected separately. No claim of a vendor telemetry opt-out is made.
3. **Public support:** IMON must supply a dedicated private developer/privacy
   contact and stable public policy page. The repository's public policy is a
   readable draft; public GitHub issues must never receive personal data/secrets.
4. **Production identity and signing:** choose a permanent package ID, monotonically
   increasing version code, operator-owned upload key and Play App Signing setup.
   The review package/certificate is not the production identity.
5. **Native/runtime compatibility:** verify every shipped native ABI's ELF LOAD
   alignment and bundle packaging; test on an actual 16 KiB page-size device/emulator
   without compatibility fallback. ARM64 beta libraries statically inspected here
   have 16 KiB LOAD alignment; that is not runtime proof or an AAB admission result.
6. **Permission/foreground-service declarations:** justify every retained location,
   nearby-device, camera, microphone and foreground-service use, show disclosures
   before optional access, and complete required Play Console forms/demonstrations.
   `specialUse` declarations alone do not grant acceptance. Review remote execution,
   downloaded runtimes and tooling against device/network abuse policies.
7. **Release testing and listing:** exercise denial/revocation/Stop, startup agreement,
   account/data deletion instructions, low-memory/background/thermal behavior,
   content rating, store screenshots, export controls and SDK/model licenses.
   Run Play pre-launch reports and any required closed testing using the intended
   signed AAB. Physical phone sharding remains an experimental claim with no proof.

## Sources

- [New apps use Android App Bundles](https://developer.android.com/guide/app-bundle).
- [Current target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878): mobile new apps/updates require API 36 from 2026-08-31.
- [Accessibility and sensitive permissions](https://support.google.com/googleplay/android-developer/answer/16558241): autonomous Accessibility actions are not an allowed use case; consent does not override this restriction.
- [Developer policy including AI-generated content](https://support.google.com/googleplay/android-developer/answer/18258653): prevent restricted content and provide in-app reporting.
- [16 KiB page-size requirements and verification](https://developer.android.com/guide/practices/page-sizes).

Recheck the policies and Play Console requirements at submission time. This guide
records concrete build restrictions and known gaps; it cannot certify a changing
policy review outcome.

## Owner distribution decision

The product has two distribution channels: **GitHub Full** and **Google Play**.
Architecture/ABI splits and the retained legacy UI test flavor are not additional
product editions. The Full beta retains implemented advanced capabilities and
explicit user/agent opt-ins. The current Play artifact is labeled **Play Review**
so it cannot be mistaken for the future signed, policy-reviewed store edition.
Both include the versioned first-use agreements and actual-state startup loader.
