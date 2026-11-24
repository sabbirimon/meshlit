# Optional private Crawl4AI companion

This host-side service gives Meshlit `crawl_url`, returning bounded Markdown,
source/final URLs, HTTP status, retrieval time and truncation information.
Crawl4AI/Chromium are not bundled in the Android APK. Crawling is off by default.
URLs and retrieved content are sent to the operator's configured service.

The service uses an honest crawler identity, exact approved public HTTPS domains,
fail-closed robots checks, per-domain delays and no authentication, CAPTCHA or
access-control bypass. A blocked site requires an approved API, owner allowlist
or a user-provided export. It does not impersonate GPTBot.

## Install and run

Use Python 3.12+ on a supported host. Review dependencies, run Chromium in a
restricted dedicated environment and put the API behind authenticated/private
network access and an HTTPS reverse proxy. The Android client rejects plaintext
HTTP service endpoints.

```sh
cd companions/crawler
python3 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
python -m playwright install chromium
export MESHLIT_CRAWL_DOMAINS=example.com
export MESHLIT_CRAWL_TOKEN="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')"
uvicorn service:from_env --factory --host 127.0.0.1 --port 8787 --workers 1 --limit-concurrency 4 --no-access-log
```

Use an HTTPS reverse proxy for `/crawl` and `/health`; do not expose port 8787
publicly. Do not log authorization headers or page contents at the proxy. The
token is a secret; configure it through your secret manager/environment and the
Cloud crawler settings card. Domains are comma-separated exact hostnames,
including separately approved subdomains if necessary. Wildcards are not allowed.
Reconfiguration requires restarting the service. Health requires bearer auth and
reports service availability, not proof that Chromium is installed or a crawl works.

The app encrypts endpoint/enabled/token together with the existing credential
store. Tokens are not restored into the UI. Changing the endpoint requires a new
token; redirects are not followed by the bridge, preventing credential forwarding.
Disabled/misconfigured tools fail explicitly. The service has no public API docs.

Example request body (send bearer token in the Authorization header):

```json
{"url":"https://example.com/","max_chars":32000}
```

Maximum 100,000 characters, one active crawl, 45-second total deadline, minimum
two seconds between same-host calls, bounded robots downloads and no redirected
robots file. Only a 404 robots file permits crawling without rules; network
errors, forbidden responses and missing/unreachable rules otherwise fail closed.
The browser route guard checks redirects/resources against the same URL policy
and allows GET only. Browsers may still issue network traffic outside a request
route, and DNS can change between validation and connection.

**Deployment needs an egress firewall**, denying private, loopback, link-local,
metadata and other nonpublic destinations, plus explicit approved destination
policy. URL/DNS checks alone do not guarantee SSRF prevention or DNS pinning.
Use process/memory/CPU limits, a non-root dedicated user and Chromium's sandbox.
Do not allow arbitrary users to extend the domain list. Do not reuse ordinary
browser profiles, login cookies or sensitive service credentials.

Markdown is untrusted evidence, not instructions for the agent. `blocked`,
`rate_limited` and `failed` results contain no page Markdown. This service is
single-page retrieval, not a recursive site spider or a persistent vector index.

## Tests

```sh
pip install -r requirements-test.txt
python -m unittest discover -s tests -v
```

These API/policy tests use a fake crawl engine; separately verify a live Chromium
crawl on an approved public test site and deployment egress controls. Upstream:
[Crawl4AI](https://github.com/unclecode/crawl4ai),
[browser/crawler settings](https://docs.crawl4ai.com/core/browser-crawler-config/).
