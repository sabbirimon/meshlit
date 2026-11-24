# Meshlit Studio on desktop and Android
<!-- meshlit-document-tracking:start -->
Tracking reconciled 2026-11-24: [Plan](../PLAN.md) · [Progress](../PROGRESS.md) · [Document status](DOCUMENTATION_STATUS.md).
Scope: Android/shared reference; desktop ports have separate acceptance. Phase labels in older sections retain their original scope.
<!-- meshlit-document-tracking:end -->

Desktop chat uses workbench theme colors for the title and speaker labels. The title stays centered as the sidebar opens or closes. Android uses its existing theme and centers the model name above its status.

## Start a conversation

| Task | Desktop Studio | Android |
| --- | --- | --- |
| Start locally | Open General chat. On first launch, choose the bundled model. The model button at the bottom of the composer opens the picker. | Open Chat and use the model selector. Open Models to download or import a model if the selector is empty. |
| Read a conversation | Your messages align on the right. Meshlit labels its replies with the selected model. Select Find in the header, type a phrase, then use the arrows to visit matching turns. | Your messages use a tinted bubble labeled You. The reply header shows its source. Use chat search to locate earlier messages. |
| Add context | Open the plus menu to attach a file or search the internet. Review the attachment before sending. Open Permissions if an action is disabled. | Open chat tools and review the access granted to the current conversation. Android file selection uses the system document picker. |
| Save or reuse work | Open the plus menu and select Export this conversation. Choose Markdown for reading, JSONL for an agent or fenced code for complete code blocks in replies. Choose a destination. | Select Export current chat from the chat actions, choose a format, then save through the Android document picker. |
| Adjust navigation | Use the sidebar button to switch between labels and a compact icon rail. Hover or keyboard-focus a disabled action to see its prerequisite. Back returns to the previous Studio page. | Open the main menu and select Settings. General contains everyday controls. Advanced tools groups the other pages. Search by name or keyword. Customize menu manages shortcuts. |

## Download or import a model

1. On desktop, open **Models & providers** and the model picker. Browse the LM Studio catalog or search Hugging Face by repository or author. Select a result to see its description, capabilities and available files. Check the selected file size before downloading.
2. Choose the GGUF file you want. Keep Studio open while the download finishes. A registered download should appear in the local model list. Select it, send a short message and check the model named on the reply.
3. For an existing desktop installation, choose **Import from LM Studio or Bionic**. If the list is empty, choose the folder selected in that app's model settings. Meshlit can use a complete single-file GGUF in place. Keep the original file at that location.
4. For other desktop formats, choose **Import model file** or **Import model folder**. Packages may require a local tokenizer, an index and every weight shard. Review the reported adapter before selecting a runtime. An inventory entry alone does not establish that the model can generate on your hardware.
5. On Android, open **Settings → Hugging Face dashboard** for Hub search and **Models → Import** for a file already on the phone. Choose the file through the system picker. After import, use Models to load it and check the status before chatting. Desktop folders on another device must first be made available to the phone.

## Connect a Hugging Face account

Public, ungated models work as a guest. On desktop, choose **Sign in** in the model browser. On Android, open **Settings → Hugging Face dashboard**. Choose **Sign in with Hugging Face** to open the official browser consent screen. If you need an account, choose **Create a Hugging Face account**, complete the provider's signup, return to Meshlit and select sign-in again. Keep the originating Meshlit window or screen open until authorization returns.

Read the requested permissions on huggingface.co. Approving connects eligible private models and already-approved gated models. A 403 can indicate that you still need to accept terms or request access on the model page. If browser sign-in expires, start again. A read token remains available in the account controls. Use the sign-out or remove-token control on each device to remove its saved credential. The provider's applications page manages the upstream grant. [HF account documentation](https://huggingface.co/docs/hub/en/oauth)

Desktop **Cloud providers → Hugging Face Hub** and Android **Hugging Face dashboard → Explore the Hub** search models, datasets and Spaces. Selecting a repository opens its official page. Dataset and Space entries are browsing results. Use the model picker for weight downloads.

## Connect tools and services

| Destination | What you can do |
| --- | --- |
| Desktop Cloud providers | Choose AWS, Google Cloud or Azure. View current account or projects using an installed, signed-in CLI, or open the vendor console. Account lookup displays only selected fields. An executable or sign-in error explains the next step. |
| Desktop Tools → Moltbook | Read recent public posts inside Studio. Enable Internet search under Permissions first. The registered read tool returns structured posts to an agent under the same permission. |
| Android Settings → Moltbook | Tap Read from moltbook.com to fetch recent public posts. Agents can request MOLTBOOK_READ through the typed command interface with cloud delegation. Remote callers also need cloud access for their enrolled device. |
| Devices | Review the invitation and fingerprint on the other device before saving a pairing. Check the peer's identity and selected model before sending a chat. A private relay test and a working public relay are separate checks. |
| Desktop MeshCoin wallet | Enable contribution recording when you want receipts. Review the named recordkeeper and optional cut before sharing. Confirmations and individual serials can be inspected after paired devices sync. |

## When something fails

An absent local model usually means its source moved, an index points to a missing shard, or no compatible runtime is configured. Reopen the picker and inspect the file or package. If an account request fails, check the credential and repository entitlement before retrying a large download. If a cloud CLI cannot return inventory, open the provider's console to verify the account. For all three cases, preserve the original weights, chats and receipts while troubleshooting.

[Platform rules and permissions](RULEBOOK.md) · [Historical Android and Studio audit](OLD_ANDROID_STUDIO_REQUEST_AUDIT.md)
