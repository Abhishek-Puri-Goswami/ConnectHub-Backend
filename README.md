# ConnectHub v2 â€” Production-Ready Real-Time Chat Backend

## What's New in v2
- **OTP email verification** on registration (same-page flow, 5min expiry, 60s resend cooldown, max 5 attempts)
- **Forgot password** with OTP â†’ reset token â†’ new password flow
- **Strong input validation** â€” passwords require uppercase, lowercase, digit, special char (8-72 chars)
- **XSS sanitization** on all user content (messages, bios, room names)
- **API versioning** â€” all routes prefixed with `/api/v1/`
- **Flyway migrations** â€” schema versioned, no more `ddl-auto=update`
- **Logback** with console + rolling file appenders + MDC trace IDs
- **Cursor-based pagination** for messages (no duplicate/missed messages)
- **Redis Pub/Sub** email pipeline (auth publishes â†’ notification subscribes â†’ sends)
- **Rate limiting** at gateway (100 req/min per user via Redis)
- **Circuit breaker ready** (Resilience4j in gateway)
- **Audit logging** for all admin actions
- **JaCoCo** 80% coverage target + SonarQube integration
- **Gateway-only external access** (services use `expose:` not `ports:` in Docker)
- **@LoadBalanced RestTemplate** for service-to-service calls via Eureka

## Architecture
```
Internet â†’ ALB â†’ API Gateway (8080) â†’ Eureka Discovery
                       â†“
    â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¼â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
    â”‚   auth (8081)    â”‚  room (8082)  msg (8083) â”‚
    â”‚   media (8084)   â”‚  presence (8085/Redis)    â”‚
    â”‚   notif (8086)   â”‚  websocket (8087/STOMP)   â”‚
    â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”´â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
              â†“                    â†“
         MySQL (per-svc)     Redis (shared)
```

## Services
| Service | Port | DB | Key Features |
|---------|------|----|------|
| service-registry | 8761 | â€” | Eureka discovery |
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
POST /api/v1/auth/register           â†’ 201 {message, email}
POST /api/v1/auth/verify-registration-otp â†’ 200 {accessToken, refreshToken, user}
POST /api/v1/auth/resend-registration-otp â†’ 200 {message, cooldownSeconds}
```

## Password Reset Flow
```
POST /api/v1/auth/forgot-password    â†’ 200 {message} (always succeeds)
POST /api/v1/auth/verify-reset-otp   â†’ 200 {data: resetToken}
POST /api/v1/auth/reset-password     â†’ 200 {message}
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

## Project Evolution Track
- [x] Infrastructure Setup
- [x] Admin Server
- [x] API Gateway
- [x] Auth Service
<<<<<<< HEAD
- [x] Media Service
=======
>>>>>>> service/auth-service
