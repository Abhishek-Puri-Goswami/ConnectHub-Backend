# ConnectHub Backend — Local Run Context

## Aim (current effort)
Restore the full backend microservices stack to running order on this machine
(native, not Docker), then build Hoppscotch collections and debug each
service's API flows. Branch: `local-run`.

**Direction confirmed by the project owner (post architecture review):** this
is staying a single-developer, local-only, always-local deployment —
no CI/CD, no cloud deploy target, Docker being phased out entirely
(including Kafka/Redis, currently the only two things still in Docker — see
`Native_Redis_Kafka.md`). Every simplification below should be read in that
light: things aren't being cut because they're bad patterns in general, but
because they don't earn their cost at this project's actual scale
(1 developer, 3 demo accounts, one machine). See the full audit findings
from this session for the reasoning if that context is ever needed again.

## Why native instead of Docker for the app services
`.env` already points `MYSQL_HOST` / `REDIS_HOST` / `EUREKA_HOST` at
`localhost`, and untracked `start-backend.ps1` / `stop-backend.ps1` run each
service as a plain `java -jar`. MySQL runs as a native Windows service
(`MySQL80`). Kafka + Redis run in Docker (see below) since there's no native
install for them on this machine. The Dockerfiles/compose files were found
deleted-but-uncommitted at the start of this effort and were restored via
`git checkout` — they still exist and work, just aren't the active path.

## Infra
- **MySQL**: native Windows service `MySQL80`, always running independent of
  this project. 6 databases already provisioned: `connecthub_auth`,
  `connecthub_room`, `connecthub_message`, `connecthub_media`,
  `connecthub_notification`, `connecthub_payment`, plus app user `dbadmin`
  (see `.env` for creds). Re-run grants from `init-databases.sh` if a DB/user
  ever needs recreating (adapt for native `mysql.exe`, not the container
  script as-is).
- **Kafka + Redis**: Docker containers, brought up via:
  ```bash
  docker compose -f docker-compose.yml -f docker-compose.infra.yml up -d kafka redis
  ```
  `docker-compose.infra.yml` (new file, part of this effort) publishes
  `9092:9092` (kafka) and `6379:6379` (redis) and overrides Kafka's
  `KAFKA_ADVERTISED_LISTENERS` to `localhost:9092` — the base compose file
  only exposes Kafka inside the docker network and maps Redis to `6385`,
  neither of which native JVM processes on the host could reach.
- **Docker Desktop must be started manually** (`Start-Process "C:\Program
  Files\Docker\Docker\Docker Desktop.exe"`) — it does not auto-start, and this
  machine has had multiple unplanned restarts/hangs during this effort, so
  always verify `docker info` succeeds before assuming containers are up.

## Boot order
`service-registry` → `api-gateway` → `auth-service` → `room-service` →
`message-service` → `media-service` → `presence-service` →
`notification-service` → `websocket-service` → `payment-service` →
`admin-server`.

**`config-server` is intentionally NOT started** — verified that no other
service actually depends on it (no `spring-cloud-starter-config` anywhere
else, no `bootstrap.yml`, zero references to `CONFIG_SERVER_HOST`/`8888`
outside its own module and the inert `docker-compose.yml` env vars). It was
in the original boot order under an unverified assumption carried over from
Docker Compose's `depends_on` graph. See `Config_Server_Reuse.md` for the
full finding and how to bring it back for a demo later.

Each is started via `Start-Process java -Xmx300m -jar <module>/target/<module>-1.0.0.jar`
after loading `.env` into the process environment (same pattern as
`start-backend.ps1`), with stdout/stderr redirected to `logs/<module>-out.log`
/ `-err.log`. Verify each one with `curl http://localhost:<port>/actuator/health`
before starting the next — don't parallelize boot, `service-registry` is a
hard dependency for everything else's Eureka registration.

Ports: service-registry 8761, api-gateway 8080, auth 8081, room 8082,
message 8083, media 8084, presence 8085, notification 8086, websocket 8087,
payment 8088, admin-server 9090. (config-server 8888 exists but isn't part
of the normal boot — see above.)

## Known-good fixes already applied (in `.env`, not committed — gitignored)
- `CONFIG_REPO_PASSWORD` — old GitHub PAT had expired (401 from GitHub
  itself). Replaced with a new classic PAT with `repo` scope (fine-grained
  tokens need explicit per-repo access, which caused a confusing 404 the
  first time). **No longer load-bearing** now that config-server is disabled
  (see boot order above) — kept documented in case config-server is
  reactivated for a demo per `Config_Server_Reuse.md`.

## Resolved issues
- **`notification-service` mail health** — was DOWN (`535 5.7.8
  BadCredentials`) with two earlier Gmail accounts/app-passwords. Fixed:
  `MAIL_USERNAME=ap.goswami.2854@gmail.com` with a valid app password.
  Verified both via raw `curl smtps://smtp.gmail.com:465` AUTH PLAIN (`235
  Accepted`) and via the service's own `/actuator/health` (`mail: UP`).
  If mail breaks again, re-verify with the same raw curl check before
  touching app config — isolates Gmail-side rejection from app bugs.

## Environment stability — RAM matters
This machine has ~15.4GB RAM and hit 90% usage / hung once with all 9+
services running alongside Docker Desktop. **Don't run more services
simultaneously than the current task needs.** Each JVM idles around
250-600MB. Stop services with `taskkill //PID <pid> //F` (PIDs logged to
`logs/<module>.pid` and echoed when started) when done debugging one, rather
than leaving everything up. This machine has also had at least one full
force-shutdown during this effort — after any restart, assume Docker,
all Java processes, and Docker containers are gone and MySQL (native
service) is the only thing that survives; re-verify from scratch rather than
assuming prior state.

## Status: all 11 services restored and fully healthy
As of this pass, the full stack is up: config-server, service-registry,
api-gateway, auth-service, room-service, message-service, media-service,
presence-service, notification-service, websocket-service, payment-service,
admin-server (port 9090). All registered with Eureka, all green including
mail. Confirmed stable at ~1.7GB free RAM (~10.5%) — tight but not
degrading.

To bring the whole stack up from a cold state (Docker Desktop closed, no
java processes running — the normal state after this machine reboots or
hangs), repeat the boot order above. Services 8-11 (presence, websocket,
payment, admin-server) were started with `-Xmx300m` to control the RAM
budget under load — worth applying to all 11 on the next full restart, not
just the tail end, for a cleaner baseline.

## Done since the architecture review
- Hoppscotch collections built — 9 collections covering every service, see
  `hoppscotch/`.
- Frontend brought into the loop and verified end-to-end (real login through
  the actual running backend, not mocked).
- Subscription tier enforcement bug fixed (PREMIUM/PLATINUM were silently
  falling through to FREE limits) + the FREE/PRO limit model finalized
  (group rooms, members/room, storage, max file size — message rate is now
  a uniform anti-abuse limit, not tier-gated) + regression tests added.
- Config Server disabled (never actually consumed by anything — see
  `Config_Server_Reuse.md`).
- `deploy.yml` (EC2 deploy) and `sonarcloud.yml` deleted from both repos'
  `.github/workflows/` — confirmed no CI/CD or cloud deploy target going
  forward, everything runs locally. Both `.github/` directories removed
  entirely (nothing else was in them).

## Not yet done (in progress, P1 of the post-audit plan)
- Native Redis + Kafka instead of Docker — installs present at `D:\kafka`
  and `D:\redis`, see `Native_Redis_Kafka.md` for the setup steps once
  actually switched over.
- Stand up native Zipkin (steps in `Native_Redis_Kafka.md`'s sibling doc,
  once written) — tracing is already enabled by default in the native run
  with nowhere to send spans, which is why services spam
  `Connection refused: localhost:9411` in their logs today. Fall back to
  `MANAGEMENT_ZIPKIN_TRACING_ENABLED=false` in `.env` if RAM doesn't permit
  running it.
- Frontend marketing copy fix (conflates OAuth2 auth with encryption).
- **RAM usage reduction** — the `-Xmx300m` cap (now applied to all 11
  services via `start-backend.ps1`, not just the tail end) is a stopgap.
  Only run the services the current task actually needs; don't leave all 11
  up when not actively testing across services.
