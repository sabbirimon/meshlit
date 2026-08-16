/**
 * Playwright config for the Meshlit Android E2E test harness.
 *
 * Scope: drive the live Meshlit app on `R9KN2009CZJ` (Samsung SM-A207F)
 * via:
 *   1. `adb shell` (UI taps, logcat, screencaps)
 *   2. HTTP probes against the device's local server (port 8080)
 *   3. mDNS / NsdService discovery (peer presence)
 *
 * Why Playwright (a browser tool) for an Android app:
 *   - Playwright's `request` fixture is a thin HTTP/JSON client
 *     that has nothing to do with a browser — it works fine for
 *     probing the on-device HTTP server and posting JSON-RPC to
 *     `/v1/infer`.
 *   - The `test()` runner + fixtures + the `--grep @smoke` filter
 *     pattern are exactly what we want for an E2E suite that
 *     spans HTTP, NSD, and adb-driven UI navigation.
 *
 * Browser projects (`chromium`) are kept enabled for the optional
 * Meshlit web admin UI tests; they don't auto-launch unless a
 * spec calls `await chromium.launch()`.
 *
 * Device resolution:
 *   - Defaults to `R9KN2009CZJ`; override via `MESHLIT_DEVICE=<id>`
 *     so the same suite runs on any connected phone or emulator.
 */
import { defineConfig, devices } from '@playwright/test';

const deviceSerial = process.env.MESHLIT_DEVICE ?? 'R9KN2009CZJ';

export default defineConfig({
  testDir: './specs',
  fullyParallel: false,    // The Samsung only takes one adb stream at a time
  workers: 1,
  retries: 0,
  reporter: process.env.CI ? 'github' : [['list'], ['html', { open: 'never' }]],
  timeout: 60_000,          // 60s per test; cold model load can take longer
  expect: { timeout: 10_000 },
  use: {
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'samsung-r9kn2009czj',
      use: {
        ...devices['Pixel 5'],  // viewport only — we never render in the browser
      },
    },
  ],
  globalSetup: require.resolve('./setup/global-setup.ts'),
  globalTeardown: require.resolve('./setup/global-teardown.ts'),
  metadata: {
    deviceSerial,
    testTarget: 'meshlit-android-on-samsung',
  },
});