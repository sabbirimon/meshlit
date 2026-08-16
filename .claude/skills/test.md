---
name: test
description: Run unit tests for the project modules.
tools: Bash
---

# /test — Run Meshlit unit tests

Runs the `:core-*` unit test suites. Slow modules (`:core-inference`,
`:core-orchestration`) are skipped by default — opt in with the
module-name flag.

## Default scope

```
./gradlew :core-cloud-mcp:testDebugUnitTest \
          :core-trust:testDebugUnitTest \
          :core-common:testDebugUnitTest \
          :core-mcp:testDebugUnitTest
```

This is the fast feedback loop — every new helper class in
`:core-cloud-mcp` should ship with at least one test that exercises
the happy path and one that exercises the failure path.

## Full module sweep

```
./gradlew testDebugUnitTest
```

Use this before opening a PR. Slow — ~3 minutes on a clean cache.

## Writing a new test

- JUnit 4 + `runBlocking` (the project convention — no `kotlinx-coroutines-test`).
- Pure tests live in `src/test/`. Robolectric / instrumentation
  tests live in `src/androidTest/`.
- For SSE / JSON-RPC fixtures, hand-roll the response instead of
  spinning up a `MockWebServer`. See `SseParserTest` for the
  pattern.
