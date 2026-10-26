# Optional Tailscale and VPN observations
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-10-12: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Implementation increment: 2026-10-26. Open **Dashboards → VPN & network**. Basic settings remain the existing six Android/four Studio entries; VPN setup belongs to its dashboard, with technical fields under **Advanced details**.

## Studio

Enable **Allow local Tailscale status reads**, review the selected installed CLI, then **Refresh status**. Standard macOS/Linux/Windows locations are checked for an existing executable; nothing is downloaded or installed. Advanced details lets you choose another installed CLI. Choosing a different executable turns the opt-in off until you enable it again. Use only a trusted Tailscale installation.

Studio invokes the selected executable directly with `status --json`, without a shell, elevation, login keys, `up`, `down`, route changes or scanning. The [official CLI reference](https://tailscale.com/docs/reference/tailscale-cli) documents this machine-readable status command. Connection, login, approval, DNS and exit-node changes stay in the official client. On macOS, an installed `/Applications/Tailscale.app` can be opened from the dashboard; other installations can be opened normally using the operating system. **Tailscale setup** opens the official download/setup page only after a click.

The dashboard displays the daemon's reported state, OS-tunnel/userspace mode when present, assigned addresses, self/peer control-plane presence, selected exit-node presence and health-issue count. Advanced details shows up to 50 peers, with a maximum accepted inventory of 1,024. Missing fields stay unknown. Userspace mode does not mean an OS VPN is active. Peer presence does not establish reachability of a model API, and an exit-node report does not prove the path of every application's traffic. These distinctions follow the [upstream status schema](https://github.com/tailscale/tailscale/blob/main/ipn/ipnstate/ipnstate.go).

Reads have a five-second deadline and a 2 MiB response limit. Cancel, leaving the screen or disabling observation cancels the owned read process; failures clear old results. Malformed/unsupported responses are rejected rather than reported as connected. CLI stderr and raw JSON are not shown or saved. Login URLs, node keys and account profiles are excluded from the parsed view. Authentication and detailed health remediation remain in Tailscale.

## Android mobile

Enable **Show this app's network status** to read Android's current default network for Meshlit. The dashboard shows VPN transport, Android internet validation, metering, observed time and whether the Tailscale app package is installed. Advanced details shows the interface and assigned addresses that Android exposes.

These are genuine Android API observations, **not Tailscale-specific connection proof**. Android restricts VPN owner identity to the owning app; see [NetworkCapabilities.getOwnerUid](https://developer.android.com/reference/android/net/NetworkCapabilities#getOwnerUid()). An installed Tailscale package, `tun` interface name or `100.x` address is not used to infer the provider. Split tunneling can exclude Meshlit even while a VPN is connected for other applications. Peer lists, Tailscale login, direct/relay details and exit-node state remain unavailable to this adapter and are viewed in the official client.

**Open Tailscale** hands off to the separately installed `com.tailscale.ipn` application. **Android VPN settings** opens the system VPN screen. If the companion is absent, the dashboard offers the official setup page. Installation and VPN consent remain Android/client-managed; no second Meshlit VPN service is created. Follow [Tailscale's Android setup](https://tailscale.com/docs/install/android) to configure the companion.

Readings refresh when the enabled dashboard resumes and when you tap Refresh. Leaving the foreground clears the displayed observation. There is no background polling, network scan, location request or companion control broadcast. Refresh after Wi-Fi/cellular, VPN or split-tunnel changes.

## Trust and observation limits

Both platforms label snapshots with their source and time and expire their headline after 60 seconds. Failure, disabling observation and client handoff clear the displayed result. Only opt-in preferences and Studio's CLI path persist; private addresses and peer observations remain in memory and are not exported or sent to models.

**Test an approved model host/service** opens the existing explicit, authenticated application-level checks. A successful host check is separate evidence from VPN status. VPN membership does not enroll a Meshlit worker, bypass TLS, grant agent permissions, enable remote root, or implement distributed model execution. No Tailscale control-plane API credentials or paid services are required by this adapter.

The integration is optional: no upstream SDK/source is imported, no telemetry is added, and no automatic connection changes occur. Live tailnet, exit-node, sleep/wake, split-tunnel and physical Android transitions still require device qualification. Unit/process fixtures and offscreen UI renders do not establish those outcomes. Budget accounting remains device-local estimates, not provider billing caps.
