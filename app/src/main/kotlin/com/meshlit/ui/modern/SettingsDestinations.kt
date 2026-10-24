package com.meshlit.ui.modern

/** One catalog drives navigation and search. No row is allowed a no-op action. */
data class SettingsDestination(val id:String,val title:String,val description:String,val advanced:Boolean=false,
    val keywords:String="")
object SettingsDestinations {
    private val advancedIds=setOf("crypto","gibberlink","remotecommands","p2p","colibri","behavior",
        "configuration","recovery","training","router","operations","gateway","commands","packages",
        "securitylab","power","external","acceleration","ssh","firewall","ide","hyperl",
        "audit","identity-recovery","openclaw","sharding","guardrails","mcp-catalog","agent-registry","agent-logs","rag")
    val all=listOf(
        SettingsDestination("menu-layout","Customize menu","Add, remove and reorder shortcuts in Settings and the main drawer",keywords="layout rearrange quick access shortcuts navigation"),
        SettingsDestination("sharding","Sharding and workers","Pair approved model workers, assess resources and negotiate layers",keywords="cluster load balancer distributed model split QR group"),
        SettingsDestination("radar","Device radar","Topology, live discovery, saved workers and local identity",keywords="map nearby far distance devices agents search logs identity user ID"),
        SettingsDestination("host-map","Global host map","World projection of consenting relay members and saved hosts",keywords="geolocation location country online recently offline device type find hosts"),
        SettingsDestination("external-services","LAN and VPN services","Check and save non-Meshlit devices by local, VPN or HTTPS URL",keywords="web API device URL local network remote non Meshlit VPN printer NAS server"),
        SettingsDestination("web-bridge","Meshlit Web/API bridge","Invite and approve a browser or non-Meshlit API client",keywords="web server API device invitation enroll browser external non Meshlit"),
        SettingsDestination("device-dashboard","Device management","Review and revoke saved endpoints, groups, workers, relay members and peer chat",keywords="approved group local remote single multi cluster pause stop revoke"),
        SettingsDestination("meshchat","Mesh chat","Foreground peer conversations over verified direct or relay connections",keywords="group device agent person message WhatsApp Telegram remote local chat"),
        SettingsDestination("securityprivacy","Security and privacy","Permissions, identity, guardrails, network access, audit and policy",keywords="privacy safety fraud trust auto on off customize settings"),
        SettingsDestination("tools-automation","Tools and automation","MCP registry, Gateway routes, agent jobs and reviewed hooks",keywords="MCP A2A agents tool registry hooks scripts"),
        SettingsDestination("mcp-catalog","MCP connections","Browse common servers and enroll an approved HTTPS route",keywords="MCP filesystem fetch memory GitHub Playwright custom server tools"),
        SettingsDestination("rag","RAG settings","Select local or remote retrieval mode; source connectors remain experimental",keywords="documents files search retrieval knowledge base vector embeddings local remote"),
        SettingsDestination("huggingface","Hugging Face dashboard","Verify account access, discover GGUF files and configure hosted inference",keywords="HF Hub token login models free paid gated download inference credits"),
        SettingsDestination("agent-registry","Agent registry","Saved agents, model assignments and registered tools",keywords="agents profiles MCP inventory"),
        SettingsDestination("agent-logs","Agent logs","Agent and MCP diagnostics with export",keywords="agent MCP jobs audit history errors"),
        SettingsDestination("guardrails","Local behavior and guardrails","Control local instructions and optional gateway term rules",keywords="censored uncensored filter safety policy"),
        SettingsDestination("search","Search access and articles","Offline content search, Internet permission and agent search grants",keywords="global chat articles Brave API key web internet search"),
        SettingsDestination("appearance","Appearance","Dynamic colors, accents, light and dark mode",keywords="theme display wallpaper font motion palette glass typography readability contrast"),
        SettingsDestination("crypto","Local cryptography","Offline hashes, random keys, HMAC and authenticated encryption",keywords="AES GCM SHA256 SHA512 encrypt decrypt cryptography agent hash keys HMAC"),
        SettingsDestination("gibberlink","GibberLink and English transcript","Optional foreground agent audio packets with readable text",keywords="ggwave sound modem audio transcript English agent"),
        SettingsDestination("remotecommands","Device and cluster commands","Scoped multi-node chat commands with human and agent switches",keywords="internet LAN remote SSH cluster multi device commands"),
        SettingsDestination("p2p","P2P chat and commands","Direct phone-to-phone WebRTC with signed pairing and optional TURN",keywords="peer Internet no VPN STUN TURN WebRTC ICE phone long distance chat"),
        SettingsDestination("colibri","Colibri host","Optional disk-streaming engine host: Off, On or Auto",keywords="MoE NVMe tokens inference external host disk streaming Colibri"),
        SettingsDestination("models","Models and downloads","Import files, manage downloads and load local models",keywords="hugging face token storage GGUF RAM"),
        SettingsDestination("media","Camera, vision and audio","Real image input, microphone and existing SDK media paths",keywords="CCTV webcam phone camera microphone WAV speech VLM STT TTS"),
        SettingsDestination("configuration","Configuration profiles","Export, review and apply portable device settings",keywords="import backup default custom declarative ansible terraform pulumi IaC"),
        SettingsDestination("personalization","Memory and personality","Local recall, profile and bounded self recovery",keywords="learning memories repair adaptation"),
        SettingsDestination("behavior","Custom local model behavior","Custom weights and your own local instructions",keywords="uncensored jailbreak system prompt offline"),
        SettingsDestination("recovery","Checkpoints and recovery","Encrypted native CPU KV snapshots and honest cluster recovery status",keywords="cache restart restore memory bank failover session tasks"),
        SettingsDestination("training","Fine-tuning","Real Soup LoRA/QLoRA jobs on a paired training host",keywords="train adapter dataset quantization stream layers compact GPU"),
        SettingsDestination("router","Model router","Scenario rules, model chains and answer comparisons",keywords="multi model tasks coding reasoning routing pipeline ensemble"),
        SettingsDestination("cloud","Cloud management dashboard","Vendor resources, costs, credentials and separate human/agent controls",keywords="AWS Azure DigitalOcean OpenRouter GCP custom API login SSH vault tokens auth functions cloud dashboard"),
        SettingsDestination("operations","Operations dashboard","Emergency stop, per-function switches and cluster capacity",keywords="kill switch stop all cluster limit human agent automation"),
        SettingsDestination("gateway","Agent Gateway","Built-in MCP, A2A and model endpoints; content policy",keywords="agentgateway proxy llm guardrail censored uncensored federation"),
        SettingsDestination("commands","Commands and instructions","Manual runtime/SSH commands and model tasks",keywords="terminal input execute stop instruction"),
        SettingsDestination("packages","Lab packages","Install, list and uninstall packages inside a ready VM",keywords="Kali Parrot Linux apt pip apk dnf pacman local web storage"),
        SettingsDestination("securitylab","Security Lab","Scoped assessments and cyber tool companions",keywords="red team blue team Androguard Quark Frida Objection Drozer Wireshark Metasploit Havoc Magisk forensics"),
        SettingsDestination("providers","Online providers","OpenAI, Claude, DeepSeek, Qwen, Gemini and compatible APIs",keywords="online cloud key token price cost Hugging Face"),
        SettingsDestination("power","Power and costs","Battery readings, download policies and electricity estimates",keywords="meter current energy charging expense thermal"),
        SettingsDestination("external","External devices and OTG","USB discovery, permissions and removable storage",keywords="gpu USB hardware peripherals drives NAS keyboard camera"),
        SettingsDestination("acceleration","Acceleration","Actual backend availability, CPU limits and platform roadmap",keywords="CUDA Vulkan OpenCL OpenGL Metal ROCm QNN CANN Intel NVIDIA AMD Huawei"),
        SettingsDestination("agents","Agent management","Delegated permissions, real jobs and registered tools",keywords="autonomous scopes tasks MCP"),
        SettingsDestination("ssh","SSH nodes and connections","Key-authenticated inbound node commands and pinned outbound connections",keywords="server LAN internet VM sandbox agent SSH NAS terminal external inter device"),
        SettingsDestination("firewall","Network rules","Persisted Meshlit listener firewall and network status",keywords="ports security IP allow deny"),
        SettingsDestination("device","Device","Device profile, name and hardware capabilities",keywords="role chipset gpu peripherals"),
        SettingsDestination("resources","Resources and sensors","Live RAM, storage, battery, model activity and available sensors",keywords="usage CPU memory camera microphone hardware diagnostics"),
        SettingsDestination("tasks","Task manager","Plan tasks, track real jobs, bulk finish and stop operations",keywords="todo priorities tags due subtasks queue retry cancel agent"),
        SettingsDestination("ide","Code workspace","Offline source editor, files, syntax highlighting and search",keywords="IDE VS Code programming Kotlin Python JavaScript JSON develop"),
        SettingsDestination("hyperl","HyperL libraries","Native CPU recipes, precise sums, encrypted datasets and kernel source",keywords="hyperl compute library vector affine dot relu CPU native precision encrypted dataset key rotation Metal Vulkan programming"),
        SettingsDestination("permissions","App permissions","Optional setup, runtime grants and Android accessibility",keywords="first launch camera microphone location nearby Bluetooth security"),
        SettingsDestination("files","Files and storage","Browse, preview AI assets and stream ZIP creation/extraction",keywords="SAF copy move share export folders zip unzip archive GGUF safetensors ONNX JSONL datasets tokenizer"),
        SettingsDestination("termux","Termux integration","Probe an installed shell, manage delegation and view its audit",true,"terminal commands Linux tools networking"),
        SettingsDestination("notifications","Notifications","System notification permission and channels",keywords="alert sound silent"),
        SettingsDestination("monitor","Monitoring and layer pipeline","Memory, thermal status and approved model workers",keywords="shard cluster battery CPU"),
        SettingsDestination("audit","Audit and telemetry","Encrypted audit history, device metrics, OTLP collector and exports",keywords="OpenTelemetry Grafana Tempo Prometheus Loki observability tracing monitoring CSV JSONL retention actor event"),
        SettingsDestination("logs","Logs and export","Search logs; filter severity and source; export TXT or JSONL",keywords="debug diagnostics error trace download"),
        SettingsDestination("identity-recovery","Device identity and recovery","Encrypted signing identity backup and restore",keywords="key fingerprint passphrase phrase pairing"),
        SettingsDestination("rewards","Mesh rewards wallet","Signed local compute receipts and verified backup",keywords="crypto contributions credits ledger MCC"),
        SettingsDestination("network","Network and pairing","Enroll devices, choose transports and approve layer workers",keywords="QR SSH Bluetooth web NAS hive pairing discovery"),
        SettingsDestination("peers","Forwarding peers","Manage peers for the existing inference router",true,"network IP routing"),
        SettingsDestination("runtime","Linux runtime and sandbox","VM configuration and agent activation consent",true,"root rootless terminal VNC desktop QEMU"),
        SettingsDestination("crawler","Web crawler","Configure the optional Crawl4AI companion",true,"search web endpoint token browser"),
        SettingsDestination("openclaw","Phone agent and autonomy","Built-in on-device agent, Android app scope and optional gateway",keywords="agent autonomous OS accessibility local provider pairing"),
        SettingsDestination("automation","Android automation","Accessibility automation and app allowlists",true,"permissions security agents"),
        SettingsDestination("hooks","Agent hooks","Enable and edit user-authored lifecycle scripts",true,"scripts tools"),
        SettingsDestination("help","Guide and tutorial","Offline walkthrough, configuration recipes and illustrated feature guide",keywords="help setup learn tutorial docs manual guide troubleshooting"),
        SettingsDestination("legal","Terms and privacy","Offline policies, accepted version and data controls",keywords="agreement consent privacy terms data delete IMON"),
        SettingsDestination("about","About and availability","Build information and feature implementation status",keywords="licenses version help")
    ).filter { com.meshlit.BuildProfile.routeAllowed(it.id) }.filterNot { com.meshlit.BuildConfig.PLAY_REVIEW && it.id in setOf("automation", "termux") }
    private fun isAdvanced(entry:SettingsDestination)=entry.advanced || entry.id in advancedIds
    fun search(query:String,advanced:Boolean):List<SettingsDestination> {
        val words=query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        return all.filter { entry ->
            (words.isNotEmpty() || isAdvanced(entry)==advanced) && words.all { word ->
                "${entry.title} ${entry.description} ${entry.keywords}".contains(word,true)
            }
        }
    }
}
