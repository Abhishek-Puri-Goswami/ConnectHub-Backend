# ConnectHub v2 - Production-Ready Real-Time Chat Backend

## Overview
ConnectHub is a scalable microservices-based backend for a real-time chat application, implementing modern security and performance patterns.

## Features and Enhancements
- **OTP Verification**: Email verification for registration with cooldowns and attempt limits.
- **Advanced Authentication**: Forgot password flow with OTP, reset tokens, and secure password requirements.
- **Security**: XSS sanitization on all user content and API versioning (prefixed with `/api/v1/`).
- **Database Management**: Flyway migrations for versioned schemas.
- **Logging & Tracing**: Logback with console and rolling file appenders, including MDC trace IDs.
- **Performance**: Cursor-based pagination for seamless message loading and Redis-backed rate limiting.
- **Messaging**: Redis Pub/Sub pipeline for asynchronous notifications.
- **Resiliency**: Load-balanced service communication via Eureka and Resilience4j-ready gateway.
- **Quality Assurance**: Integrated JaCoCo target for 80% code coverage and SonarQube analysis.

## Architecture
Communications flow from the user through an Application Load Balancer (ALB) to the API Gateway, which handles discovery via Eureka.

- **Frontend Interface** (Port 5173)
- **API Gateway** (Port 8080)
- **Service Registry** (Port 8761)
- **Core Microservices**:
    - Auth Service (Port 8081)
    - Room Service (Port 8082)
    - Message Service (Port 8083)
    - Media Service (Port 8084)
    - Presence Service (Port 8085)
    - Notification Service (Port 8086)
    - WebSocket Service (Port 8087)

## Tech Stack
- **Database**: MySQL (per service) and Redis (shared).
- **Communication**: REST APIs and STOMP over WebSocket.
- **Service Discovery**: Eureka.
- **Containerization**: Docker Compose.

## Quick Start
```bash
# Build all microservices
mvn clean package -DskipTests

# Start the infrastructure
docker-compose up -d

# Access Eureka Dashboard
http://localhost:8761
```

## API Flows

### Registration
- `POST /api/v1/auth/register` - Submit user details.
- `POST /api/v1/auth/verify-registration-otp` - Verify account via email OTP.
- `POST /api/v1/auth/resend-registration-otp` - Request new OTP.

### Password Management
- `POST /api/v1/auth/forgot-password` - Trigger reset flow.
- `POST /api/v1/auth/verify-reset-otp` - Validate OTP to get reset token.
- `POST /api/v1/auth/reset-password` - Set new password.

## Project Evolution Track
- [x] Infrastructure and Configuration Setup
- [x] Admin Server Implementation
- [x] API Gateway Configuration
- [x] Auth Service with OAuth2 and OTP
- [x] Media Service (S3 Integration)
- [x] Message Service (Cursor Pagination)
- [x] Notification Service (Email & SMS)
- [x] Payment Service
- [x] Presence Service (Redis)
- [x] Room Service (Management)
- [x] Service Registry (Eureka)
- [x] WebSocket Service (Real-time)
