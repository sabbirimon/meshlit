# Cloud, credentials and environment management

Updated 2026-10-06. Dedicated drawer entry and Settings → Cloud and credentials.
Source implementation is separate from authenticated vendor/device acceptance.
No full-vendor-service completion claim is made.

## Available adapters

| Provider | Explicit functions | Authentication / boundary |
| --- | --- | --- |
| AWS | STS identity; EC2 instance page; month-to-date UnblendedCost through yesterday UTC | SigV4 access/temporary keys; session token required for ASIA keys. Cost Explorer requires metered-read consent and a us-east-1 profile; first UTC day has no elapsed current-month interval. No automatic billing requests |
| Azure | Subscriptions; selected subscription resources; month-to-date ActualCost query | Existing Entra bearer token and subscription ID; expired tokens need human rotation. No interactive OAuth refresh yet |
| DigitalOcean | Account; paged Droplets/GPU Droplets; actual balance/usage fields | Scoped bearer token. Inventory is per response page; no implicit enrollment of cloud resources as Meshlit workers |
| GCP | Projects; selected project's aggregated Compute instances; billing association | Existing OAuth access token. Billing association is not spend; cost reporting needs a future configured BigQuery billing-export adapter |
| OpenRouter | Actual key limit/usage information; real public model catalog | Key info uses API key; catalog can be fetched without a key. A public/free catalog does not grant inference entitlement |
| Custom / other providers | Human-configured function ID → fixed HTTPS path, GET only | Bearer CLOUD_API_TOKEN, public HTTPS origin on port 443. No arbitrary URL/method, credential-in-query, redirect or internal metadata fetch |

The dashboard derives resources/costs only from returned fields, retains source
and fetch time and warns about partial inventories and billing delay. Missing
spend/currency is unknown. Detailed bounded API data can be expanded. HTTP input is capped at 4 MiB;
returned/stored data is bounded to about 128 KiB, depth 16 and 100 entries per
collection, with explicit truncation/partial flags and at most 32 observations. Refreshes
are explicit; shared cooldown defaults to 30 seconds. Agent admission is committed
before a network attempt; default quota is 20 calls per UTC day. Failed admitted
attempts count against that quota. This is a request budget, not a guaranteed
currency spending cap. There is no automatic retry, paid fallback or provisioning.

Primary contracts reviewed:
[AWS STS](https://docs.aws.amazon.com/STS/latest/APIReference/API_GetCallerIdentity.html),
[AWS signatures](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_sigv-create-signed-request.html),
[AWS costs](https://docs.aws.amazon.com/aws-cost-management/latest/APIReference/API_GetCostAndUsage.html),
[Azure subscriptions](https://learn.microsoft.com/rest/api/resources/subscriptions/list),
[Azure cost query](https://learn.microsoft.com/en-us/rest/api/cost-management/query/usage?view=rest-cost-management-2025-03-01),
[DigitalOcean Droplets](https://docs.digitalocean.com/products/droplets/reference/api/droplets/),
[GCP billing association](https://docs.cloud.google.com/billing/docs/reference/rest/v1/projects/getBillingInfo),
[OpenRouter key information](https://openrouter.ai/docs/api/api-reference/api-keys/get-current-api-key).

## Credential/environment vault

Create a named environment, choose CLOUD/API/SSH/WEB_LOGIN and save named values.
All values stay in synchronously committed Keystore-backed encrypted preferences;
state flows and descriptions contain names only. Maximum 32 environments, 64
variables each, 128 KiB combined values per environment; private SSH keys may be
multiline up to 64 KiB. Optional explicit expiry fails closed. Credentials can be
rotated/deleted; profiles referencing an environment must be removed/reconfigured
before deleting it. No global process environment is injected. No agent command
accepts a credential value. Secrets are omitted from configuration and log exports.
Encrypted preferences/policies and native cache files are excluded from automatic
Android backup and device transfer; re-enroll and re-enter credentials on another
installation. Secret export/sync and OS-wide autofill are unavailable.

- Cloud profiles refer to environment ID and use documented variable names shown
  in configuration. API environments with a service origin cannot be used by a
  cloud adapter targeting another origin.
- Online model/media profiles can reference an API environment plus variable name,
  e.g. OPENROUTER_API_KEY. Bind it to the exact HTTPS origin of the API endpoint.
  A reference overrides a legacy saved key. Changing endpoint cannot silently
  redirect a vault secret to another service. Chat API access remains separate
  from cloud inventory/cost access.
- SSH connections can reference an SSH environment bound to the exact saved host;
  consume SSH_PASSWORD or SSH_PRIVATE_KEY. Existing mandatory host-key pinning and
  per-host agent approval still apply. No secret returns through the tool result.
- Browser → Saved login credentials → select a WEB_LOGIN profile → confirm sharing.
  LOGIN_USERNAME and LOGIN_PASSWORD are filled only into one visible top-level,
  same-origin login form on the exact saved HTTPS origin. No submit click, cross-
  origin form or iframe filling; ambiguous forms, MFA and CAPTCHA require a human.
  Page scripts can read values once filled. The model receives no credential.
  Password filling remains human-only; Android system/browser autofill integration
  and agent credential-mediated login are future adapters.

## Human and agent controls

Human/cloud enable and human action list are independent from agent/cloud enable
and agent action list. Agents also need global CLOUD delegation, environment
agent-use permission, and independently approved CLOUD access on enrolled remote
devices. No agent command changes credentials or its own permissions. Service
consumers enforce the applicable purpose/binding/expiry and agent permission.
Cloud configuration is rechecked after a request before persisting its result.
Revocation cannot undo a request already received by a vendor.

Typed durable operations: CLOUD_PROFILES, ENVIRONMENT_PROFILES, CLOUD_EXECUTE.
Execute parameters: cloudProfileId, cloudAction and cloudPage (1–100). Credentials
are resolved internally. DigitalOcean pages are supported; Azure/GCP/AWS opaque
pagination continuation is explicitly unavailable rather than claiming a full
inventory. Read-only POSTs are fixed Azure/AWS billing query bodies, not a generic
remote request tool.

## Remaining cloud roadmap and acceptance

Authenticated AWS/Azure/DO/GCP/OpenRouter accounts, IAM/RBAC failure cases, token
expiry, pagination and actual costs must be tested with owner-provided credentials.
No keys are requested in chat and no provider test is faked. Current unit fixtures
exercise request/auth/redaction contracts; they are not live account proof.

Add OAuth/PKCE/device login and secure refresh; encrypted key-file import, SSH
passphrases/agent support, scoped API-specific token templates and credential
migration; cloud inventories for storage/Kubernetes/serverless/databases/networks;
GCP billing export; cost history, currencies/tax/discount provenance and alerts;
service adapters with typed schemas; plan/preview/approve/apply deployments, money
budgets, idempotency/reconciliation and rollback; Terraform/Pulumi/Ansible host
companions; Chinese vendor adapters via reviewed official regional endpoints.
Do not enable create/delete/paid deployment through a generic custom GET entry.
Keep future platform/private-cloud endpoints behind explicit validated adapters.


## Terraform/OpenTofu, Pulumi and Ansible integration design

These belong in Cloud → Automation tools. This section records the requested
integration design; no managed IaC execution adapter or live apply is claimed yet.
Existing pinned SSH can execute operator commands, with its existing timeout/output
limits, but is not a durable infrastructure deployment manager.

| Tool | Role | Execution host |
| --- | --- | --- |
| Terraform or OpenTofu | Version-pinned provider modules, state-backed cloud resource lifecycle, saved plan/apply | Approved host with the selected CLI/plugins and encrypted/locked state backend |
| Pulumi | Code-based desired state and structured Automation API preview/up/refresh/destroy | Approved host with Pulumi CLI, chosen language runtime and Automation API companion |
| Ansible | Configure provisioned machines, deploy Meshlit workers, install/configure services, inventory-based maintenance | Approved POSIX/Python control host, verified SSH inventory and pinned collections |
| Packer / cloud-init / Helm | Reviewed machine images, bootstrap and Kubernetes deployment | Optional capability-specific host adapters after the first tools are proven |

Source contracts: [Terraform saved plans](https://developer.hashicorp.com/terraform/cli/commands/plan),
[OpenTofu plans](https://opentofu.org/docs/cli/commands/plan/),
[Pulumi Automation API](https://www.pulumi.com/docs/iac/concepts/automation-api/),
[Ansible control hosts](https://docs.ansible.com/projects/ansible/latest/installation_guide/intro_installation.html).

Implement in order: configure tool/host/workspace and probe real installed versions;
import/review project and pin dependency/provider versions; inject only approved
short-lived credentials into the bound host's private process environment; validate;
produce a bounded, sanitized plan/preview artifact plus immutable hash; show the
actual create/change/delete set and available cost estimates; approve that exact
plan; acquire per-stack state lock and fencing token; apply through a durable
companion job; reconcile actual resources; expose status/cancel/logs and a new
reviewed rollback/recovery plan. Cancellation does not undo completed changes.
Never pass secrets in CLI arguments, store unredacted state/plan in the Android
job journal, substitute a speculative old plan, autoexecute downloaded plugins,
or infer a successful deployment from SSH exit/status alone.

Separate settings: human tool permission, agent validate/preview, agent apply,
agent destructive actions, target account/project/region/resource allowlists,
plan expiry, owner approval policy, daily calls and enforceable currency budget.
Agent apply defaults off. The existing CLOUD read scope does not grant deployment
or arbitrary shell permission. Structured automation requires a new distinct
scope, service credentials and typed IAC_* job contracts, not general CLOUD_EXECUTE.
Ansible check mode is module-dependent; it is not universal rollback or a guarantee
of zero side effects. Native Android CLIs and remote cloud execution APIs can be
added only after their real runtime/credential/state boundaries are verified.
