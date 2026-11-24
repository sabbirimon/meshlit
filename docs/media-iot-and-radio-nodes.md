# General AI nodes: media, IoT and radio

Updated 2026-10-06. Main product goal: a phone-first decentralized AI mesh with
real model layer execution and useful non-LLM contributions from heterogeneous
approved devices. A node is an authenticated capability provider, not necessarily
a machine that runs a transformer. Every capability needs observed evidence.

## Roles and current boundary

| Contribution | Examples | Current boundary / implementation gate |
| --- | --- | --- |
| Model compute | Android phones, capable Raspberry Pi, Linux/desktop GPU nodes | SDK/native CPU paths and experimental RPC exist; named physical phone and oversized-model tests pending |
| Storage / memory holder | Phone, NAS, Pi/server | Local journals/files now; authenticated content-addressed replicas and lease recovery planned |
| Sensor / preprocessing | ESP32, Arduino, environmental or location sensors | MICROCONTROLLER/SENSOR enrollment categories and role claims accepted; firmware, telemetry ingest and drivers planned |
| Media capture | Phone camera/mic, webcam, CCTV gateway, audio interface | Real image picker/phone thumbnail and microphone/file paths; external live-stream adapters planned |
| Vision / speech / DSP | Capable phone/Pi/server | Existing SDK media wrappers; compatible models/native backends required, unavailable states explicit |
| Tools / actuation | Approved GPIO/relay controller, serial tools, owner-approved shell host | SSH exec now; typed GPIO/serial/actuator drivers with owner scopes planned |
| Network / relay | Router, switch, firewall, cellular/radio gateway | Existing IP/TLS/control transport; alternative radio carrier adapters planned |
| Monitoring / power | Sensor MCU, smart power meter, capable phone | Android readings now; external calibrated sensor adapters planned |

Requested roles now include control, compute, storage, tools, routing, monitoring,
sensors, actuation, media and preprocessing. These are requested metadata, not
authority or capability proof. Approval remains explicit; an OBSERVE-approved
microcontroller cannot execute model operations merely by declaring compute.
ESP32/Arduino firmware and standalone Linux/Pi companions are not bundled Android
apps. A low-memory MCU may provide telemetry, simple DSP or control without being
admitted to native transformer layers.

Use small versioned messages over an authenticated gateway appropriate to the
hardware. Owner pairing binds device ID, public key or provisioned credential,
transport, allowed operations and firmware/driver identity. BLE/USB bonding or
finding an IP address is insufficient authorization. Gateways translate protocols
but must preserve the source identity and permit revocation per physical device.

## Media sources

Existing source: `VisionScreen` accepts user-selected images, bounds input to
8 MiB, performs bounded JPEG resizing on IO, and can obtain a real phone-camera
thumbnail through Android's camera activity. Capture/import are distinct from
analysis. The pinned RunAnywhere vision backend may be unavailable and must say
so; a captured image is not a successful VLM result. Existing `VoiceScreen` and
`RunAnywhereVoiceEngine` expose microphone capture, WAV import, STT/VAD/TTS calls
where the native backend and appropriate models are available. Physical speech
and vision correctness are unverified in this checkout.

The main Settings media route exposes these existing paths without asserting that
all models or cameras work. No CCTV/USB stream is silently started. Future source
adapters:

- Android CameraX: owned camera IDs, lifecycle capture, supported resolution/rate,
  contextual CAMERA permission, visible active source and explicit stop.
- USB UVC / webcam: actual USB permission, descriptor/format negotiation and an
  installed compatible driver; detach and resource cleanup are required.
- Authorized CCTV/NVR: authenticated RTSP or vendor/ONVIF gateway, explicit endpoint
  and camera scope, credentials stored separately, TLS where supported. Discovery
  does not guess passwords or claim ownership. Implement bounded stream decoding.
- Browser camera/microphone: the browser owns getUserMedia permission; an approved
  companion uploads bounded authenticated frames/chunks, not remote OS access.
- Audio interfaces/files/remote feeds: sample rate/channel/codec metadata, supported
  decoder and bounded buffering; no fabricated waveform or transcript.

A `MediaSource` needs stable ID, owner/permissions, transport, credential reference,
actual codec/rate/dimensions, timestamps, privacy/retention policy and current
state. A media job records source, model hash/runtime, segment/frame sequence,
processing result, uncertainty and cancellation. Low-end devices can capture and
forward selected compressed frames while stronger nodes perform vision/speech.
Enforce per-source frame rate, drop policy/backpressure, stale-data labeling,
reconnect limits, RAM/thermal/power budgets and cancellation. Keep cloud forwarding
an independent explicit opt-in; no hidden upload of camera/microphone content.

Acceptance: real phone camera and microphone, image/file input, one UVC webcam,
one authorized RTSP/NVR feed, unplug/reconnect, denied permissions, stale frames,
bounded memory on a low-end phone and no continued capture after revoke/stop.

## Signal processing and radio carriers

User direction: paired radio devices can communicate both ways where their actual
hardware, firmware, antenna and authorized profile permit. Include HF/VHF and
K/Ku-band hardware, AM/FM modes, cellular GSM/4G and other supported carriers.
Frequency bands, modulation and protocol families are separate fields; one radio
does not automatically support all of them. A phone's cellular modem supplies an
IP link through the OS/carrier; this app has no generic arbitrary-baseband control.
Radar is normally a sensing source, not a network modem. Radar data may feed DSP
jobs, but a communications link needs an actual supported radio/modem protocol.

### Hardware pairing and two-way support matrix

These are integration requirements, not currently installed radio drivers.

| Requested link | Hardware path | How Meshlit uses it |
| --- | --- | --- |
| HF / VHF / UHF | Compatible transceiver plus a packet modem/TNC, or an installed SDR/modem gateway | Framed task, telemetry and acknowledgment messages after actual bidirectional testing |
| AM / FM | Supported modulation on the paired receiver/transceiver; digital data also needs a compatible modem | Audio/signal analysis or modem data; an AM/FM receiver alone cannot send mesh messages |
| K / Ku / other microwave bands | Supported modem/transceiver, suitable RF frontend/antenna, or an existing IP bridge | IP mesh transport where available; otherwise a reviewed modem adapter |
| GSM / 4G / 5G | Phone OS cellular data or an external supported cellular modem/router, with its network access | Authenticated IP transport; no generic arbitrary baseband programming |
| Other packet radios | Compatible firmware/serial/BLE/USB/network gateway with known framing | Task/telemetry carrier admitted according to measured limits |
| Radar sensor | Supported sensor capture/driver | Timestamped sensing/DSP input; communications require a separate actual modem |

Pairing flow: discover actual hardware → identify driver and RX/TX capabilities →
review identity and link profile → obtain OS access and owner authorization →
exchange an authenticated challenge/response → measure both directions → save
verified capabilities. A USB descriptor or Bluetooth bond alone is not radio
capability evidence. RX-only devices must expose TX as unavailable. Half-duplex
and full-duplex are distinct from merely having both TX and RX support.

Network settings should expose active adapter, link state, mode/profile,
measured rate/RTT/loss, driver version, queued messages, transmission ownership
and stop/revoke. Agents use typed bounded send/status/cancel operations after
owner delegation; unsupported devices remain unavailable. Implement a real
adapter before adding its controls. Start with an existing owner-controlled IP
radio bridge or serial packet modem, then extend verified driver coverage.

Model `RadioAdapter` capabilities: driver/build ID, physical device identity,
RX/TX/bidirectional support, supported bands and frequency ranges, modulation and
protocol set, bandwidth/sample formats, half/full-duplex, antenna/clock metadata,
configured power/rate limits, link metrics, permissions and observed state.
Pairing is explicit and does not infer transmit consent. Default transmit off;
owner-approved radio profiles establish permissible frequency/mode/power/duration
and equipment constraints. Revocation/expiry must stop owned transmission and
record the actual hardware acknowledgement. Frequency/region-specific compliance
is resolved for the deployment, not invented by a universal default band preset.

Carriers can be USB/serial/Bluetooth-connected radios, approved remote SDR gateways,
existing microwave point-to-point IP bridges, cellular data, Wi-Fi or other real
links. Put transport behind a common framed-message interface with MTU, reliable
and unreliable modes, encryption/authentication, replay protection, fragmentation,
acknowledgments, rate/backpressure and truthful link-loss states. Do not expose
raw SDR transmit loops to autonomous models without bounded owner delegation.

Use low-rate/long-range links for small tasks, heartbeats, status and recovery
references. Transfer bulk artifacts only with measured capacity, integrity and
resume support. Layer activations have strict bandwidth/RTT/memory demands:
benchmark the actual path before admitting a radio node to a sharded generation.
No assumption that HF/LoRa-like links can continuously carry large-model tensors.
Store-and-forward task routing, leases and deduplication are preferable for slow
or intermittent links. Broadband microwave/IP uses the same authenticated mesh
transport after measurement and owner pairing.

Signal tasks can include owner-authorized spectrum/FFT measurements, noise-floor
estimation, antenna/link diagnostics, demodulation of authorized feeds, sensor
fusion and radar sample analysis. Track real sample rate, center frequency,
calibration, gain, timestamps, units, clipping and dropped samples. Missing
calibration means relative amplitude, not a fabricated absolute dBm measurement.

Open-source components reviewed as candidates, not bundled capabilities:
- SoapySDR: vendor/platform-neutral SDR support, Boost Software License 1.0.
  https://github.com/pothosware/SoapySDR
- liquid-dsp: native DSP building blocks; inspect/preserve its license at a pinned
  revision before integration. https://github.com/jgaeddert/liquid-dsp
- GNU Radio: mature radio/DSP host ecosystem; keep licensing boundaries explicit
  for this Apache app/optional companion. https://github.com/gnuradio/gnuradio
- Reticulum: a candidate task/telemetry carrier over heterogeneous physical links;
  review protocol/identity/resource constraints before writing an adapter.
  https://github.com/markqvist/Reticulum

Do not implement an SSH/TLS/DSP modem from scratch where a reviewed maintained
library fits. Do not treat upstream installation as proof of a working Android
USB driver, radio link, transmission or radar interpretation.

Radio acceptance: two owned compatible radios, independently paired identities,
real bidirectional task/ack transfer, negative cases for an unsupported band/mode,
no TX before consent, loss/reconnect, revoke/emergency stop, actual throughput/RTT,
fragmentation, duplicate/replay rejection, calibration limits and physical power
measurement. Layer jobs require a separate real-model throughput/memory test.

## Core priority and next build sequence

1. Prove current real model generation and physical phone RPC layer execution.
2. Preserve strict resource/capability admission and implement committed phone
   journals/leases/checkpoint replay before promising crash recovery.
3. Build a small Linux/Pi companion with the actual matching CPU runtime, storage,
   tool and capture adapters; test each advertised capability.
4. Add a minimal MCU gateway/firmware with authenticated telemetry and bounded
   actions; avoid advertising transformer capabilities without execution proof.
5. Add media/DSP/radio plugins one at a time, using the same task, budget, audit,
   permission, revocation and failure contracts as model jobs.
6. Integrate cross-cluster task handoff and declarative deployment only after the
   corresponding real resource implementations and partition tests exist.
