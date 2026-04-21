# ── Stage 1: Build ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

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
COPY code-coverage-report/pom.xml code-coverage-report/pom.xml

RUN mvn -pl websocket-service -am dependency:go-offline -q

COPY websocket-service/src websocket-service/src
RUN mvn -pl websocket-service -am package -DskipTests -q

# ── Stage 2: Runtime ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/websocket-service/target/*.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
