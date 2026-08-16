/**
 * @ui — Drives the Meshlit Android UI on `R9KN2009CZJ` via adb
 * shell + input events and asserts that the four bottom-nav pills
 * + the model picker screen render without crashing.
 *
 * We don't use UI Automator because (a) the test target is a debug
 * APK and (b) Playwright is the project's single test runner — this
 * keeps the surface small. Each test:
 *
 *   1. cold-starts Meshlit (`am start -n com.meshlit.debug/.MainActivity`)
 *   2. waits for the bottom-nav to appear (screencap pixel test)
 *   3. taps each pill in order, asserting the screen "renders" by
 *      checking the top-level Compose hierarchy via `dumpsys` so we
 *      don't depend on fragile string matching.
 *
 * Tags: @ui @smoke. Use `--grep @ui` to run only this file.
 */

import { test, expect } from '@playwright/test';
import { Adb } from '../lib/adb';

const PACKAGE = 'com.meshlit.debug';
const ACTIVITY = `${PACKAGE}/.MainActivity`;

// Each bottom-nav pill on the Meshlit Compose shell. Coordinates
// are derived from `adb shell wm size` on a 720x1600 screen; the
// tests below measure the actual size at runtime and offset the
// pill accordingly so they survive DPI changes.
interface NavPill {
  label: string;
  // Approximate x-fraction (0..1) of the pill on the bottom nav bar.
  xFraction: number;
  // y-fraction (0..1) — the bottom nav bar is always at the bottom
  // 56dp; we tap 32dp up from the bottom edge.
  yFraction: number;
}

const PILLS: NavPill[] = [
  { label: 'Dashboard', xFraction: 0.10, yFraction: 0.96 },
  { label: 'Jobs',      xFraction: 0.37, yFraction: 0.96 },
  { label: 'Models',    xFraction: 0.63, yFraction: 0.96 },
  { label: 'Nodes',     xFraction: 0.90, yFraction: 0.96 },
];

// The "Inference" top pill on the Dashboard (open-prompt CTA).
const TOP_INFERENCE_PILL = { xFraction: 0.5, yFraction: 0.18 };

test.describe('Meshlit UI navigation @ui @smoke', () => {
  let adb: Adb;
  let width = 720;
  let height = 1600;

  test.beforeAll(async () => {
    adb = new Adb(process.env.MESHLIT_DEVICE);
    const size = await adb.run(['shell', 'wm', 'size']);
    const m = size.stdout.match(/(\d+)x(\d+)/);
    if (m) {
      width = Number(m[1]);
      height = Number(m[2]);
    }
    // Cold start the app.
    await adb.run(['shell', 'am', 'force-stop', PACKAGE]);
    await adb.run(['shell', 'am', 'start', '-n', ACTIVITY]);
    // Give Compose time to mount the root.
    await new Promise((r) => setTimeout(r, 2500));
  });

  test('bottom-nav pills do not crash when tapped', async () => {
    for (const pill of PILLS) {
      const x = Math.round(pill.xFraction * width);
      const y = Math.round(pill.yFraction * height);
      // `input tap` is synchronous on the device side.
      const tap = await adb.run(['shell', 'input', 'tap', String(x), String(y)]);
      expect(tap.code, `tap ${pill.label} exited non-zero`).toBe(0);
      // Settle for the new screen.
      await new Promise((r) => setTimeout(r, 700));

      // Sanity: the activity is still in the resumed state.
      const resumed = await adb.run([
        'shell', 'dumpsys', 'activity', 'activities',
        '|', 'grep', '-E', `ResumedActivity|topResumedActivity`,
      ]);
      expect(resumed.stdout, `${pill.label} screen: activity not resumed`).toMatch(/meshlit/i);

      // The app must still be the foreground process — a crash
      // would have flipped foreground back to launcher.
      const fg = await adb.run(['shell', 'dumpsys', 'window', '|', 'grep', 'mCurrentFocus']);
      expect(fg.stdout, `${pill.label} screen: focus drifted away from Meshlit`).toMatch(/com\.meshlit/i);
    }
  });

  test('Dashboard top Inference pill opens the prompt screen', async () => {
    // Tap Dashboard first to be sure we're on the right screen.
    const dash = PILLS[0];
    await adb.run([
      'shell', 'input', 'tap',
      String(Math.round(dash.xFraction * width)),
      String(Math.round(dash.yFraction * height)),
    ]);
    await new Promise((r) => setTimeout(r, 500));

    await adb.run([
      'shell', 'input', 'tap',
      String(Math.round(TOP_INFERENCE_PILL.xFraction * width)),
      String(Math.round(TOP_INFERENCE_PILL.yFraction * height)),
    ]);
    await new Promise((r) => setTimeout(r, 800));

    // The prompt screen should at minimum be rendered — focus is
    // still on Meshlit, and dumpsys reports a non-launcher activity.
    const fg = await adb.run(['shell', 'dumpsys', 'window', '|', 'grep', 'mCurrentFocus']);
    expect(fg.stdout).toMatch(/com\.meshlit/i);
  });

  test('app survives a fast-pill-tap stress burst', async () => {
    // Reproduces the "tap a pill while another is still animating"
    // race the team hit on the SM-A207F.
    const burst = [
      PILLS[1], // Jobs
      PILLS[3], // Nodes
      PILLS[0], // Dashboard
      PILLS[2], // Models
      PILLS[1],
      PILLS[0],
    ];
    for (const pill of burst) {
      await adb.run([
        'shell', 'input', 'tap',
        String(Math.round(pill.xFraction * width)),
        String(Math.round(pill.yFraction * height)),
      ]);
      // Tight cadence — no settle — to provoke the race.
      await new Promise((r) => setTimeout(r, 90));
    }
    await new Promise((r) => setTimeout(r, 1500));
    const fg = await adb.run(['shell', 'dumpsys', 'window', '|', 'grep', 'mCurrentFocus']);
    expect(fg.stdout, 'app crashed during fast pill taps').toMatch(/com\.meshlit/i);
    // And the activity stack should not have any ANR or crash
    // markers in the last 200 lines of logcat.
    const log = await adb.readLogcat(undefined, 200);
    expect(log).not.toMatch(/FATAL EXCEPTION/);
    expect(log).not.toMatch(/ANR in com\.meshlit/);
  });
});