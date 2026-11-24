# Meshlit E2E — Playwright test harness

End-to-end tests for the Meshlit Android app. Drives a connected
device (default: `R9KN2009CZJ`) via:

- `adb shell` for UI taps + logcat
- The on-device HTTP server (`http://127.0.0.1:8080`) for JSON probes
- mDNS / NsdService for peer discovery

Playwright is used as a thin test runner + HTTP client — it does not
launch a browser unless a spec asks it to.

## Layout

```
tests/e2e/
  playwright.config.ts        — single project targeting the phone
  package.json                — npm scripts (@smoke / @ui / @http / @infer / @peer)
  lib/
    adb.ts                    — typed adb wrapper (run, tap, screencap, logcat)
    device.ts                 — discoverDevice(): IP + port + nodeId via logcat
  setup/
    global-setup.ts           — fails fast if the device is unreachable
    global-teardown.ts
  specs/
    http-endpoints.spec.ts    — @http   /v1/health, /v1/capabilities, /v1/model
    inference-smoke.spec.ts   — @infer  end-to-end inference flow
    ui-navigation.spec.ts     — @ui     bottom-nav + dashboard top pill
    peer-discovery.spec.ts    — @peer   /v1/capabilities + load signal
```

## Running locally

```bash
cd tests/e2e
npm install
npx playwright install --with-deps chromium   # one-time

# Run a tag
npm run test:e2e:smoke          # @smoke — fastest, runs everywhere
npm run test:e2e:http           # HTTP probes only
npm run test:e2e:infer          # inference flow
npm run test:e2e:peer           # peer discovery + trust fields
npm run test:e2e:ui             # bottom-nav UI (needs a screen-on device)
npm run test:e2e:list           # see every test without running it

# Override the device serial
MESHLIT_DEVICE=emulator-5554 npm run test:e2e:smoke

# Loopback mode (when CI can't reach the phone's LAN IP)
MESHLIT_BASE_URL=http://127.0.0.1:8080 npm run test:e2e:smoke
```

`adb forward tcp:8080 tcp:8080` is set up automatically by
`global-setup.ts` when the suite detects a loopback path; otherwise
the suite uses the LAN IP discovered from `adb shell ip addr`.

## CI

`.github/workflows/playwright-e2e.yml` runs the suite on every push
that touches `app/`, `core-inference/`, `core-net/`, or
`tests/e2e/`. The job requires a self-hosted runner labelled
`meshlit-android` with a USB-attached phone.

## Adding a new spec

1. Create `specs/<name>.spec.ts` and tag every `test(...)` with at
   least `@smoke` so the default grep picks it up.
2. If the spec needs adb, import `Adb` from `lib/adb` — never call
   `adb` from a spec directly.
3. If the spec needs the device endpoint, rely on
   `process.env.MESHLIT_BASE_URL` rather than re-discovering it.
4. Add an `npm run` script in `package.json` if the new tag is worth
   filtering on its own.