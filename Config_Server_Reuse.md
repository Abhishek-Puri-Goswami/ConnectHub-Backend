# Config Server — Disabled, How to Reuse It Later

## Status: disabled, zero migration required

`config-server` is **not actually consumed by any other service in this
project.** Verified directly, not assumed:

- Only `config-server/pom.xml` depends on `spring-cloud-config-server`. None
  of the other 11 services depend on `spring-cloud-starter-config` (the
  *client* library needed to pull config from it) — checked every `pom.xml`.
- No service has a `bootstrap.yml` (the file Spring Cloud Config client reads
  before `application.yml`, where you'd normally point it at the config
  server).
- No `spring.config.import=configserver:...` property exists anywhere.
- `CONFIG_SERVER_HOST` / port `8888` are referenced only in
  `docker-compose.yml` (env vars nobody reads) and `config-server`'s own
  module — never in application code or config.

Every service has been running entirely off its own local
`application.yml` + environment variables the whole time. Config Server was
included in the original boot order under the assumption it was a hard
dependency (carried over from the Docker Compose `depends_on` graph), but
that assumption was never actually true for how the app is wired — it
registers with Eureka and clones its git-backed config repo on startup, and
then nothing downstream ever asks it for anything.

**Practical effect: you can stop starting `config-server` entirely, right
now, with no config values to migrate anywhere.** That's already done — see
`start-backend.ps1` and the boot order in `CLAUDE.md`.

## What still exists, untouched

- The `config-server` module itself (code, Dockerfile, pom.xml) — unchanged,
  still builds and runs standalone if you want to demo it.
- The external config repo it pulls from
  (`https://github.com/Abhishek-Puri-Goswami/ConnectHub-Config-Server.git`,
  containing `auth-service.yml`, `room-service.yml`, etc. — one file per
  service, checked out locally at `D:\ConnectHub\connecthub-config`) — still
  there, still valid, just not fetched by anything right now.
- `docker-compose.yml`'s `config-server` service definition and every other
  service's `CONFIG_SERVER_HOST`/`CONFIG_SERVER_USER`/`CONFIG_SERVER_PASSWORD`
  env vars — unchanged. They were always inert (nothing read them), so
  removing them isn't necessary either; they just don't do anything.

## How to actually wire a service to it, for a later demo

If you want to demonstrate the real Spring Cloud Config pattern later
(useful for an architecture walkthrough — "here's how centralized config
*would* work at multi-instance scale"), pick one service (e.g.
`auth-service`) and:

1. Add the client dependency to that service's `pom.xml`:
   ```xml
   <dependency>
     <groupId>org.springframework.cloud</groupId>
     <artifactId>spring-cloud-starter-config</artifactId>
   </dependency>
   ```
2. Create `auth-service/src/main/resources/bootstrap.yml`:
   ```yaml
   spring:
     application:
       name: auth-service
     cloud:
       config:
         uri: http://${CONFIG_SERVER_USER}:${CONFIG_SERVER_PASSWORD}@localhost:8888
         fail-fast: true
   ```
3. Start `config-server` first (it's still a fully working module — just
   `java -jar config-server/target/config-server-1.0.0.jar` after loading
   `.env`, same as before), then start `auth-service` — it will now pull
   `auth-service.yml` from the config repo on boot and merge it over its
   local `application.yml`.
4. To prove it's live: change a value in `auth-service.yml` in the config
   repo, push it, hit `auth-service`'s `/actuator/refresh` endpoint (needs
   `spring-boot-starter-actuator` refresh scope, already present), and show
   the new value took effect without a restart — that's the actual value
   proposition of Config Server (live config changes across a fleet without
   redeploying), which only matters once there's a fleet to reconfigure.

Until there's a real reason to reconfigure services live (multiple
instances, multiple environments), this stays disabled and every service
keeps reading its own `application.yml` + `.env` directly — simpler, one
fewer moving part, one fewer external GitHub dependency in the boot path
(this is also the exact thing that broke boot entirely earlier in this
project's history when its PAT expired).
