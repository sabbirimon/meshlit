# Meshlit Studio on desktop and Android

Desktop chat uses workbench theme colors for the title and speaker labels. The title stays centered as the sidebar opens or closes. Android centers the model name in the chat header and uses the phone’s system typeface by default. Its status bar and system navigation follow the appearance selected in Meshlit, including when the phone uses a different theme.

On Android, open **Models → On device** to see the installed model card. Choose another installed model using **Choose model**. Each card shows its publisher, available capabilities, file format, and specifications. Model context is the training limit. Active context is the window allocated for the current session. **Import** opens the file picker or accepts a model link.

For a larger chat window on an ARM64 phone with the native runner installed, open **Storage and runtime → Context and KV cache → Native local**, choose the context size, and select **Save for next load**. Unload and reload the model to apply the change. The card shows the resulting active context. Android’s device profile offers up to 8,192 tokens on high-capacity phones, subject to model and available-memory limits.

The mobile model indicator is orange on standby, green during generation, blue while starting or loading, violet for a tool call, yellow when approval is needed, and red on failure. Gray means unloaded. A configured remote route is teal. The text beside the dot names its current state.

**Show reply activity** is in the Android chat menu and Conversation and token settings and the composer’s tools menu on desktop. Expand Activity on a reply to see recorded operations and any user-facing summary supplied by the provider. **Explain answer** prepares a follow-up that asks the model to explain its evidence and assumptions.

Use the composer to create a Markdown document, open research, or choose an image or video generator. Android uses the configured Media studio profile. Desktop lists connected media tools. Preview returned images in the conversation. Press Play for audio or video. Browse PDF pages, read Word document text, and inspect code before choosing a download location. Code blocks have their own **Download code** action. Download written answers with **Download reply**. In Android Media studio choose **Add file to chat**, then **Download file** beneath that reply to save the original image, audio or video. Desktop tools that return files show a download button in chat. Their outputs also stay available in **Download tool output** for the current session, up to eight files and 32 MiB total.

Type **/tools** in either composer to browse available destinations and connected tools. **/tools pair devices** searches by task. **/tool devices** opens the device workflow, and **/tool mcp:NAME {"text":"hello"}** stages a registered tool with editable arguments. The registry enforces its normal permissions. Desktop also includes commands from installed IDE extensions.

On Android, share a reply through **Response actions → Share response** to an app that accepts text. Choose **Meshlit** when sharing text back and review **Add to draft**. For an outside client on Android, open **+ → Allow model clients**, choose the model ID, issue a scoped client key for that model, and start the gateway. Keep the gateway screen open. Configure the client with the displayed loopback port and its own bearer key. Cross-device clients need an authenticated encrypted tunnel. Keep the owner token private. To use an outside model from Android chat, open **+ → External model providers** and select the configured provider in chat. Use **+ → MCP tools** for a tool server. On desktop, **Ask another model** opens a registered route and prepares a draft. **Allow local model clients** opens the local server controls.

### Ask another model from chat

Type **Connect to Gemini** or **Connect to ChatGPT** to choose the provider and model. Then try **Ask Gemini to summarize: …** or **Tell ChatGPT: review this function …**. A delegated reply appears in the same conversation. Paste the specific material after the instruction.

Choose **Always send named tasks** on desktop or **Auto-send named tasks here in this chat** on Android to skip the prompt on later tasks for the approved destination. Desktop access settings let you revoke automatic sending. On Android, use the connection dialog in the same chat. A configured provider API handles in-chat replies. On Android, the installed consumer app can also receive a task via sharing and show its answer there.

On Mac or Linux, select **Dictate a named task** in the desktop microphone menu. Start system dictation and finish the instruction with **send task now**. For example, say “Ask Gemini to suggest three names for my app, send task now.” Windows can use built-in voice input. Android can route a final voice transcription through the same named-task permission as a typed request.

For a desktop agent in a trusted workspace, choose **Always allow, including agents** for the selected provider. Its `meshlit_delegate_model_task` tool accepts a `request` such as `Ask Gemini to suggest three names for my app`. On Android, enable agent access on the provider profile and select it under **Agent Gateway → Named model tasks**. In the client key form, use **Fill for Gemini tasks** or **Fill for ChatGPT tasks** and issue the scoped key. Start the gateway. The client can call `named_model_task` with an `instruction` field. Its reply also appears in **Agent tasks** in chat history.

Desktop **+ → Generate image → FLUX.1 schnell** can generate an image using the public Hugging Face Space with Internet permission. Enter the prompt to send. The preview appears in chat, where **Download file** saves the original. Availability depends on the Space’s free capacity.

A live provider check received a text response from a separate local Ollama service running Gemma 4 E4B. A second check invoked the public FLUX generator through the desktop composer and verified identical bytes in its WebP preview and download. [Generated sailboat](evidence/2026-11-24-live-providers/flux-sailboat.webp)

After the first exchange, the conversation rail offers a point for each prompt and reply. Tap the rail on Android to choose a message, or select a mark on desktop to jump directly.

The mobile chat header shows the active context window when the runtime reports it. If only the model specification is available, the header labels it as the model maximum. New messages show their local time. Completed replies show elapsed time and token speed when the runtime provides a count. Desktop local chat shows its configured window and the same reply metrics.

In conversations with at least two prompts, the short marks beside the transcript show your position. Hover or focus a desktop mark to preview its prompt and click to jump. On a phone, tap the marks and choose a prompt. **Add to chat → Share screen** opens the system source picker. Desktop shows a live preview in the browser and attaches the frame you choose. Android captures one frame after five seconds. Review the attachment in chat before pressing Send.

On a phone, open the chat menu and choose **Models and imports**. **On device** opens file import, URL import and installed models. **Hugging Face** opens all formats. Tap **All formats** to limit discovery to GGUF, or **Fits this phone** to show repositories with a published GGUF weight estimate within the current memory budget. Use **Load more models** to continue. **LM Studio** opens the public catalog and the larger **Community GGUF** repository list. Choose a family, a variant, a repository and then the file size you want to download. Transfers appear under **On device**. Long-press the attachment, chat-tools or voice icon to see its label.

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
5. On Android, open **Models and imports → Hugging Face** for repository search, **LM Studio** for catalog families, or **On device → Import** for a file already on the phone. Choose the file through the system picker. After import, use Models to load it and check the status before chatting. Desktop folders on another device must first be made available to the phone.

On a Samsung phone, open **Settings → Agents** to inspect profiles, teams and the registry. Select **More tools** for logs and connections. Long-press a settings shortcut for its description. The device radar shows live discovery while scanning. Devices without position data appear in the inset. The phone model library puts local import and URL download ahead of recommendations.

Desktop replies show elapsed time and any token counts returned by the engine. Decode speed excludes startup when reported by the engine. Overall speed includes time spent preparing the request and loading the model. Older saved replies may have no measurements.

## Pair an Android phone with Studio over the internet

1. On the desktop, open **Devices → Enable nearby pairing → Configure internet relay**. Enter `https://meshlit-relay.imon.com.bd` and the relay operator’s access token. Keep **Devices** open and choose **Copy my device ID**.
2. On Android, open **Devices → Pair Studio desktop by ID**. Enter the relay token and the desktop’s 20-character device ID, then tap **Pair desktop**. Compare the phone ID on this screen with the ID on the desktop approval prompt.
3. After approval, the desktop appears in **Saved desktops**. Tap **Verify connection** to reconnect after reopening the phone app. The phone checks the desktop’s saved public key and node identity. **Forget** removes the saved desktop. **Forget relay token** separately removes the relay credential.

On November 24, 2026, a Samsung Galaxy A20s paired with a test Studio instance on a Mac through the public hostname using LTE and free WARP. After a force-stop and relaunch, **Verify connection** checked the same saved identity. The desktop recorded one pairing approval across both sessions. The screenshot hides the desktop name. [Samsung reconnect result](evidence/2026-11-24-live-providers/samsung-relay-reconnected.png)

## Connect a Hugging Face account

Public, ungated models work as a guest. On desktop, choose **Sign in** in the model browser. On Android, open **Models → Hugging Face → Sign in or create an account**. Choose **Sign in with Hugging Face** to open the official browser consent screen. If you need an account, choose **Create a Hugging Face account**, complete the provider's signup, return to Meshlit and select sign-in again. Keep the originating Meshlit window or screen open until authorization returns.

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

[Platform rules and permissions](RULEBOOK.md) · Historical Android and Studio audit (archived)

[R7 model cards, chat screenshots and native inference measurements](evidence/2026-11-24-aquos-r7/README.md)

## Inspect a Linux node with Shirudo

In desktop Studio, open **Tools → Network security**. On Android, open **Settings → Privacy & permissions → Shirudo shields**. Export a status report on the Linux target with `/run/shirudo/runtime/usr/bin/shirudo status --json` and open the JSON file in Meshlit. The viewer shows the host’s reported verdict, loaded shield count and individual security checks. A report is a snapshot. Independent proof requires separate TPM attestation.

On a configured Linux desktop, **Scan local Linux target** requests system authorization to inspect the local target. **Apply signed Shirudo shield** lets the operator choose a local `.upatch`, review its path and authorize the load through polkit. Shirudo verifies the signature. Scan again after applying a shield. On macOS, Windows and Android, use the report viewer. Studio agents can read the latest viewed summary for the current session using `meshlit_shirudo_status`.

See the [upstream target requirements](https://docs.shirudo.dev/) before setting up a Linux node.

Importing a Shirudo report scans its seven expected security checks. The summary counts alerts and warnings and names any missing checks. **Export original JSON** on Android or **Tools → Network security → Export Shirudo report** on desktop saves a reusable copy with the original fields.

## Customize the chat composer

Open **+** in chat. Pair devices, choose models, open agents, browse files or change internet search access there. **More tools → Customize composer** on desktop and **Customize composer** on Android let you pin destinations, connect external services and import shortcut JSON. Type `/tool research` to open a pinned shortcut named `research`. A linked HTTPS service opens in your browser. Registered tools run through the installed tool host after you review their inputs. Their result goes into a draft for review.

A portable shortcut file looks like this.

```json
{"schema":1,"tools":[{"command":"research","title":"Hugging Face","target":"https://huggingface.co"},{"command":"pair","title":"Pair devices","target":"devices"}]}
```

Desktop also accepts actions from its Studio dashboard and registered tools. Android supports its own destinations and MCP tools from enrolled, enabled routes. In **Customize composer**, discover approved tools after configuring their route. Use the picker on the target platform to select its tool names. An import accepts up to 32 shortcuts and 64 KiB.

Type or dictate “scan my Shirudo report” and send it, or use `/shirudo scan`, `/shirudo export` or `/shirudo status`. On a prepared Linux desktop, `/shirudo scan` requests a live scan. Android, macOS and Windows open the report picker. **Autopilot** on Android requests permission to use the selected built-in agent tools. Desktop’s **Autopilot** supports the approved web-read workflow. After enabling it, send “search the web for” followed by your query. The query goes to Wikipedia and the results go to the selected model. Operator actions, external tools and file selection retain their own prompts.

### Work with results in chat

Chat-launched Shirudo scans return their check results to the composer. From a report or supported diagnostic dashboard choose **Explain in chat**. You can also type `/shirudo explain` for the report in this session. A draft lets you review what goes to the selected model. Send it, then ask follow-up questions in the same conversation.

Use **Paste tool result** for output copied from another service. A registered external tool can return text or structured data to the draft. The desktop composer labels binary output by type and size. Use the attachment picker when you want the model to see an image. In the other direction, open a pinned tool with a draft present to review the draft as JSON input. The tool runs only after confirmation.

On desktop, `/app describe your app` asks the selected model for a single HTML app and opens complete source in an editable document. Save the file after review. For an existing codebase, choose **More tools → Coding workspace**. An installed IDE agent can also receive your draft through **Agents and model tools**.

Use `/ssh build host`, `/ssh test host`, or “SSH to host and build my app” with a saved host. Select the existing project folder and its npm, Cargo or Gradle task, then approve the displayed target and command. Meshlit checks the known SSH host key and returns the exit status and output to the draft. Android chat can request the structured actions delegated to a paired SSH node. Enable SSH node tools and choose a compatible agent route.

For a multi-file app, send `/project` followed by a description. Review the generated source and file list before saving into a new project directory. For a local checkout, `/build` and `/test` ask you to select the folder and task, then return the process output and exit status to chat. Build tools and project dependencies must already be installed. For interactive edits to an existing project, open it in the coding IDE and use an installed coding agent.

## Open an installed Android app from chat

In the full Android app, type **open Calculator**, then tap the matching app. You can also use its package name. To allow control, type **operate Calculator**, review the package and open the Android automation settings. Enable the Android service and select Phone tools in Agent mode for actions inside the app. Choosing **Ask in chat** sends the original text through the regular chat path.

Android **App permissions** links to Accessibility, Usage access and Security. Android asks you to make any grant in system settings. Desktop **Permissions → System permissions** opens platform-specific controls, including Screen Time on macOS and App diagnostics on Windows. Linux controls vary by desktop environment.

For a hands-free task, load a local tool-capable model and send **use Calculator to calculate 37 times 8**. On first use, select the app and review the phone-agent and chat Autopilot grants. Subsequent exact-name requests in the same authorized chat session can launch it directly. The agent may inspect, tap and type within that package. Stop cancels further work. Protected screens, password fields and Android permission approvals require a person. Model and accessibility support varies by app.

If the app is missing, choose **Search app store** or **Install a local APK…**. The latter opens App permissions to select a trusted APK and request installation through Android. After installing, return to the draft and send it again.
