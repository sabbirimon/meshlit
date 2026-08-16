/**
 * Global setup: discovers the live Meshlit device on R9KN2009CZJ (or
 * MESHLIT_DEVICE override) once before the suite runs, captures its
 * HTTP base URL + nodeId, and exposes them via process.env so every
 * spec can read `process.env.MESHLIT_BASE_URL` without re-resolving.
 *
 * If the device isn't reachable, we fail fast with a clear error —
 * the suite should never silently pass against a missing endpoint.
 */

import { Adb } from '../lib/adb';
import { discoverDevice } from '../lib/device';

const DEVICE_SERIAL = process.env.MESHLIT_DEVICE ?? 'R9KN2009CZJ';
const MESH_PACKAGE = 'com.meshlit.debug';
const MESH_ACTIVITY = 'com.meshlit.MainActivity';

export default async function globalSetup() {
  const adb = new Adb(DEVICE_SERIAL);

  // 1. Make sure adb sees the device.
  const devs = await adb.run(['devices']);
  if (!devs.stdout.includes(DEVICE_SERIAL)) {
    throw new Error(
      `adb does not see ${DEVICE_SERIAL}.\n` +
        `Run: adb devices\n` +
        `Set MESHLIT_DEVICE to override.`,
    );
  }

  // 2. Bring the app to a known foreground state. force-stop + start
  //    makes logcat + NSD re-register cleanly.
  await adb.stopApp(MESH_PACKAGE);
  await adb.startApp(MESH_PACKAGE, MESH_ACTIVITY);

  // 3. Wait until the on-device HTTP server is bound on 8080.
  try {
    await adb.waitForPort(8080, 45_000);
  } catch (e) {
    throw new Error(
      `Meshlit HTTP server never bound port 8080 on ${DEVICE_SERIAL}: ${(e as Error).message}`,
    );
  }

  // 4. Resolve the device endpoints (host / port / nodeId) via NSD,
  //    falling back to logcat.
  const endpoints = await discoverDevice(adb);

  // 4a. If the discoverer returned the LAN IP but `adb forward` is
  //     wired up, prefer the loopback base URL. The CI host is rarely
  //     on the same subnet as the phone; adb-forward is the only
  //     reliable bridge. `discoverDevice` keeps the LAN IP for
  //     cluster / discovery tests that actually need it.
  const loopbackUrl = `http://127.0.0.1:${endpoints.port}`;
  try {
    const loop = await fetch(`${loopbackUrl}/v1/health`, {
      signal: AbortSignal.timeout(3000),
    });
    if (loop.ok) endpoints.baseUrl = loopbackUrl;
  } catch {
    // Stick with the LAN URL.
  }

  // 5. Smoke-test the HTTP server.
  const probe = await fetch(`${endpoints.baseUrl}/v1/health`);
  if (!probe.ok) {
    throw new Error(
      `Device HTTP server reachable but /v1/health returned ${probe.status}`,
    );
  }

  // 6. Prefer the canonical nodeId from /v1/capabilities — logcat
  //    truncates it to 8 hex chars, the server keeps the full 16.
  let canonicalNodeId = endpoints.nodeId;
  try {
    const capsResp = await fetch(`${endpoints.baseUrl}/v1/capabilities`);
    if (capsResp.ok) {
      const caps = (await capsResp.json()) as { nodeId?: string };
      if (caps.nodeId && caps.nodeId.length >= 8) {
        canonicalNodeId = caps.nodeId;
      }
    }
  } catch {
    // fall through to logcat-derived value
  }

  // 7. Make endpoints available to every spec.
  process.env.MESHLIT_BASE_URL = endpoints.baseUrl;
  process.env.MESHLIT_NODE_ID = canonicalNodeId;
  process.env.MESHLIT_TIER = endpoints.tier;
  process.env.MESHLIT_DEVICE = DEVICE_SERIAL;

  console.log(
    `[global-setup] device=${DEVICE_SERIAL} ` +
      `node=${canonicalNodeId} ` +
      `tier=${endpoints.tier} ` +
      `base=${endpoints.baseUrl}`,
  );
}