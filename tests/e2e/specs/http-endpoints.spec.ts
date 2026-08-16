/**
 * @http — Probes the on-device Meshlit HTTP server's surface.
 *
 * The Meshlit app exposes a JSON API on port 8080 (NanoHTTPD).
 * Verified contract (from curl on 2026-08-15):
 *
 *   GET /v1/health        → { status, engine, port, capabilityTier,
 *                             engineTag, runtimeId, runtimeDisplayName,
 *                             metrics: { queueDepth, totalJobs, ... } }
 *   GET /v1/capabilities  → { peerId, nodeId, capabilityTier,
 *                             freeRamMb, freeDiskMb, tier, gpuBackend,
 *                             deviceClass, deviceName, ... }
 *   GET /v1/runtimes      → { deviceRuntimeId, runtimes: [
 *                             { runtimeId, displayName, status,
 *                               supportedFormats, approxApkFootprintBytes } ] }
 *   GET /v1/model         → { loaded, name, contextSize, ... }
 *
 * These tests confirm the JSON contract is intact and that the
 * device's nodeId matches the value the server reports back.
 */

import { test, expect } from '@playwright/test';

const baseUrl = process.env.MESHLIT_BASE_URL ?? 'http://127.0.0.1:8080';
const expectedNodeId = process.env.MESHLIT_NODE_ID ?? '';

test.describe('Meshlit HTTP server @http @smoke', () => {
  test('GET /v1/health returns status=ok', async ({ request }) => {
    const r = await request.get(`${baseUrl}/v1/health`);
    expect(r.status()).toBe(200);
    const body = await r.json();
    expect(body.status).toBe('ok');
    expect(typeof body.engine).toBe('string');
    expect(typeof body.port).toBe('number');
  });

  test('GET /v1/runtimes advertises at least one shipped runtime', async ({ request }) => {
    const r = await request.get(`${baseUrl}/v1/runtimes`);
    expect(r.status()).toBe(200);
    const body = await r.json();
    expect(Array.isArray(body.runtimes)).toBe(true);
    expect(body.runtimes.length).toBeGreaterThan(0);
    const shipped = body.runtimes.filter((rt: any) => rt.status === 'shipped');
    expect(shipped.length).toBeGreaterThan(0);
    expect(typeof body.summary.shippedCount).toBe('number');
  });

  test('GET /v1/model reports load state', async ({ request }) => {
    const r = await request.get(`${baseUrl}/v1/model`);
    expect(r.status()).toBe(200);
    const body = await r.json();
    expect(typeof body.loaded).toBe('boolean');
  });

  test('GET /v1/capabilities reports hardware + nodeId', async ({ request }) => {
    const r = await request.get(`${baseUrl}/v1/capabilities`);
    expect(r.status()).toBe(200);
    const body = await r.json();
    expect(typeof body.nodeId).toBe('string');
    expect(body.nodeId.length).toBeGreaterThan(0);
    expect(typeof body.freeRamMb).toBe('number');
    expect(typeof body.freeDiskMb).toBe('number');
    if (expectedNodeId) {
      expect(body.nodeId).toBe(expectedNodeId);
    }
  });
});
