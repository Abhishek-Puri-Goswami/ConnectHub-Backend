# ConnectHub v2 — Production-Ready Real-Time Chat Backend

## What's New in v2
- **OTP email verification** on registration (same-page flow, 5min expiry, 60s resend cooldown, max 5 attempts)
- **Forgot password** with OTP → reset token → new password flow
- **Strong input validation** — passwords require uppercase, lowercase, digit, special char (8-72 chars)
- **XSS sanitization** on all user content (messages, bios, room names)
- **API versioning** — all routes prefixed with `/api/v1/`
- **Flyway migrations** — schema versioned, no more `ddl-auto=update`
- **Logback** with console + rolling file appenders + MDC trace IDs
- **Cursor-based pagination** for messages (no duplicate/missed messages)
- **Redis Pub/Sub** email pipeline (auth publishes → notification subscribes → sends)
- **Rate limiting** at gateway (100 req/min per user via Redis)
- **Circuit breaker ready** (Resilience4j in gateway)
- **Audit logging** for all admin actions
- **JaCoCo** 80% coverage target + SonarQube integration
- **Gateway-only external access** (services use `expose:` not `ports:` in Docker)
- **@LoadBalanced RestTemplate** for service-to-service calls via Eureka

## Architecture
```
Internet → ALB → API Gateway (8080) → Eureka Discovery
                       ↓
    ┌──────────────────┼──────────────────────────┐
    │   auth (8081)    │  room (8082)  msg (8083) │
    │   media (8084)   │  presence (8085/Redis)    │
    │   notif (8086)   │  websocket (8087/STOMP)   │
    └──────────────────┴──────────────────────────┘
              ↓                    ↓
         MySQL (per-svc)     Redis (shared)
```

## Services
| Service | Port | DB | Key Features |
|---------|------|----|------|
| service-registry | 8761 | — | Eureka discovery |
| api-gateway | 8080 | Redis | JWT filter, rate limit, trace ID, circuit breaker |
| auth-service | 8081 | MySQL+Redis | Register+OTP, login, OAuth2, forgot password, audit |
| room-service | 8082 | MySQL | Rooms, members, roles, mute, pin |
| message-service | 8083 | MySQL | Messages, cursor pagination, reactions, XSS sanitize |
| media-service | 8084 | MySQL+S3 | Upload, thumbnails, presigned URLs |
| presence-service | 8085 | Redis | Online tracking, stale cleanup |
| notification-service | 8086 | MySQL+Redis | Email (OTP/welcome), in-app notifications |
| websocket-service | 8087 | Redis | STOMP/SockJS, Redis Pub/Sub broadcast |

## Quick Start
```bash
# Build all services
mvn clean package -DskipTests

# Start everything
docker-compose up -d

# Check Eureka
open http://localhost:8761

# Test registration
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"john","email":"john@test.com","password":"Test@1234","fullName":"John Doe"}'

# Swagger UI (per service, access directly in dev)
open http://localhost:8081/swagger-ui.html
```

## Registration Flow
```
POST /api/v1/auth/register           → 201 {message, email}
POST /api/v1/auth/verify-registration-otp → 200 {accessToken, refreshToken, user}
POST /api/v1/auth/resend-registration-otp → 200 {message, cooldownSeconds}
```

## Password Reset Flow
```
POST /api/v1/auth/forgot-password    → 200 {message} (always succeeds)
POST /api/v1/auth/verify-reset-otp   → 200 {data: resetToken}
POST /api/v1/auth/reset-password     → 200 {message}
```

## WebSocket Connection
```javascript
const socket = new SockJS("http://localhost:8080/ws");
const client = Stomp.over(socket);
client.connect({Authorization: "Bearer <jwt>"}, () => {
  client.subscribe("/topic/room/<roomId>", msg => console.log(JSON.parse(msg.body)));
  client.send("/app/chat.send", {}, JSON.stringify({roomId:"<id>",content:"Hello!",type:"TEXT"}));
});
```
