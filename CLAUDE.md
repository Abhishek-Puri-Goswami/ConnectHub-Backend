# ConnectHub Backend — Local Run Context

## Direction
Single-developer, local-only, always-local deployment. No CI/CD, no cloud
deploy target, no Docker (MySQL, Redis, and Kafka are all native now —
Docker Desktop isn't needed for anything in this project anymore). Every
simplification in this file should be read in that light: things weren't
cut because they're bad patterns in general, but because they don't earn
their cost at this project's actual scale (1 developer, 3 demo accounts,
one machine). Branch: `local-run`.

## Infra — all native, zero Docker
- **MySQL**: native Windows service `MySQL80`, always running independent
  of this project. 6 databases: `connecthub_auth`, `connecthub_room`,
  `connecthub_message`, `connecthub_media`, `connecthub_notification`,
  `connecthub_payment`, plus app user `dbadmin` (see `.env` for creds).
  Re-run grants from `init-databases.sh` if a DB/user ever needs recreating
  (adapt for native `mysql.exe`, not the container script as-is).
- **Redis**: native, `D:\redis\redis-server.exe`. Started automatically by
  `start-backend.ps1`. See `Native_Redis_Kafka.md` for the Cygwin
  path-mangling gotcha if starting it manually.
- **Kafka**: native, `D:\kafka` (KRaft mode, single node). Started
  automatically by `start-backend.ps1`, which also correctly captures the
  real `java.exe` child PID (not the `.bat` wrapper's PID) so
  `stop-backend.ps1` doesn't orphan it. See `Native_Redis_Kafka.md` for the
  Windows file-locking crash this hit once and how it was fixed (wipe +
  reformat the log dir — it's disposable local data).
- `docker-compose.yml` / `docker-compose.infra.yml` still exist and still
  work as a fallback if ever needed, but aren't the active path.
- **Zipkin**: native, `D:\zipkin\zipkin-server-exec.jar`. Every service
  already tries to export spans to it by default (that's why they used to
  log `Connection refused: localhost:9411` noise) — starting it required
  zero service-side changes. See `Native_Zipkin.md` for the RAM cost
  measured on this machine and the `$startZipkin` toggle in
  `start-backend.ps1` if that ever needs disabling again.

## Boot order
Redis, Kafka, and Zipkin start first, then: `service-registry` →
`api-gateway` → `auth-service` → `room-service` → `message-service` →
`media-service` → `presence-service` → `notification-service` →
`websocket-service` → `payment-service` → `admin-server`.

**`config-server` is not part of the boot at all** — verified that no other
service actually depends on it (no `spring-cloud-starter-config` anywhere
else, no `bootstrap.yml`, zero references to `CONFIG_SERVER_HOST`/`8888`
outside its own module). It was originally assumed to be a hard dependency,
carried over unverified from Docker Compose's `depends_on` graph. See
`Config_Server_Reuse.md` for the full finding and how to reactivate it for
a demo later if ever wanted.

**One command does the whole thing**: `.\start-backend.ps1` (loads `.env`,
starts Redis, Kafka, then all 11 services in order, each with `-Xmx300m`).
`.\stop-backend.ps1` tears it all down cleanly, Redis/Kafka included.
Verified via a full clean-slate stop/start cycle — all 11 report healthy at
`http://localhost:<port>/actuator/health`.

Ports: service-registry 8761, api-gateway 8080, auth 8081, room 8082,
message 8083, media 8084, presence 8085, notification 8086, websocket 8087,
payment 8088, admin-server 9090, Redis 6379, Kafka 9092, Zipkin 9411.
(config-server 8888 exists but isn't part of the normal boot.)

## Known-good fixes already applied (in `.env`, not committed — gitignored)
- `CONFIG_REPO_PASSWORD` — old GitHub PAT had expired. **No longer
  load-bearing** now that config-server is disabled — kept documented in
  case config-server is reactivated per `Config_Server_Reuse.md`.
- `MAIL_USERNAME=ap.goswami.2854@gmail.com` with a valid Gmail app
  password — `notification-service` mail health was DOWN twice before this
  with expired/wrong credentials. If mail breaks again, verify with a raw
  `curl smtps://smtp.gmail.com:465` AUTH PLAIN check before touching app
  config — isolates Gmail-side rejection from app bugs.

## Environment stability — RAM matters
This machine has ~15.4GB RAM and has hung once running the full stack
alongside Docker Desktop (before the Docker removal above). Every service
now starts with `-Xmx300m` via `start-backend.ps1`. Still: **don't run more
services than the current task needs** — stop what you're not using rather
than leaving all 11 up indefinitely. This machine has had at least one full
force-shutdown historically — after any restart, assume nothing is running
except the native MySQL Windows service, and re-verify from scratch rather
than assuming prior state.

## Product decisions from the architecture review (this session)

A full PM/architect/security audit was run against this project and its
findings drove most of the simplification work below. Key decisions, so
they don't get re-litigated or accidentally reverted later:

- **Subscription tiers are FREE vs PRO (paid)**, not three separate tiers
  with different limits — `PREMIUM` and `PLATINUM` both map to the same
  "paid" limit bucket in `message-service`/`media-service`/`room-service`.
  The account-level role system still distinguishes `PREMIUM`/`PLATINUM`
  (that's a billing/display detail), but the actual enforced limits only
  have two tiers.
- **Finalized limit model** (implemented, tested): group rooms 5/500,
  members per room 25/250, storage 100MB/10GB, max file size 10MB/250MB.
  **Message rate limiting is deliberately NOT tier-gated** — it's a
  uniform 60/min anti-abuse limit for everyone. Gating core conversational
  throughput behind a paywall makes FREE feel broken rather than limited,
  which is the wrong trade for a chat product.
- **Deferred to "Phase 2 — implement only if there's real demand"**:
  message history retention windows, advanced search, scheduled messages.
  These aren't limit tweaks — each needs real subsystem design (retention
  policy decisions, search infrastructure, a delivery scheduler) that isn't
  justified without evidence anyone wants them yet.
- **Razorpay integration: paused, not abandoned.** The payment/subscription
  flow (`payment-service`, Razorpay order/verify/cancel, webhook handling)
  is functionally complete and was the thing the tier-enforcement bug
  above was found in. No further engineering investment here until there's
  a real monetization need — it's real payment-gateway surface area and
  worth minimizing until it's actually gating real revenue.
- **Five login methods** (password, email OTP, phone OTP, Google OAuth,
  GitHub OAuth) are intentional — kept for demonstration purposes, not a
  gap to trim.
- **`presence-service` stays a separate service** — room for future scope
  beyond its current small footprint, not folded into `websocket-service`.
- **Admin analytics dashboard stays as-is**, no further investment right
  now — turns out to already be more complete than first assessed (every
  field it collects is already charted). See
  `Admin_Analytics_Expansion_Plan.md` for a tiered reference plan (what
  reuses existing data vs. what needs real new subsystems) if this ever
  gets picked up.
- **Local SonarQube stays** (`docker-compose.sonarqube.yml`) as the ongoing
  quality-gate tool; SonarCloud CI was removed along with all other CI/CD.

## Done since the architecture review
- Hoppscotch collections built — 9 collections covering every service, see
  `hoppscotch/`.
- Frontend brought into the loop and verified end-to-end (real login
  through the actual running backend, not mocked).
- Subscription tier enforcement bug fixed + FREE/PRO limit model finalized
  + regression tests added (see Product decisions above).
- Config Server disabled — see `Config_Server_Reuse.md`.
- `deploy.yml` (EC2 deploy) and `sonarcloud.yml` deleted from both repos'
  `.github/workflows/`; both `.github/` directories removed entirely.
- Kafka and Redis switched from Docker to native — see `Native_Redis_Kafka.md`.
- Zipkin stood up natively — see `Native_Zipkin.md`. Verified real traces
  flowing (not just "server is up") via Zipkin's own API; 8 services were
  already trying to export spans and started succeeding immediately, no
  service-side changes needed. Wired into `start-backend.ps1`.
- Frontend marketing copy fixed (was conflating OAuth2 authentication with
  encryption — now correctly attributes both claims separately).
- 3 real demo accounts seeded through actual API flows (not hand-inserted)
  — see `DETAILS.md` (gitignored, contains real credentials).

## Not yet done
- **RAM usage reduction beyond the `-Xmx300m` stopgap** — still not a real
  fix, just a bound. Currently stable at ~2.5-3.2GB free with everything
  running (11 services + Redis + Kafka + Zipkin). Revisit trimming unused
  Spring Boot autoconfig if RAM pressure becomes a recurring problem again.
