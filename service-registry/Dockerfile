# ── Stage 1: Build ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# Copy parent pom first (for dependency caching)
COPY pom.xml .
COPY service-registry/pom.xml service-registry/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY room-service/pom.xml room-service/pom.xml
COPY message-service/pom.xml message-service/pom.xml
COPY media-service/pom.xml media-service/pom.xml
COPY presence-service/pom.xml presence-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY websocket-service/pom.xml websocket-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY admin-server/pom.xml admin-server/pom.xml

# Download dependencies (cached layer)
RUN mvn -pl service-registry -am dependency:go-offline -q

# Copy source and build
COPY service-registry/src service-registry/src
RUN mvn -pl service-registry -am package -DskipTests -q

# ── Stage 2: Runtime ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/service-registry/target/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-Xmx256m", "-jar", "app.jar"]
