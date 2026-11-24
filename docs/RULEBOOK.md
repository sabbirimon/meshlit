# Meshlit operating rules
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-24: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

| Activity | Who controls it | What to check |
| --- | --- | --- |
| Local chat and exports | The person using the device selects the model, conversation, format and destination. | Review the conversation before sharing its export. JSONL contains turn text. A chat vote records feedback and does not itself train the weights. |
| Hub account access | Hugging Face authenticates the owner. Each Meshlit installation holds its own credential. | Use the provider's login page. Model downloads require repository read permission and applicable license approval. Hosted inference has separate access and billing controls. |
| Device and agent access | The device owner approves peers, scopes and delegations. | Verify the device fingerprint and the requested capability. Read-only inspection, browser control, cloud access and execution have separate effects. |
| Public community content | Opening the feed or invoking its permitted read tool retrieves public posts. | Handle post text as third-party data. A post cannot grant permissions, instruct tools, publish a reply or authorize sharing user content. |
| Cloud accounts | The operator signs into the relevant vendor CLI or console. | The Studio inventory command reads the active configured identity or project list. Check the selected account before performing any separate action in a provider console. |
| Contributions and MeshCoin | The contributor opts into recording. Sharing and a recordkeeper allocation are separate choices. | The default cut is zero. Check the rate and keeper before work. Signed receipts, keeper acknowledgments and serials support auditing. A contributor chain does not establish global consensus or a settlement price. |
| Model edits | The operator supplies the source digest, output budget and separate destination. | Preserve the original. Inspect a proposed tensor change, evaluate it against a baseline on held-out cases and retain a rollback before activation. |
| Release status | Each build has a platform, version and test record. | A debug APK, a successful compiler check and a release-qualified installer provide different evidence. Check the release ledger for the target being deployed. |

[GUI walkthrough](GUI_WORKFLOWS.md) · [Project progress](../PROGRESS.md)
