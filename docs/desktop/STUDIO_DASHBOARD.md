# Studio home
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-24: [Plan](../../PLAN.md) · [Progress](../../PROGRESS.md) · [Document status](../DOCUMENTATION_STATUS.md).
Scope: desktop/server; individual acceptance gates apply. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

General Chat opens in an empty Studio window. Use the model and device controls above the conversation, or enter a request such as **connect Ollama**, **use LM Studio**, **use LM Bionic**, **choose an online provider**, **pair my laptop**, or **open coding IDE**. These requests open setup before a model is ready. Enter credentials in the connection dialog. Use **Dashboards** for management and search. The coding IDE opens in a separate window. In the Code OSS edition, a saved startup preference takes precedence.

The native client has a device dashboard for pairing links, QR codes and saved nodes. Code OSS can register a device that serves an OpenAI-compatible API. After you select a server and model and approve the endpoint, Code OSS opens a fresh conversation on that connection. Model inference uses the selected server's compute. For Ollama and LM Studio, start their API server first. For Bionic, use the address shown by its Local Model API. Use authenticated HTTPS for remote servers or an authenticated tunnel terminating on loopback. Clear a Settings search to restore all categories.
