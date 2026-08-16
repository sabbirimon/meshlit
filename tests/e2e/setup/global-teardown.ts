/**
 * Global teardown: currently a no-op. Left as a hook so we can add
 * logcat archival, log upload, or a single force-stop once the rest
 * of the suite is wired up.
 */

export default async function globalTeardown() {
  // Intentionally empty — see global-setup.ts for the rationale.
}