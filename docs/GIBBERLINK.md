# Experimental GibberLink with English transcript

Settings → GibberLink and English transcript is off by default. It uses the actual
local ggwave PCM codec, not speech recognition, an LLM, a prerecorded modem sound
or a fake successful response. The existing chat model/voice adapters remain
separate. No ElevenLabs service/key or GibberLink demo source is required.

Explicitly enable audio, optionally grant agents, and keep this screen visible.
Tap Grant microphone permission for human Android approval, then Listen on one
phone and Send sound on the other. Each operation is bounded to 30 seconds;
listening defaults to 20 seconds (agent limit 25). Only one local audio operation
can be active. Stop, background/close, policy revocation and global Media Stop
cancel capture/playback and release native/audio resources.

Send 1–96 printable English characters. Translate to English before sending;
this is not an automatic translation feature or proof of English semantics.
Each sent message has its exact English transcript and playback state. Actual
decoded incoming packets appear as English-character transcripts with time and
an unverified-source label. No packet is fabricated on silence/corruption/timeout.
A playback result proves local speaker playback, not delivery to another device.
The bounded session transcript holds at most 100 entries and can be cleared.

**There is no encryption, sender identity, replay authentication or delivery
acknowledgment in this audio mode.** Nearby people can record/decode or inject
packets. Never send secrets or automatically execute received content. Use the
separately authenticated P2P/SSH paths for remote commands. GibberLink is an
optional audio transport, not a secret language or a smarter LLM.

For agents, separately enable GibberLink audio tools in Conversation settings and
saved audio/agent + global Media/automation grants. `gibberlink_send` plays a bounded
English packet; `gibberlink_listen` returns a real decoded packet or a no-packet
result. The screen must be foreground; agents cannot grant microphone permission,
change saved access or run commands from received text. A reliable local tool-use
model is needed; the tiny starter model is not qualified for autonomous planning.

Source pinned to ggwave `060aec73dd7123ccac200442f75bdc7369795ffe`, with minimal
unchanged upstream C++/headers and MIT + Reed-Solomon notices. The bounded JNI
wrapper serializes upstream global state, checks live handles and uses the
memory-safe `ggwave_ndecode`. Source SHA-256 provenance is included. Android
builds four ABIs with 16 KiB page alignment. Offline APK notices are shipped.

Evidence: a real native PCM encode/decode round trip, silence rejection and
invalid-handle/buffer/payload checks passed on this Intel Mac. Physical speakers,
microphones, noise, sample-rate compatibility, Android WebView and Samsung/Xiaomi
agent-to-agent audio are not qualified yet. Core Candidate blocks audio controls
and agent tools; shared inactive source/native code is not an OS sandbox.

Primary sources: [ggwave](https://github.com/ggerganov/ggwave),
[GibberLink reference](https://github.com/PennyroyalTea/gibberlink).
