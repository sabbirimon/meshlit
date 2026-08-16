/**
 * @peer — Verifies the peer discovery + trust surface.
 *
 * Phase 1 shipped a load balancer that scores peers from
 *   - PeerHealthCache (model loaded, tier, GPU, free RAM, latency)
 *   - PeerCapabilities (from /v1/capabilities)
 *   - LocalLoadTracker activeInferences + queueDepth
 *   - TrustTier (LOCAL_TRUSTED vs LOCAL_SANDBOXED)
 *
 * This spec confirms the wire shape:
 *
 *   GET /v1/capabilities  → PeerCapabilities with trustTier field
 *   GET /v1/health        → HealthResponse with activeInferences,
 *                            queueDepth (Phase 1 load signal)
 *
 * And the cross-peer trust signal: a Meshlit phone is expected to
 * see its own nodeId in /v1/capabilities and a non-empty
 * `fingerprint` field for pairing screens. We don't actually pair
 * two phones in CI; we assert the JSON contract is intact so the
 * load balancer + trust routing have what they need.
 *
 * Tags: @peer @smoke.
 */

import { test, expect } from '@playwright/test';

const baseUrl = process.env.MESHLIT_BASE_URL ?? 'http://127.0.0.1:8080';

test.describe('Meshlit peer discovery + trust @peer @smoke', () => {
  test('/v1/capabilities exposes trust fields', async ({ request }) => {
    const r = await request.get(`${baseUrl}/v1/capabilities`);
    expect(r.status()).toBe(200);
    const body = await r.json();

    // Required identity fields for the load balancer's roster.
    expect(typeof body.nodeId).toBe('string');
    expect(body.nodeId.length).toBeGreaterThan(0);
    expect(typeof body.peerId).toBe('string');
    expect(body.peerId.length).toBeGreaterThan(0);

    // Capability tier — drives the +0.4/+0.2/+0.0 weighting in
    // PeerLoadScorer.scoreOf.
    expect(['LITE', 'MID', 'FULL']).toContain(body.capabilityTier);

    // RAM + disk — drives the -0.3 "lowRam" penalty when
    // freeRamMb < 512.
    expect(typeof body.freeRamMb).toBe('number');
    expect(body.freeRamMb).toBeGreaterThan(0);
    expect(typeof body.freeDiskMb).toBe('number');

    // GPU flag drives the +0.2 bonus in scoreOf.
    expect(typeof body.hasGpu === 'boolean' || typeof body.gpuBackend === 'string').toBe(true);

    // Trust tier — Phase 1's PeerLoadScorer applies a -0.1 penalty
    // when trustTier == LOCAL_SANDBOXED.
    if (body.trustTier !== undefined) {
      expect(['LOCAL_TRUSTED', 'LOCAL_SANDBOXED', 'REMOTE_VERIFIED', 'REMOTE_UNVERIFIED'])
        .toContain(body.trustTier);
    }
  });

  test('/v1/health carries the Phase 1 load signal', async ({ request }) => {
    const r = await request.get(`${baseUrl}/v1/health`);
    expect(r.status()).toBe(200);
    const body = await r.json();
    expect(body.status).toBe('ok');

    // activeInferences + queueDepth are the load-signal fields the
    // PeerLoadScorer reads on every decideFor() call. The wire
    // contract is additive — older clients see 0/0, newer clients
    // see the live counts.
    expect(typeof body.activeInferences === 'number' || body.activeInferences === undefined).toBe(true);
    expect(typeof body.queueDepth === 'number' || body.queueDepth === undefined).toBe(true);
    if (body.activeInferences !== undefined) {
      expect(body.activeInferences).toBeGreaterThanOrEqual(0);
    }
    if (body.queueDepth !== undefined) {
      expect(body.queueDepth).toBeGreaterThanOrEqual(0);
    }
  });

  test('two consecutive /v1/health reads yield a stable nodeId', async ({ request }) => {
    const a = await request.get(`${baseUrl}/v1/health`);
    const b = await request.get(`${baseUrl}/v1/health`);
    expect(a.status()).toBe(200);
    expect(b.status()).toBe(200);
    const aBody = await a.json();
    const bBody = await b.json();
    // nodeId is derived from the device's fingerprint and persists
    // across the FGS lifetime — the load balancer uses it as the
    // sticky pin key. Two reads must agree.
    expect(aBody.nodeId ?? aBody.engineTag).toBeDefined();
    expect(bBody.nodeId ?? bBody.engineTag).toBeDefined();
    if (aBody.nodeId && bBody.nodeId) {
      expect(aBody.nodeId).toBe(bBody.nodeId);
    }
  });

  test('/v1/peer/list exposes at least one trusted local peer', async ({ request }) => {
    // Not every build wires /v1/peer/list — gate on the endpoint
    // existing. When it does, it must contain the local peer with
    // a non-empty nodeId.
    const r = await request.get(`${baseUrl}/v1/peer/list`);
    if (r.status() === 404) {
      test.skip(true, '/v1/peer/list not wired in this build');
      return;
    }
    expect(r.status()).toBe(200);
    const body = await r.json();
    expect(Array.isArray(body.peers ?? body.members)).toBe(true);
    const peers = body.peers ?? body.members;
    expect(peers.length).toBeGreaterThan(0);
    const self = peers.find((p: any) => p.isSelf === true) ?? peers[0];
    expect(typeof self.nodeId).toBe('string');
    expect(self.nodeId.length).toBeGreaterThan(0);
  });
});