# Running Redis & Kafka Natively (No Docker)

This is the follow-up to the RAM discussion in `CLAUDE.md`: right now Kafka and
Redis run in Docker (`docker-compose.infra.yml`), which means Docker Desktop
itself (plus its WSL2 VM) sits in memory just to host two small services. This
guide replaces that with native installs on Windows — same ports, so **no
application config changes are needed**: every service already reads
`REDIS_HOST=localhost` (`.env`) and defaults `KAFKA_BROKERS` to
`localhost:9092` (see e.g. `auth-service/src/main/resources/application.yml`).

## Why this is a clean swap

Neither service is hardcoded to Docker's internal networking for the native
run path — that's only how `docker-compose.yml`'s *undecorated* form works
(`KAFKA_ADVERTISED_LISTENERS: kafka:9092`, which is exactly what
`docker-compose.infra.yml` already overrides to `localhost:9092` for us). A
native Kafka/Redis on `localhost` is indistinguishable to every Java service
from the Dockerized one on the same port. Stop one, start the other — nothing
else changes.

## Redis

Windows has no official native Redis build anymore. Pick one:

### Option A — Memurai (recommended, easiest)

Memurai is a Redis-compatible server built for Windows, installs as a native
Windows service (starts automatically, no manual launch needed each session).

1. Download the free "Memurai for Developers" edition:
   https://www.memurai.com/get-memurai
2. Run the installer — it registers a Windows service listening on `6379` by
   default (same port the app already expects).
3. Verify:
   ```powershell
   redis-cli -h localhost -p 6379 ping
   # PONG
   ```
   (Memurai ships its own `redis-cli.exe`, or use any Redis CLI/GUI client.)
4. Since it's a Windows service, it survives reboots on its own — one less
   thing to remember to start, unlike the Docker container.

### Option B — WSL2 + native Linux Redis

If you already have WSL2 set up (Docker Desktop uses it under the hood
anyway, so this doesn't save much unless you drop Docker Desktop entirely):

```bash
# inside your WSL2 distro
sudo apt update && sudo apt install -y redis-server
sudo sed -i 's/^bind 127.0.0.1.*/bind 0.0.0.0/' /etc/redis/redis.conf
sudo service redis-server start
```

WSL2 forwards `localhost:6379` from Windows to the distro automatically on
recent WSL builds — verify with `redis-cli -h localhost -p 6379 ping` from
PowerShell. If it doesn't forward, add `netsh interface portproxy` rules or
just use Option A instead — it's simpler for this project's purposes.

### Matching the Docker container's settings

The Docker version runs with `--maxmemory 256mb --maxmemory-policy
allkeys-lru` (see `docker-compose.yml`). To match on Memurai, edit
`memurai.conf` (installed under `C:\Program Files\Memurai`) and set:
```
maxmemory 256mb
maxmemory-policy allkeys-lru
```
Not required for correctness — only relevant if you want the same eviction
behavior under memory pressure.

## Kafka

Kafka needs a JVM — you already have JDK 21 installed for the app services,
so no extra Java install needed. This project's Kafka runs in **KRaft mode**
(no ZooKeeper — see `apache/kafka:3.7.0` image with `KAFKA_PROCESS_ROLES:
"broker,controller"` in `docker-compose.yml`), so the native setup mirrors
that directly.

1. Download Kafka (matching version 3.7.x used in compose):
   https://downloads.apache.org/kafka/3.7.2/kafka_2.13-3.7.2.tgz
   Extract it somewhere stable, e.g. `D:\kafka`.

2. Set Java for the Kafka scripts (PowerShell):
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Java\latest\jdk-21"
   $env:Path = "$env:JAVA_HOME\bin;$env:Path"
   cd D:\kafka
   ```

3. Generate a cluster ID and format the storage directory (one-time setup):
   ```powershell
   $uuid = & .\bin\windows\kafka-storage.bat random-uuid
   & .\bin\windows\kafka-storage.bat format -t $uuid -c .\config\kraft\server.properties
   ```

4. Edit `config\kraft\server.properties` to match what the app expects
   (single-node, plaintext, advertised on localhost — same intent as
   `docker-compose.infra.yml`'s `KAFKA_ADVERTISED_LISTENERS` override):
   ```properties
   listeners=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
   advertised.listeners=PLAINTEXT://localhost:9092
   controller.listener.names=CONTROLLER
   controller.quorum.voters=1@localhost:9093
   ```

5. Start the broker (keep this window open, or wrap it the same way
   `start-backend.ps1` backgrounds the Java services):
   ```powershell
   & .\bin\windows\kafka-server-start.bat .\config\kraft\server.properties
   ```

6. Verify it's up:
   ```powershell
   & .\bin\windows\kafka-broker-api-versions.bat --bootstrap-server localhost:9092
   ```
   A list of API versions (not a connection error) means it's ready. This is
   the same check the Docker container's healthcheck uses internally.

7. Memory: the Docker container caps Kafka at `KAFKA_HEAP_OPTS: "-Xmx256m
   -Xms128m"`. Match that for the native process by setting before step 5:
   ```powershell
   $env:KAFKA_HEAP_OPTS = "-Xmx256m -Xms128m"
   ```

## Switching over

1. Stop the Docker containers (don't need to remove them, just stop):
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.infra.yml stop kafka redis
   ```
2. Start native Redis (Memurai service should already be running) and native
   Kafka (step 5 above).
3. Restart any currently-running backend services that had already opened
   connections to the old (Docker) instances — Redis/Kafka clients don't
   always reconnect cleanly across a full backend swap, so a clean restart
   of the Java services avoids stale connection issues.
4. No `.env` changes needed — verified by design above.

## Rolling back to Docker

Just reverse it: stop the native processes (stop the Memurai service via
`services.msc` or `Stop-Service Memurai`; close the Kafka window or `Ctrl+C`
it), then bring the containers back with the command from `CLAUDE.md`:
```bash
docker compose -f docker-compose.yml -f docker-compose.infra.yml up -d kafka redis
```
