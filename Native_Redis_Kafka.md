# Running Redis & Kafka Natively (No Docker)

**Status: done and verified.** Kafka and Redis run natively now — Docker is
no longer needed for anything in this project (MySQL was already native).
All 11 backend services boot and report healthy against this setup exactly
as they did against the Docker containers, with **zero `.env` changes** —
every service already read `REDIS_HOST=localhost` and defaulted
`KAFKA_BROKERS` to `localhost:9092`, so a native instance on the same port is
indistinguishable to the app from the Dockerized one.

Installs already present at `D:\redis` (Redis 8.10.0, from the
[redis-windows/redis-windows](https://github.com/redis-windows/redis-windows)
project — `redis-server.exe` + `redis.conf`; still Cygwin-based under the
hood, which is why the path-mangling gotcha below exists) and `D:\kafka`
(Apache Kafka 3.6.0, KRaft mode — no ZooKeeper, matching how the old
`apache/kafka:3.7.0` Docker image was configured; the exact version differs
slightly but KRaft-mode config is compatible across the 3.x line).

## Starting them

**Redis:**
```powershell
cd D:\redis
redis-server.exe redis.conf --maxmemory 256mb --maxmemory-policy allkeys-lru
```
Gotcha hit while setting this up: this Redis build's `redis-server.exe` is a
Cygwin binary and mangles absolute Windows paths passed as the config-file
argument (`D:\redis\redis.conf` gets misread as `/redis/D:\redis\redis.conf`
→ "Fatal error, can't open config file"). Always run it with a **relative**
path (`redis.conf`) from inside `D:\redis` as the working directory, as
above — that's why `start-backend.ps1`-style wrapping needs
`-WorkingDirectory "D:\redis"` explicitly if backgrounding it via
`Start-Process`.

Verify: `redis-cli.exe -h localhost -p 6379 ping` → `PONG`.

**Kafka:**
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\latest\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:KAFKA_HEAP_OPTS = "-Xmx256m -Xms128m"
cd D:\kafka
.\bin\windows\kafka-server-start.bat .\config\kraft\server.properties
```
`config\kraft\server.properties` is already configured correctly (verified,
not assumed):
```properties
process.roles=broker,controller
node.id=1
listeners=PLAINTEXT://:9092,CONTROLLER://:9093
advertised.listeners=PLAINTEXT://localhost:9092
controller.listener.names=CONTROLLER
controller.quorum.voters=1@localhost:9093
```
Storage was already formatted (`D:\kafka\data\kraft-combined-logs` existed)
— if it ever needs redoing from scratch:
```powershell
$uuid = & .\bin\windows\kafka-storage.bat random-uuid
& .\bin\windows\kafka-storage.bat format -t $uuid -c .\config\kraft\server.properties
```

Verify: `.\bin\windows\kafka-broker-api-versions.bat --bootstrap-server localhost:9092`
should print a real API-versions list, not a connection error.

### Gotcha: Kafka crashes on Windows when it cleans up log files (FIXED)

Symptom: the broker runs for a while (or dies right after a restart) with
```
java.nio.file.FileSystemException: ...timeindex -> ...timeindex.deleted:
The process cannot access the file because it is being used by another process
ERROR Shutdown broker because all log dirs in D:\kafka\data\kraft-combined-logs have failed
```
Root cause (Kafka-on-Windows limitation, KAFKA-1194): Kafka renames/deletes
segment files while they are memory-mapped, which Windows refuses. **Two**
background jobs trigger it, and both must be off:

1. **Retention deletion** (old segments past `log.retention.*`).
2. **Log-cleaner compaction** of `__consumer_offsets` (the cleaner rewrites
   and swaps segment files). Disabling only retention is *not* enough — this
   was reproduced with 30,000 keyed records; the broker still died.

**Fix (applied to `D:\kafka\config\kraft\server.properties`; also required
on any new machine — see SETUP.md):**
```properties
log.retention.ms=-1
log.retention.bytes=-1
log.cleaner.enable=false
```
Trade-off: logs are never trimmed. Irrelevant for a local dev broker with a
handful of demo accounts; if disk usage ever matters, stop Kafka and wipe
`D:\kafka\data\kraft-combined-logs`, then re-format storage (commands above).
Verified by repeated `stop-backend.ps1` / `start-backend.ps1` cycles.

### Start/stop details worth knowing
- `kafka-server-start.bat` is a wrapper: `Start-Process` records the *cmd*
  PID, not java's. `start-backend.ps1` polls up to 45s for the real
  `kafka.Kafka` java child and records that PID (the child appears late — a
  race that used to leave Kafka orphaned).
- `stop-backend.ps1` stops services with a real Ctrl+C (so Spring shuts down
  gracefully), then force-kills only leftovers, and also kills any stray
  `kafka.Kafka` java process.

## Rolling back to Docker

`docker-compose.infra.yml` still exists and still works if ever needed:
```bash
docker compose -f docker-compose.yml -f docker-compose.infra.yml up -d kafka redis
```
Just stop the native `redis-server.exe` / Kafka processes first (same port,
can't run both at once).
