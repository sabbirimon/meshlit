# Authenticated web/API companion and local groups

Modern Devices → Web/API devices and groups. Off by default. Enable the TLS
listener on port 18792; a foreground notification offers Stop access. Check the
phone's certificate SHA-256 before approving its self-signed certificate on the
other device. No unrestricted HTTP control endpoint is added by this feature.

Create a 15-minute invitation, open https://PHONE_IP:18792/ and enter the invitation
and a device name/category. The browser creates a random client credential,
sends an enrollment request, and keeps the credential in memory. Copy it privately
before closing the tab; there is no browser localStorage token persistence.
The directory persists only a SHA-256 credential hash in encrypted storage.
The owner then approves individual scopes on the phone. Pending/revoked identities
cannot access the API. Invitations rotate explicitly and expire; enrollment is
bounded to ten attempts per minute and 64 directory identities.

Categories include Android, computer, server, NAS, router, switch, firewall,
browser and other. Requested roles are descriptive claims. These records are
control clients, not attested compute or durable-storage providers.
Native model worker pairing remains separate on port 50551.

| Method | Route | Contract |
|---|---|---|
| GET | / | static companion UI, no credentials or private state embedded |
| POST | /api/v1/enroll | X-Meshlit-Invitation, id/name/kind/token/roles, pending owner approval |
| GET | /api/v1/status | Bearer approved client token, own identity/access status |
| POST | /api/v1/commands | AgentCommand JSON, stable per-client requestId ≤50 characters |
| GET | /api/v1/jobs | own admitted job history only |
| POST | /api/v1/cancel | own full namespaced requestId only |

Bodies are JSON with Content-Length, ≤64 KiB. Four handlers maximum, five-second
transport deadline; long operations run as durable background command jobs. No
cross-origin browser calls/CORS grant. Authorization and private body data are not
logged. Device scopes are checked at submission and again before execution;
saved app delegation gates mutations/private task/workspace reads as well.

Server prefixes client request IDs with a hash-derived device namespace; clients
receive the full ID for polling/cancel. Reusing ID+same parameters is idempotent.
Another client cannot enumerate or cancel its jobs. Credentials are revocable:
future requests fail and its queued/running jobs are cancelled; completed actions
are not rolled back. Stopping the listener cancels remote jobs and clears the
invitation. Listener activation is not restored silently after process death.

Device groups store local names and selected approved members. They do not start a
native pipeline, negotiate roles, replicate task memory or implement consensus.
Revocation removes group membership. Native worker enrollment has separate verified
runtime offers and explicit compute consent.

Host agents can use standard HTTPS JSON clients after enrolling the same way.
Pin/trust the verified certificate, rather than disabling certificate checks.
Network/TLS/browser interoperability must be tested on real phones; source and
loopback transport tests alone are not physical LAN evidence.
