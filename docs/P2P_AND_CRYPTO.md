# P2P chat, scoped remote commands and local cryptography

Implemented source is Experimental unless noted. Build/fixture checks are not
physical phone, Internet reachability, penetration testing or production proof.

## Phone to phone without a VPN

Settings → P2P chat and commands opens an app-owned, foreground WebRTC data
channel. Both phones need a current WebView with WebRTC and secure WebCrypto.
No new native WebRTC AAR, VPN, public SSH port or downloaded executable is needed.

1. On each phone, explicitly enable foreground P2P. Keep both screens open.
2. Generate a fresh random 256-bit pairing key on one phone. Give only the intended
   peer that key through an independent trusted channel. It remains session-only.
3. For the same LAN, no external ICE server is needed when host candidates work.
   For Internet/mobile networks, enter your approved STUN server on each phone.
   If desired, separately allow TURN and supply your approved server's credentials.
   No server or paid entitlement is bundled. STUN sees network metadata; TURN sees
   metadata and carries encrypted traffic. Direct-only can fail under CGNAT,
   restrictive firewalls or incompatible mobile networks.
4. One phone taps Create offer and privately shares the signed package. The other
   pastes it and taps Accept peer package, then returns its signed answer. The first
   phone accepts that answer. Descriptions expire after five minutes and include
   network metadata. HMAC-SHA256 verifies possession of the shared key; DTLS/SCTP
   carries encrypted data. This is not verification of a person's real-world identity.
5. Wait for **Connected · direct P2P** or **Connected · encrypted TURN relay**,
   derived from the selected candidate pair. Unknown/unapproved routes disconnect.
   Connection attempts and gathering have bounded deadlines. No automatic retry
   or discovery grants are made. A session expires after 30 minutes.
6. Send peer chat text or request actual peer status. Received text is untrusted
   evidence and never auto-runs an LLM, shell or tool.

Enable Accept scoped peer commands and the receiver's agent grant before allowing
commands. Remote requests always use agent permissions on the receiving phone,
including requests a human sends from another phone. Supported operations are
STATUS, VM_STATUS, VM_START, VM_STOP, CLUSTER_STATUS, CLUSTER_WORKER_START and
CLUSTER_STOP. VM and cluster mutation switches are separate; existing global
operation gates, saved VM authorization, cluster delegation and worker pairing
still apply. There is no arbitrary shell/root/credential/configuration command.

The P2P channel does **not** carry native layer/tensor inference traffic or enroll
model workers. It can ask a remote phone to manage its already-approved local
worker, but a phone-to-phone chat connection is not a qualified Internet model
cluster. Each actual command reply is correlated to its request; timeout means
unknown remote outcome. Requests are not automatically replayed. Transcript
history is bounded session memory, with sender/direction shown. Packets are bounded,
expire after two minutes and reject repeated IDs. Clocks must be reasonably aligned.
Closing/backgrounding the screen, revoking access or emergency Stop disconnects;
completed remote disk/process effects are not undone.

For local LLM-directed chat, choose P2P chat and command tools in Conversation
settings and separately grant P2P agent access. Agents can pause/resume their own
session inside human grants. They cannot generate new owner permissions, change
ICE servers, edit receiver scopes or enroll a peer.

## Multiple saved nodes and existing clusters

Settings → Device and cluster commands exposes up to eight saved, independently
fingerprint-pinned SSH nodes per sequential request. Enroll hosts, credentials and
per-node actions in SSH first; then enable remote commands and optionally agents.
Conversation → Multi-node command tools exposes only structured node actions,
not a generic shell. Global SSH/automation and saved SSH delegation still apply.
Agents can pause/resume dispatch inside existing human grants; not enable saved
access, enlarge target actions or resume emergency Stop. Local cancellation closes
owned transports but cannot certify termination of a remote process.

Private LAN/approved reachable private Internet SSH transports retain their own
requirements. The distinct P2P path above avoids VPN/port forwarding for phone
commands where WebRTC ICE succeeds. Neither path claims native multi-host model
speed, automatic sharding or production fleet scheduling from node counts.

## Built-in cryptography

Settings → Local cryptography supplies SHA-256/SHA-512, a SecureRandom-generated
256-bit hex key, HMAC-SHA256 calculation/constant-time verification, and
AES-256-GCM encryption/decryption with a fresh random 96-bit nonce, 128-bit tag
and bound authentication data. Platform JCA primitives are used. ML1 envelopes
carry version, nonce and authenticated ciphertext; they never include the key.
The same key and authentication data are required for decryption. Inputs are
bounded (64 KiB plaintext); invalid or altered ciphertext is rejected. Keys are
random bytes, **not passwords**; no password derivation or key recovery is supplied.

Manual cryptography is a local human utility available in Core Candidate too.
It stores no key/input history, performs no network request and makes no automatic
clipboard/export. This does not automatically encrypt other app data or establish
security certification. Losing a key loses access to ciphertext.

Agent tools require a separate saved cryptography grant and per-chat selection.
Arguments/results, including any key supplied, are visible to the local planning
model and may remain in saved chat. Do not put real credentials/private keys in
chat. Use the credential vault for service secrets. Agent cryptography remains
blocked in Core Candidate.

## Evidence and primary references

- Actual browser-to-browser signed WebRTC offer/answer, direct route, Unicode
  delivery, altered-SDP rejection, replay rejection and disconnected-send rejection
  were checked on this Mac. `companions/p2p/serve-test.py` serves the explicit
  loopback-only integration page; click Run data channel checks. These checks do
  not execute Android VM/cluster commands or test distant phone networks.
- JCA tests use known SHA-256/RFC 4231 HMAC vectors and real GCM round trips,
  fresh nonces, altered ciphertext, authentication-data mismatch and size limits.
- Android WebView/physical phones, Internet STUN/TURN interoperability, Windows,
  Xiaomi/Samsung end-to-end commands and security/production review remain gates.
- [WebRTC peer connections](https://webrtc.org/getting-started/peer-connections),
  [data channels](https://webrtc.org/getting-started/data-channels),
  [Android cryptography](https://developer.android.com/privacy-and-security/cryptography).
