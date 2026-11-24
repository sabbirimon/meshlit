# Studio device relay
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-24: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../../docs/DOCUMENTATION_STATUS.md).
Scope: optional host/schema/reference; not a bundled qualified service. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Run on a host with Node.js 22 or later and a WebSocket-capable HTTPS reverse proxy. Install dependencies with `npm ci --ignore-scripts` in this directory and set `MESHLIT_RELAY_TOKEN` to a random secret of at least 32 characters. Start with `npm start`. Proxy the exact path `/v1/devices` to the loopback port specified by `PORT` (default 8765). Keep the access token outside source control. TLS between the verified Studio endpoints carries the model traffic inside the WebSocket tunnel.

On each computer, open **Devices → Enable nearby pairing → Configure internet relay** and enter the HTTPS origin and token. Open Devices on the receiving computer to accept a new pairing request for two minutes. Choose **Copy my device ID** there and enter it on the other computer. Local discovery works without the relay. To share a model, select a paired computer and choose **Share models**. On its peer choose **Use a shared model**. Clearing the relay URL disconnects the relay. Turning off device networking closes the host and active connections.

The relay handles online IDs, short-lived connection tickets, and encrypted streams. It is intended for a private deployment with a shared operator-issued access token. Its bounded in-memory registry resets on restart. Serve each deployment from one process until multi-instance routing is available.
