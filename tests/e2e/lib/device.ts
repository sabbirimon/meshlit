/**
 * Discovers the Samsung device's network endpoints at runtime.
 *
 * Resolution order (each step short-circuits on success):
 *   1. `adb shell ss -lnt` confirms the on-device HTTP server is
 *      bound on the configured port.
 *   2. `adb shell ip -4 addr show wlan0` (or fallback to `eth0` /
 *      radio0) extracts the device's local IPv4 — this is the host
 *      our tests will hit.
 *   3. `adb logcat` greps the last 1500 lines for either
 *      `ctx={host=…, port=8080, …}` (InferenceHttpServer / router
 *      stack log lines) or the older `host=…` / `port=…` format.
 *      The first hit gives us port + nodeId + tier.
 *   4. Hard-coded fallback to 192.168.1.145:8080 so the harness
 *      stays useful even if all signals are absent.
 *
 * The output is cached in `process.env.MESHLIT_*` so every spec can
 * read it without re-resolving.
 */

import { Adb } from './adb';

export interface DeviceEndpoints {
  host: string;
  port: number;
  nodeId: string;
  tier: string;
  fingerprint: string;
  baseUrl: string;
}

const MESH_PACKAGE = 'com.meshlit.debug';
const DEFAULT_PORT = 8080;
const DEFAULT_HOST = '192.168.1.145';
const DEFAULT_NODE_ID = '';
const DEFAULT_TIER = 'LITE';

export async function discoverDevice(adb: Adb): Promise<DeviceEndpoints> {
  // 1. Confirm the port is bound (cheap sanity check, also wakes up
  //    any race after force-stop + start).
  const portBound = await adb.run(['shell', 'ss', '-lnt']);
  const port = parsePort(portBound.stdout, DEFAULT_PORT);

  // 2. Resolve the device's local IPv4 from `ip addr`.
  const host = await resolveHost(adb) ?? DEFAULT_HOST;

  // 3. Grep logcat for richer signals — nodeId, tier, fingerprint.
  let nodeId = DEFAULT_NODE_ID;
  let tier = DEFAULT_TIER;
  let fingerprint = '';
  try {
    const log = await adb.readLogcat(undefined, 1500);
    const parsed = parseLogcat(log);
    if (parsed) {
      nodeId = parsed.nodeId || nodeId;
      tier = parsed.tier || tier;
      fingerprint = parsed.fingerprint || fingerprint;
    }
  } catch {
    // logcat is best-effort — the IP + port are enough for HTTP tests.
  }

  return {
    host,
    port,
    nodeId,
    tier,
    fingerprint,
    baseUrl: `http://${host}:${port}`,
  };
}

function parsePort(ssOutput: string, fallback: number): number {
  // `ss -lnt` looks like:
  //   LISTEN  0  0  *:8080  *:*
  // Multiple ports may be listening (Meshlit has 8080 + 8081, plus
  // unrelated services). Prefer the Meshlit default 8080 if present,
  // otherwise fall back to the first LISTEN entry that's >= 1024.
  if (/(?:^|\s)\*:?8080(?:\s|$)/m.test(ssOutput)) return 8080;
  const m = ssOutput.match(/(?:^|\s)\*:?(\d{4,5})(?=\s|$)/m);
  if (m) {
    const p = Number(m[1]);
    if (p >= 1024 && p <= 65535) return p;
  }
  return fallback;
}

async function resolveHost(adb: Adb): Promise<string | null> {
  const interfaces = ['wlan0', 'eth0', 'radio0'];
  for (const iface of interfaces) {
    const r = await adb.run(['shell', 'ip', '-4', 'addr', 'show', iface]);
    const m = r.stdout.match(/inet\s+(\d{1,3}(?:\.\d{1,3}){3})/);
    if (m) return m[1];
  }
  // Last-ditch: any inet line.
  const all = await adb.run(['shell', 'ip', '-4', 'addr']);
  const m = all.stdout.match(/inet\s+(\d{1,3}(?:\.\d{1,3}){3})/);
  return m ? m[1] : null;
}

interface LogcatSignals {
  nodeId: string;
  tier: string;
  fingerprint: string;
}

function parseLogcat(log: string): LogcatSignals | null {
  // Format A (newer, NanoHTTPD / router stack):
  //   ctx={host=0.0.0.0, port=8080, nodeId=72c490ab, tier=LITE, fp=...}
  // Format B (older MeshlitApplication logger):
  //   host=192.168.1.145, port=8080, tier=LITE, nodeId=72c490ab
  const ctx = log.match(/ctx=\{[^}]*port=(\d+)[^}]*\}/);
  const portFromCtx = ctx ? Number(ctx[1]) : 0;
  const hostFromCtx = log.match(/ctx=\{[^}]*host=([\d.]+)[^}]*\}/)?.[1] ?? '';
  const nodeIdFromCtx = log.match(/ctx=\{[^}]*node[_-]?id=([0-9a-f]+)[^}]*\}/i)?.[1] ?? '';
  const tierFromCtx = log.match(/ctx=\{[^}]*tier=([A-Z_]+)[^}]*\}/)?.[1] ?? '';
  const fpFromCtx = log.match(/ctx=\{[^}]*fp=([0-9a-f]+)[^}]*\}/i)?.[1] ?? '';

  const hostFromB = log.match(/host=([\d.]+)/)?.[1] ?? '';
  const nodeIdFromB = log.match(/node[_-]?id=([0-9a-f]+)/i)?.[1] ?? '';
  const tierFromB = log.match(/tier=([A-Z_]+)/)?.[1] ?? '';

  const nodeId = nodeIdFromCtx || nodeIdFromB;
  const tier = tierFromCtx || tierFromB;
  const fingerprint = fpFromCtx;
  if (!nodeId && !tier && !hostFromB && !portFromCtx) return null;
  return { nodeId, tier, fingerprint };
}

export { MESH_PACKAGE };
