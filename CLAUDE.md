# ConnectHub Backend — Local Run Context

## Aim (current effort)
Restore the full backend microservices stack to running order on this machine
(native, not Docker), then build Hoppscotch collections and debug each
service's API flows. Branch: `local-run`.

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

## Boot order (per original microservice build order)
`config-server` → `service-registry` → `api-gateway` → `auth-service` →
`room-service` → `message-service` → `media-service` → `presence-service` →
`notification-service` → `websocket-service` → `payment-service` →
`admin-server`.

Each is started via `Start-Process java -jar <module>/target/<module>-1.0.0.jar`
after loading `.env` into the process environment (same pattern as
`start-backend.ps1`), with stdout/stderr redirected to `logs/<module>-out.log`
/ `-err.log`. Verify each one with `curl http://localhost:<port>/actuator/health`
before starting the next — don't parallelize boot, config-server and
service-registry are hard dependencies for everything else.

Ports: config-server 8888, service-registry 8761, api-gateway 8080,
auth 8081, room 8082, message 8083, media 8084, presence 8085,
notification 8086, websocket 8087, payment 8088 (admin-server port TBD).

## Known-good fixes already applied (in `.env`, not committed — gitignored)
- `CONFIG_REPO_PASSWORD` — old GitHub PAT had expired (401 from GitHub
  itself). Replaced with a new classic PAT with `repo` scope (fine-grained
  tokens need explicit per-repo access, which caused a confusing 404 the
  first time). config-server clones
  `https://github.com/Abhishek-Puri-Goswami/ConnectHub-Config-Server.git` at
  startup — needs internet + this token to be valid.

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

## Not yet done
- Hoppscotch collections not yet built.
- Frontend (`connecthub-frontend`, separate working directory) not yet
  brought into the loop.
- **RAM usage reduction — deferred until debugging work is done.** The
  `-Xmx300m` cap is a stopgap, not a real fix. Revisit: trimming unused
  Spring Boot autoconfig, disabling Zipkin tracing export noise since no
  collector is running locally (`MANAGEMENT_ZIPKIN_TRACING_ENABLED` is set
  for Docker compose but not currently for native runs, so every service
  wastes cycles/log noise retrying `localhost:9411`), and deciding on a
  permanent per-service `-Xmx` baked into the start script rather than an
  ad-hoc flag. Until then: only run the services the current task actually
  needs, don't leave all 11 up when not actively testing across services.
