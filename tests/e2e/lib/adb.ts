/**
 * Adb wrapper for the Meshlit E2E test harness.
 *
 * Thin promise-based wrapper around `adb -s <serial> <cmd>`. Designed
 * to be awaited from Playwright tests — never call out to `adb`
 * directly from the spec files.
 *
 * Why centralised:
 *   - One place to handle the device serial (via `MESHLIT_DEVICE` env).
 *   - One place to serialise logcat captures (path under
 *     `test-results/<test-name>/logcat.txt`).
 *   - One place to swallow the harmless "device offline" race that
 *     happens right after `force-stop` + `start`.
 */

import { spawn } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

export interface AdbResult {
  stdout: string;
  stderr: string;
  code: number;
}

export class Adb {
  constructor(public readonly serial: string) {}

  /** Run an adb subcommand on the configured device. */
  async run(args: string[], opts: { timeoutMs?: number } = {}): Promise<AdbResult> {
    const allArgs = ['-s', this.serial, ...args];
    return new Promise<AdbResult>((resolve, reject) => {
      const child = spawn('adb', allArgs, { stdio: ['ignore', 'pipe', 'pipe'] });
      let stdout = '';
      let stderr = '';
      const timer = setTimeout(() => {
        child.kill('SIGKILL');
        reject(new Error(`adb ${allArgs.join(' ')} timed out after ${opts.timeoutMs ?? 30_000}ms`));
      }, opts.timeoutMs ?? 30_000);
      child.stdout.on('data', (d) => { stdout += d.toString(); });
      child.stderr.on('data', (d) => { stderr += d.toString(); });
      child.on('error', reject);
      child.on('close', (code) => {
        clearTimeout(timer);
        resolve({ stdout, stderr, code: code ?? -1 });
      });
    });
  }

  /** Tap a screen coordinate. Adds a 50ms settle delay so the
   *  event is processed before the next action. */
  async tap(x: number, y: number): Promise<void> {
    await this.run(['shell', 'input', 'tap', String(x), String(y)]);
    await sleep(50);
  }

  /** Type text via `input text`. Spaces become %s. */
  async typeText(text: string): Promise<void> {
    await this.run(['shell', 'input', 'text', text.replace(/ /g, '%s')]);
  }

  /** Press a single key. */
  async key(key: string): Promise<void> {
    await this.run(['shell', 'input', 'keyevent', key]);
  }

  /** Force-stop the app under test. */
  async stopApp(packageId: string): Promise<void> {
    await this.run(['shell', 'am', 'force-stop', packageId]);
  }

  /** Launch the app under test. */
  async startApp(packageId: string, activity: string): Promise<void> {
    await this.run([
      'shell', 'am', 'start', '-W',
      '-n', `${packageId}/${activity}`,
    ]);
  }

  /** Capture a PNG screenshot to the device, then pull to local disk. */
  async screenshot(localPath: string): Promise<void> {
    const onDevice = '/sdcard/meshlit-e2e-snap.png';
    await this.run(['shell', 'screencap', '-p', onDevice]);
    await this.run(['pull', onDevice, localPath]);
  }

  /** Start a long-running logcat capture, returning a [stopFn]. */
  async beginLogcat(outDir: string): Promise<() => Promise<void>> {
    await mkdir(outDir, { recursive: true });
    const outFile = join(outDir, 'logcat.txt');
    const child = spawn('adb', [
      '-s', this.serial, 'logcat', '-v', 'threadtime',
    ], { stdio: ['ignore', 'pipe', 'pipe'] });
    const stream = (await import('node:fs')).createWriteStream(outFile);
    child.stdout.pipe(stream);
    return async () => {
      child.kill('SIGINT');
      await new Promise<void>((resolve) => stream.end(resolve));
    };
  }

  /** Read the last N logcat lines, optionally filtered by tag. */
  async readLogcat(tag?: string, lines = 200): Promise<string> {
    const r = await this.run(['logcat', '-d', '-t', String(lines), tag ? `*:S ${tag}:V` : '*:V']);
    return r.stdout;
  }

  /** Wait for the on-device HTTP server to bind its port. */
  async waitForPort(port: number, timeoutMs = 30_000): Promise<void> {
    const start = Date.now();
    while (Date.now() - start < timeoutMs) {
      const r = await this.run([
        'shell', 'ss', '-lnt', 'sport', '=', `:${port}`,
      ]);
      if (r.stdout.includes(`:${port}`)) return;
      await sleep(500);
    }
    throw new Error(`port ${port} did not bind within ${timeoutMs}ms`);
  }
}

export function sleep(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}
