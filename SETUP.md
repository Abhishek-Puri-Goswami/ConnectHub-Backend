# ConnectHub — Setup From Scratch

For someone cloning both repos (`connecthub-backend`, `connecthub-frontend`,
`local-run` branch) onto a brand-new machine with nothing installed yet.
This project runs entirely locally — no Docker, no cloud account, no CI/CD
— but it does need several third-party service credentials to run with
every feature working (email, SMS, payments, OAuth, file storage). Where a
credential is optional, that's called out so you can skip it and still get
the core chat features running.

**Currently Windows-only.** The startup scripts are PowerShell and call a
Kafka `.bat` launcher. On Mac/Linux you can still run everything — install
the same tools, use Kafka's `.sh` scripts instead of `.bat`, and either
write your own launcher script or start each `java -jar` manually with the
environment variables `.env` would otherwise provide.

## 1. Minimum versions

| Tool | Minimum | Verified with (this project) |
|---|---|---|
| JDK | 17 (pom.xml's declared target) | 21 |
| Maven | 3.9+ | 3.9.14 |
| Node.js | 18+ (Vite 5 requirement) | 25.8.2 |
| npm | bundled with Node | 11.12.1 |
| MySQL | 8.0+ | 8.0.44 |
| Redis | any recent version; Windows has no official build — use [redis-windows/redis-windows](https://github.com/redis-windows/redis-windows) | 8.10.0 |
| Kafka | 3.3+ (needs KRaft mode support) | 3.6.0 |
| Zipkin | any recent 2.x/3.x (optional) | 3.5.1 |
| Git | any recent version | — |

## 2. Clone both repos

```bash
git clone -b local-run https://github.com/Abhishek-Puri-Goswami/ConnectHub-Backend.git connecthub-backend
git clone -b local-run https://github.com/Abhishek-Puri-Goswami/ConnectHub-Frontend.git connecthub-frontend
```

## 3. Install the runtimes

- **JDK 17+**: https://adoptium.net (Temurin builds) or Oracle's site
- **Maven**: https://maven.apache.org/download.cgi — add `bin/` to `PATH`
- **Node.js 18+**: https://nodejs.org (npm comes bundled)
- **MySQL 8.0+ Community Server**: https://dev.mysql.com/downloads/mysql/
  — during install, set and remember a root password
- **Git**: https://git-scm.com/downloads

## 4. Install native infra (Redis, Kafka, and optionally Zipkin)

Full step-by-step in `Native_Redis_Kafka.md` and `Native_Zipkin.md` — the
short version:

- **Redis**: download from
  [redis-windows/redis-windows releases](https://github.com/redis-windows/redis-windows/releases),
  extract anywhere (this project uses `D:\redis`), run
  `redis-server.exe redis.conf` from inside that directory (must be a
  relative path — see `Native_Redis_Kafka.md` for why).
- **Kafka**: download from https://kafka.apache.org/downloads (any 3.3+
  release), extract (this project uses `D:\kafka`), then one-time format
  the storage:
  ```powershell
  cd D:\kafka
  $uuid = & .\bin\windows\kafka-storage.bat random-uuid
  & .\bin\windows\kafka-storage.bat format -t $uuid -c .\config\kraft\server.properties
  ```
  Edit `config\kraft\server.properties` so `advertised.listeners` reads
  `PLAINTEXT://localhost:9092` (not the default) — see
  `Native_Redis_Kafka.md` for the exact block to match.
- **Zipkin (optional — tracing only, nothing breaks without it)**: download
  the self-contained jar per `Native_Zipkin.md`, extract to `D:\zipkin`.

## 5. Edit `start-backend.ps1` for your machine

`start-backend.ps1` currently has the paths from the machine this project
was set up on hardcoded into it — your JDK install location and wherever
you put Redis/Kafka/Zipkin in step 4 almost certainly won't match. Open the
script and edit these 4 spots before running it (search the file for
`D:\` — every hit is one of these):

1. **JAVA_HOME**, near the top:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Java\latest\jdk-21"
   ```
   Change to wherever you installed the JDK, e.g.
   `C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot`.

2. **Redis** — the line starting `Start-Process "D:\redis\redis-server.exe"`:
   update both `D:\redis\redis-server.exe` and the
   `-WorkingDirectory "D:\redis"` right after it to wherever you extracted
   Redis. Both must point at the *same* folder — `redis-server.exe` needs
   its config file passed as a relative path from its own directory (see
   `Native_Redis_Kafka.md` for why; passing an absolute path here breaks
   it in a confusing way).

3. **Kafka** — the line starting
   `$kafkaWrapper = Start-Process "D:\kafka\bin\windows\kafka-server-start.bat"`:
   three references to update together —
   `D:\kafka\bin\windows\kafka-server-start.bat`,
   `D:\kafka\config\kraft\server.properties`, and
   `-WorkingDirectory "D:\kafka"`.

4. **Zipkin** — inside the `if ($startZipkin)` block: update
   `D:\zipkin\zipkin-server-exec.jar` and `-WorkingDirectory "D:\zipkin"`.
   If you skipped installing Zipkin in step 4, set `$startZipkin = $false`
   instead so the script doesn't try to launch a jar that isn't there.

`stop-backend.ps1` needs **no edits** — it only kills whatever PIDs
`start-backend.ps1` recorded to `backend-pids.csv`, so it works regardless
of where anything is actually installed.

One more thing that trips people up: both scripts assume you're running
them from the `connecthub-backend` repo root
(`cd connecthub-backend; .\start-backend.ps1`) — the service jar paths
(`service-registry/target/...`) and `backend-pids.csv` are relative to
wherever you launch the script *from*, not to the script's own location on
disk.

## 6. Create the MySQL databases

Using the root password you set in step 3:
```sql
CREATE DATABASE IF NOT EXISTS connecthub_auth;
CREATE DATABASE IF NOT EXISTS connecthub_room;
CREATE DATABASE IF NOT EXISTS connecthub_message;
CREATE DATABASE IF NOT EXISTS connecthub_media;
CREATE DATABASE IF NOT EXISTS connecthub_notification;
CREATE DATABASE IF NOT EXISTS connecthub_payment;

CREATE USER IF NOT EXISTS 'dbadmin'@'%' IDENTIFIED BY 'choose-a-password';
CREATE USER IF NOT EXISTS 'dbadmin'@'localhost' IDENTIFIED BY 'choose-a-password';

GRANT ALL PRIVILEGES ON connecthub_auth.*         TO 'dbadmin'@'%';
GRANT ALL PRIVILEGES ON connecthub_room.*         TO 'dbadmin'@'%';
GRANT ALL PRIVILEGES ON connecthub_message.*      TO 'dbadmin'@'%';
GRANT ALL PRIVILEGES ON connecthub_media.*        TO 'dbadmin'@'%';
GRANT ALL PRIVILEGES ON connecthub_notification.* TO 'dbadmin'@'%';
GRANT ALL PRIVILEGES ON connecthub_payment.*      TO 'dbadmin'@'%';
GRANT ALL PRIVILEGES ON connecthub_auth.*         TO 'dbadmin'@'localhost';
GRANT ALL PRIVILEGES ON connecthub_room.*         TO 'dbadmin'@'localhost';
GRANT ALL PRIVILEGES ON connecthub_message.*      TO 'dbadmin'@'localhost';
GRANT ALL PRIVILEGES ON connecthub_media.*        TO 'dbadmin'@'localhost';
GRANT ALL PRIVILEGES ON connecthub_notification.* TO 'dbadmin'@'localhost';
GRANT ALL PRIVILEGES ON connecthub_payment.*      TO 'dbadmin'@'localhost';
FLUSH PRIVILEGES;
```
You don't need to create tables — Flyway migrations run automatically the
first time each service starts.

## 7. Backend `.env` — obtaining every credential

```bash
cd connecthub-backend
cp .env.example .env
```
Then fill in each section:

**JWT_SECRET** — generate a random 512-bit secret:
```bash
openssl rand -base64 64
```
(No `openssl` on Windows? Use Git Bash, which ships with it, or PowerShell:
`[Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Max 256 }))`)

**MySQL** — use the `dbadmin` user/password you created in step 6, and the
root password from step 3 for `MYSQL_ROOT_PASSWORD`.

**EUREKA_PASSWORD / ADMIN_PASSWORD** — any password you choose; these
protect the Eureka dashboard and Spring Boot Admin respectively.

**PLATFORM_ADMIN_EMAIL / PASSWORD** — the account auto-created on
`auth-service`'s first boot. Choose your own. Note: it's seeded with role
`ADMIN`, not `PLATFORM_ADMIN` — see step 11 below to promote it.

**AWS S3** (media uploads — optional, media-service still starts without
it, uploads will just fail):
1. Create an AWS account at https://aws.amazon.com (free tier available)
2. S3 console → Create bucket → note the bucket name and region
3. IAM console → Users → Create user → Attach policy with
   `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` on that bucket →
   Security credentials tab → Create access key
4. Fill `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_S3_BUCKET`,
   `AWS_REGION`

**Gmail SMTP** (email OTP/notifications — needed for registration to fully
work, since registration OTP is emailed):
1. Use any Gmail account, enable 2-Step Verification if not already on:
   https://myaccount.google.com/security
2. Generate an app password: https://myaccount.google.com/apppasswords
   (requires 2-Step Verification to be enabled first)
3. `MAIL_USERNAME` = the Gmail address, `MAIL_PASSWORD` = the 16-character
   app password (not your regular Gmail password)

**Google OAuth** (optional — "Sign in with Google"):
1. https://console.cloud.google.com/apis/credentials
2. Create a project if you don't have one → Create Credentials → OAuth
   client ID → Application type: Web application
3. Authorized redirect URI: `http://localhost:8080/login/oauth2/code/google`
4. Fill `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`

**GitHub OAuth** (optional — "Sign in with GitHub"):
1. https://github.com/settings/developers → New OAuth App
2. Authorization callback URL: `http://localhost:8080/login/oauth2/code/github`
3. Fill `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`

**Twilio SMS** (phone OTP — optional, phone verification/login just won't
work without it):
1. Sign up at https://console.twilio.com (free trial includes credit)
2. Console dashboard shows your Account SID and Auth Token directly
3. Get a phone number: Phone Numbers → Buy a number (trial accounts get one
   free) → this is `TWILIO_FROM_NUMBER`
4. Note: trial accounts can only send SMS to phone numbers you've verified
   in the Twilio console first

**Razorpay** (payments/subscriptions — optional, the rest of the app works
without it, subscription upgrade just won't):
1. Sign up at https://dashboard.razorpay.com/signup
2. Stay in **Test Mode** (toggle top-right) for local dev — test mode can't
   move real money
3. Settings → API Keys → Generate Test Key → fill `RAZORPAY_KEY_ID`,
   `RAZORPAY_KEY_SECRET`
4. Subscriptions → Plans → Create Plan — create two plans (one for
   PREMIUM, one for PLATINUM pricing) → fill `RAZORPAY_PLAN_PREMIUM_ID`,
   `RAZORPAY_PLAN_PLATINUM_ID` with the `plan_...` IDs shown
5. `RAZORPAY_WEBHOOK_SECRET` — only needed if testing the webhook path;
   Settings → Webhooks → Add New Webhook, set any secret string there and
   here (must match)

**Firebase** (FCM push notifications — optional, leave blank to disable,
the service starts cleanly without it):
1. https://console.firebase.google.com → Create project
2. Project Settings → Service Accounts → Generate new private key
   (downloads a JSON file)
3. Base64-encode it: `base64 -w 0 firebase-service-account.json` (Git
   Bash) or PowerShell:
   `[Convert]::ToBase64String([IO.File]::ReadAllBytes("firebase-service-account.json"))`
4. Paste the result as `FIREBASE_SERVICE_ACCOUNT_JSON` (one long line)

**SONAR_TOKEN** — only needed if running local SonarQube
(`docker-compose.sonarqube.yml`) for code quality checks; skip otherwise.

**Config Server variables** (`CONFIG_SERVER_*`, `CONFIG_REPO_*`) — leave
commented out/blank. Not needed; see `Config_Server_Reuse.md`.

## 8. Build the backend

```bash
mvn clean install -DskipTests
```
This compiles all 12 modules and produces each service's `target/*.jar` —
required before the first run, since jars aren't committed to git.

## 9. Frontend `.env.local`

```bash
cd connecthub-frontend
npm install
cp .env.example .env.local
```
Defaults already point at `localhost:8080` (the local gateway) — no
changes needed there. Fill in:
- `VITE_RAZORPAY_KEY_ID` — same `RAZORPAY_KEY_ID` from backend step 7
- `VITE_RAZORPAY_PLAN_ID` — one of the plan IDs from backend step 7
- `VITE_FIREBASE_*` — same Firebase project as backend step 7, but from
  Firebase Console → Project Settings → General → Your apps → Web app
  (a different set of values than the service-account JSON — this is the
  public web config)
- `VITE_ENABLE_PAYMENTS` — `true`/`false`, whether to show the billing UI

## 10. Run everything

```powershell
cd connecthub-backend
.\start-backend.ps1
```
Starts Redis, Kafka, Zipkin (if installed), then all 11 services in order.
Takes a couple of minutes. Verify with `http://localhost:8761` (Eureka —
should show all services registered) or check each
`http://localhost:<port>/actuator/health` (ports listed in `CLAUDE.md`).

```bash
cd connecthub-frontend
npm run dev
```
Opens at `http://localhost:5173`.

`.\stop-backend.ps1` (from `connecthub-backend`) tears the backend down
cleanly, Redis/Kafka/Zipkin included.

## 11. First login — promoting the platform admin

Log in once with the `PLATFORM_ADMIN_EMAIL`/`PASSWORD` from your `.env` —
this account exists but only has role `ADMIN`. To get true `PLATFORM_ADMIN`
access (there's no self-service path for the very first one — see
`CLAUDE.md` for why), run directly against MySQL:
```sql
UPDATE connecthub_auth.users
SET role='PLATFORM_ADMIN', subscription_tier='PLATINUM'
WHERE email='your-platform-admin-email';
```
Log out and back in afterward — the JWT caches the role, so a fresh token
is needed to pick up the change.

## If something doesn't come up

- Check `logs/<service>-out.log` / `-err.log` in `connecthub-backend` —
  every service's stdout/stderr is redirected there.
- `Native_Redis_Kafka.md` and `Native_Zipkin.md` cover the specific
  Windows gotchas hit while setting this project's own instances up
  (Kafka file-locking crashes, Redis path mangling, etc.) — worth checking
  if either hits the same symptoms.
- `CLAUDE.md` has the full current state of this project's local setup,
  including what's deliberately disabled (Config Server, CI/CD) and why.
