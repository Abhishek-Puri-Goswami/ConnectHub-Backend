# Running Zipkin Natively

**Status: done and verified.** Distributed tracing works end-to-end across
the stack, with real span data (timing, HTTP method/URI/status, service
name) confirmed via Zipkin's own API.

## Why this needed no service-side changes at all

Every service already has tracing enabled by default in the native run —
`management.zipkin.tracing.endpoint` defaults to `${ZIPKIN_URL:http://zipkin:9411}/api/v2/spans`
with `tracing.sampling.probability: 1.0` (see e.g.
`message-service/src/main/resources/application.yml`). This is exactly why
services were spamming `Connection refused: localhost:9411` in their logs
before this — they were already trying to export spans to a collector that
didn't exist. Standing up Zipkin didn't require touching any service's
config; it just gave the exporter somewhere to actually send to. Confirmed
live: within seconds of starting Zipkin, `GET /api/v2/services` on it
listed 8 services that had already been silently retrying against it.

## Install

No installer — Zipkin ships as a single self-contained executable Spring
Boot jar. Downloaded from Maven Central directly (the old
`search.maven.org/remote_content?...LATEST` shortcut URL is broken/returns
a 500 now — resolve the actual latest version via the search API first):
```bash
curl -s "https://search.maven.org/solrsearch/select?q=g:io.zipkin+AND+a:zipkin-server&core=gav&rows=1&wt=json"
# read the "v" field, e.g. 3.5.1, then:
curl -sL -o zipkin-server-exec.jar \
  "https://repo1.maven.org/maven2/io/zipkin/zipkin-server/3.5.1/zipkin-server-3.5.1-exec.jar"
```
Installed at `D:\zipkin\zipkin-server-exec.jar` (~131MB).

## Running it

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\latest\jdk-21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd D:\zipkin
java -Xmx300m -jar zipkin-server-exec.jar
```
Default port `9411`, matching `.env`'s `ZIPKIN_URL`. Verify:
`curl http://localhost:9411/health` → `{"status":"UP",...}`.

Wired into `start-backend.ps1` — starts automatically with everything else,
right after Kafka, controlled by a `$startZipkin` toggle at the top of that
step in case RAM gets tight later (see below).

## Verifying traces are real (not just "server is up")

```bash
curl http://localhost:9411/api/v2/services
# ["api-gateway","auth-service","media-service","message-service", ...]

curl "http://localhost:9411/api/v2/traces?serviceName=api-gateway&limit=1"
# real span JSON: traceId, timing, http.url, method, status, etc.
```
Or browse `http://localhost:9411/zipkin/` for the UI.

## RAM cost and the fallback

Measured directly on this machine: starting Zipkin (with `-Xmx300m`, same
cap as every other service) dropped free RAM from ~4.3GB to ~2.5GB with the
full 11-service stack + Kafka + Redis already running — a real cost, but
within the range this machine has run stable at before (it was stable down
to ~1.7GB free during earlier work in this project). If RAM pressure
becomes a problem again: set `$startZipkin = $false` at the top of the
Zipkin block in `start-backend.ps1`, or just don't start it manually — every
service already degrades gracefully without it (the "Connection refused"
log line is harmless noise, not a functional failure). No `.env` or service
code needs to change either way.
