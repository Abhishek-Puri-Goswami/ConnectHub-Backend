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

**One command does the whole thing**: `.\start-backend.ps1` (rebuilds jars if source is
newer, loads `.env`, starts Redis, Kafka, then all 11 services in order, each with `-Xmx300m`,
waits on health, fails fast). Machine paths live in its config block at the top.
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

## Full-audit remediation plan (22 findings) — status
Order agreed after the audit. Commit each phase separately; final step is a
full regression/security retest (two-account attack scenarios; convert the
scratch e2e scripts into a committed regression suite).

- **Phase 0 — DONE**: #1 WebSocket URL (`VITE_WS_URL` must be `http(s)://`;
  `toSockJsUrl` also converts `ws://`); minimal #5 start script (build-if-stale,
  health gates, fail-fast incl. Kafka, graceful Ctrl+C stop); #6 Kafka-on-Windows
  crash — root cause was retention deletion **and** log-cleaner compaction of
  `__consumer_offsets`; fixed with `log.retention.ms=-1`, `log.retention.bytes=-1`,
  `log.cleaner.enable=false` (see `Native_Redis_Kafka.md`).
- **#3 token invalidation — DONE**: password reset / revoke-all / self-delete /
  suspend used to lock the user out of the API for the whole invalidation TTL
  (gateway only checked key *existence*). Now a token is rejected only if its
  `iat` is older than the stored invalidation time (`TokenInvalidation` helper,
  duplicated in api-gateway and auth-service because the gateway has no common-lib).
  Same-second tokens are accepted (JWT `iat` is 1s resolution). The refresh
  endpoint was hardened in the same change (must be `type=refresh`, user active
  and not suspended, not invalidated) — otherwise the fix would have left refresh
  tokens usable after a reset. Verified live with a disposable user.
- **#4 logout — DONE**: logout used to blacklist the whole token string while the
  gateway checks `token:blacklist:<jti>`, so logout revoked nothing. It now
  blacklists the `jti` for the token's remaining lifetime (other sessions unaffected;
  `/auth/validate` checks it too). Known remaining gap: the refresh token is not
  revoked by logout (refresh tokens carry no jti) — decide with #2.
- **#2 authorization / tenant isolation — DONE**: any authenticated user could read/post
  into/rename/delete other users' rooms, forge notifications, force users offline, and
  subscribe to or inject into private rooms over WebSocket. Now enforced per service
  (caller = gateway-injected `X-User-Id`/`X-User-Role`):
  - room-service `RoomAccess`: member for read/members/pin; room ADMIN for update/add/kick/roles;
    only the creator can delete, grant ADMIN, or manage invite codes; the creator can't be
    demoted/kicked; own-only for `read`/`byUser`/mute; `GET /rooms` is platform-admin only;
    invite codes are returned to the creator only.
  - message-service: membership (via room-service) for send/history/search/unread/reactions,
    room ADMIN for clear; sender id is forced from the header; page size capped at 100.
  - notification-service: own notifications only; creating notifications is internal-only.
  - presence-service: writes only for self; aggregate reads internal/admin only.
  - websocket-service: `RoomAccessInterceptor` guards SUBSCRIBE (room topics = members only, other
    users' queues denied, unknown topics denied, frames without a principal rejected); every chat
    handler checks membership and edit/delete relays also check authorship; CONNECT now rejects
    revoked tokens (jti blacklist / suspended / invalidated) like the gateway.
  - Internal calls are identified by `X-Internal-Service` (set by each service's Feign
    interceptor); the gateway now **strips that header** from external requests. It is a marker,
    not a secret: anything reaching a service port directly can still set it (that is #9).
  - Platform admins get room-list/delete/kick and stats endpoints, but NOT room content.
  - Live-verified with a 3-account attack script (78 checks). Known behaviour: membership checks
    fail closed, so for the first ~minute after boot (before room-service is reachable from
    websocket/message-service) chat can be rejected.
- **Phase 1 remaining**: refresh-token revocation on logout (see #4 note).
- **#8 upload size limits — DONE**: media-service's multipart cap was 2MB, which defeated the
  10MB/250MB tier limits. The multipart ceiling is now 260MB and `UploadSizeGuardFilter` returns 413
  from the declared Content-Length per plan (FREE 10MB, paid 250MB, avatars 2MB) *before* the body is
  spooled, so a FREE user can't push 250MB through. Oversize/quota errors are 413, empty file / bad
  type are 400 (were 500). Live-verified; PRO-size uploads are unit-tested only (need storage, see #7).
- **#7 media storage = local disk — DONE**: S3 (and its dead credentials) is gone; media-service uses a
  `StorageProvider` interface with a `LocalDiskStorageProvider` (root `~/connecthub-media` or
  `MEDIA_STORAGE_DIR`; keys that resolve outside the root are refused). Stored URLs are stable
  (`http://localhost:8080/api/v1/media/file/<id>[/thumb]`, from `MEDIA_PUBLIC_BASE_URL`) because they live
  inside messages/avatars forever. **Deviation from the "short-lived signed URL" plan:** an expiring URL
  can't be stored, so instead `POST /media/session` (normal auth) sets a 30-minute HttpOnly, SameSite=Lax
  cookie (`ch_media`, HMAC-signed with a media-specific domain prefix, so a login JWT is not accepted and
  vice versa) scoped to `/api/v1/media/file`; the frontend renews it every 20 min (`services/mediaSession.js`,
  started from ChatLayout). Serving requires that cookie + room membership (avatars: any signed-in user),
  is dead after suspension/password reset/revoke-all, supports Range (video), and sends nosniff + sandbox CSP;
  documents are attachments. The gateway lets `/api/v1/media/file/` through unauthenticated (media-service
  does its own check). Cookie relies on localhost being same-site across ports — revisit if ever hosted.
  Also fixed: thumbnails were labelled JPEG but written in the source format. Old S3-era media rows have
  dead URLs (their bytes never existed locally). Live-verified (upload, serve, 401/403 cases, Range, reset).
- **#13 HTTP status handling — DONE**: client mistakes (missing header/param/part, malformed JSON, wrong type,
  unknown path, wrong method, wrong media type) used to fall into catch-all handlers and return 500 (or 400 for
  any RuntimeException in message-service). New `common-lib` `CommonWebExceptionHandler` (built on Spring's
  `ResponseEntityExceptionHandler`) maps them to 400/404/405/406/413/415 (missing `X-User-Id` = 401) with one body
  `{success,status,error,message}`; every service advice (auth, room, message, media, presence, notification,
  payment, new one in websocket) extends it — do NOT re-declare handlers for MethodArgumentNotValid /
  NoResourceFound / MaxUploadSizeExceeded in a service (ambiguous mapping). Unexpected errors are now a generic
  500 that never echoes exception text (media used to leak SDK messages). Domain fixes: message edit/delete by
  a non-author = 403, unknown message = 404, duplicate reaction = 409 (`ConflictException`), account-delete
  wrong password = 403 / missing = 400. Empty-body login stays 401 on purpose. Live-verified (44 cases).
- **Phase 2 done.**
- **#9 internal service exposure — DONE**: verified from a non-loopback address that every service port was
  reachable unauthenticated: spoofed `X-User-*` headers listed all users (incl. password hashes), `POST /actuator/loggers`
  changed log levels, metrics/env were readable, and the Eureka registry accepted anonymous requests (a rogue
  "auth-service" registration would have received real logins). Now: (1) every service and the gateway bind to
  `127.0.0.1` (`BIND_ADDRESS`; Eureka instances register `EUREKA_INSTANCE_IP`=127.0.0.1; Kafka listeners too, see
  SETUP.md); (2) the gateway sends `X-Internal-Auth` (= `INTERNAL_SERVICE_SECRET`, auto-generated into `.env` by
  start-backend.ps1) on every routed request and strips any client-supplied one; Feign clients send it too;
  (3) common-lib `InternalAuthFilter` hides all identity/internal headers (`X-User-*`, `X-Internal-*`) from any request
  without the valid secret, so `X-Internal-Service` is now trustworthy; (4) sensitive actuator endpoints need the
  secret (admin-server sends it via `HttpHeadersProvider`), health is status-only, gateway exposes health/info only;
  (5) auth-service no longer `permitAll`s everything: public paths mirror the gateway list, `/admin/**` needs an admin
  role header, the rest needs `X-User-Id`; (6) Eureka requires basic auth. Services refuse to start without the
  secret. Live-verified. NOT covered: MySQL (native, all interfaces — bind it to localhost in my.ini yourself),
  Zipkin :9411 (all interfaces, traces only), Swagger/API-docs are still public.
- **#10 sensitive data / roles — DONE**: the four admin endpoints (`/admin/users`, suspend, reactivate, role) returned the raw
  `User` entity, i.e. `passwordHash` (and OAuth `providerId`). They now return `AdminUserDto` (explicit allow-list), and
  the entity also has `@JsonIgnore` on both fields as a second barrier. `changeRole` only accepts `USER` / `ADMIN` /
  `PLATFORM_ADMIN` (any string used to be stored and shown in the audit log; a missing value was a 500), refuses
  changing your own role, and invalidates the target's tokens so a demotion applies immediately instead of after the
  24h token expiry. **Open product question, deliberately not changed**: `GET /profile/{id}`, `/search` and
  `/users/batch` show any signed-in user another user's email and phone number (the DM info panel displays them).
  Also note plain `ADMIN` can change another user's profile/password endpoints (`updateProfile`/`changePassword`
  accept ADMIN as well as self) — decide whether that should be PLATFORM_ADMIN only.
- **#15 brute-force protection — DONE**: password login had no throttling at all (12 wrong passwords in a row went
  through). New `LoginAttemptService` (Redis, TTL-based so locks expire on their own): 5 wrong passwords lock that account's
  password login for 15 min (429 + `Retry-After`, even the correct password is refused while locked); the counter is keyed
  by user id when the account exists and by the normalised typed identifier otherwise, so unknown names behave identically
  (no enumeration) and email/username variants share one lock; a correct password clears it. Per client IP: 30 failures /
  15 min blocks password spraying. The same limiter guards the current-password check in change-password and the
  password confirmation in delete-account (separate counter). Email-OTP login and password reset are NOT locked, so a
  locked-out owner is never stuck. Trade-off accepted: someone who knows a username can lock that account's password
  login for 15 min. Also fixed: OTP verify counted attempts as get-then-increment, so parallel guesses shared one budget
  (now an atomic increment first; 30 parallel guesses are all counted), and the gateway now overwrites the client's
  `X-Forwarded-For` with the real remote address — the existing per-IP limits (forgot-password, OTP) trusted its first
  entry, so they could be dodged by lying. Live-verified (19 checks incl. lock expiry and rotating fake XFF).
- **#16 member validation — DONE**: group/DM creation and add-member accepted any user id (never-existing or deleted
  accounts became phantom members). room-service now asks auth-service (`UserDirectory` + Feign `AuthClient`, batch lookup
  with the caller id forwarded) and answers 400 naming every unknown id before anything is saved; if auth-service cannot
  answer the request fails closed with 503. Self-join by invite code is unaffected. Live-verified (14 checks).
- **Phase 3 done.** **Phase 4**: #11 (billing UI still advertises
- **#11 billing UI vs backend — DONE**: the billing page and upgrade modal advertised Premium/Platinum limits that were never
  enforced (4/8 GB storage, 90-day history, priority support, 10/25 messages per min, 10/25 groups). Decision (asked): the UI
  now sells **one paid plan, "Pro"** (₹100/month). Checkout still sends the existing `PREMIUM` plan key so payment-service is
  untouched (Razorpay stays paused); legacy PLATINUM subscribers keep their plan/price and admin-granted roles show as Pro.
  Numbers live in frontend `utils/plans.js` (test-pinned): FREE = 5 group chats, 25 members/room, 100 MB, 10 MB files,
  5 uploads/min; Pro = 500 / 250 / 10 GB / 250 MB / 30 per min; 60 messages/min on every plan (not a paid feature).
  The backend did NOT actually enforce the documented 500 group-chat cap for paid users (only FREE was capped) — now
  enforced in room-service, and upgrade prompts say "Upgrade to Pro". If a backend limit changes, update `plans.js`.
- **#14 endpoints the frontend calls that did not exist — DONE (no frontend change needed)**: (1) `POST
  /auth/forgot-password/phone` and (2) `POST /auth/verify-reset-otp/phone` — SMS password reset, mirroring the email flow
  (purpose `resetphone`, same reset-token JWT, generic "if an account exists" answer, only active LOCAL accounts with a
  *verified* phone get a code, 60s cooldown, 5 guesses per code, same per-IP limit as email); (3) `GET /rooms/join/{code}` —
  invite preview for the join page: `RoomPreviewDto` (name, description, avatarUrl, isPrivate, memberCount, maxMembers only;
  no room id/creator/members/code), any signed-in user, 404 for unknown/revoked codes. Because codes are only 8 hex chars,
  preview and join lookups are limited to 20/min per user (`InviteLookupLimiter`, 429). Live-verified (27 checks).
  Note: registration only verifies the email, so phone-reset only works for users who verified their phone number.
  the old limits), #12, #14. **Phase 5**: P2 hygiene #17–#22.

## Not yet done
- **RAM usage reduction beyond the `-Xmx300m` stopgap** — still not a real
  fix, just a bound. Currently stable at ~2.5-3.2GB free with everything
  running (11 services + Redis + Kafka + Zipkin). Revisit trimming unused
  Spring Boot autoconfig if RAM pressure becomes a recurring problem again.
