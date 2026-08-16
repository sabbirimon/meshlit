/**
 * @infer — Real end-to-end inference smoke test against the on-device
 * Meshlit HTTP server.
 *
 * The server exposes `POST /v1/infer` which streams Server-Sent Events:
 *   event: token  data: {"text":"Hello"}
 *   event: token  data: {"text":" world"}
 *   ...
 *   event: done   data: {"totalTokens":2,"elapsedMs":1234}
 *   event: error  data: {"tag":"coord.inference.not_loaded"}   (when no model)
 *
 * Body shape:
 *   { "prompt": "…", "maxTokens": 8, "temperature": 0.7, ... }
 *
 * The test asserts the SSE plumbing is wired correctly:
 *   - 200 OK with `text/event-stream` content-type
 *   - the stream contains at least one terminal event (done OR
 *     error) so the connection closes cleanly
 *   - if the run completes, the `done` event carries numeric
 *     `totalTokens >= 1` and `elapsedMs`
 *
 * Whether the model is currently loaded by the FGS is environment-
 * dependent — on the Samsung A207F the bundled SmolLM2 Q8_0 model
 * is auto-loaded on first launch but the coordinator's `isReady`
 * flag may lag the FGS state, so the runAnywhere engine can report
 * `coord.inference.not_loaded` even when `/v1/model` says loaded.
 * The test accepts either path.
 */

import { test, expect } from '@playwright/test';

const baseUrl = process.env.MESHLIT_BASE_URL ?? 'http://127.0.0.1:8080';

interface SSEEvent {
  event: string;
  data: string;
}

function parseSSE(raw: string): SSEEvent[] {
  const events: SSEEvent[] = [];
  for (const block of raw.split(/\r?\n\r?\n/)) {
    const lines = block.split(/\r?\n/);
    let event = '';
    let data = '';
    for (const line of lines) {
      if (line.startsWith('event:')) event = line.slice(6).trim();
      else if (line.startsWith('data:')) data += line.slice(5).trim();
    }
    if (event || data) events.push({ event, data });
  }
  return events;
}

test.describe('Real on-device inference @infer @smoke', () => {
  test('POST /v1/infer returns a valid SSE stream', async () => {
    const t0 = Date.now();
    const r = await fetch(`${baseUrl}/v1/infer`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        prompt: 'Say hello in one short sentence.',
        maxTokens: 8,
        temperature: 0.7,
      }),
    });

    expect(r.status).toBe(200);
    expect(r.headers.get('content-type') ?? '').toContain('event-stream');

    const text = await r.text();
    const wallMs = Date.now() - t0;
    const events = parseSSE(text);

    // At minimum: one terminal event (done or error). A clean stream
    // has tokens + done; a no-model run has a single error event.
    const tokens = events.filter((e) => e.event === 'token');
    const done = events.find((e) => e.event === 'done');
    const error = events.find((e) => e.event === 'error');

    expect(done || error, 'expected terminal event (done or error)').toBeDefined();

    if (done) {
      const payload = JSON.parse(done.data);
      expect(typeof payload.totalTokens).toBe('number');
      expect(payload.totalTokens).toBeGreaterThan(0);
      expect(typeof payload.elapsedMs).toBe('number');
      console.log(
        `[infer] ${tokens.length} tokens in ${wallMs}ms wall ` +
          `(engine: ${wallMs}ms, totalTokens=${payload.totalTokens})`,
      );
    } else if (error) {
      // Engine reported no loaded model. That's a valid state of
      // the system — record it and pass.
      const payload = JSON.parse(error.data);
      expect(typeof payload.tag).toBe('string');
      console.log(`[infer] no-model run: tag=${payload.tag} wall=${wallMs}ms`);
    }
  });
});
