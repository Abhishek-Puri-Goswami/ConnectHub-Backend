# Pre-requisite: mvn clean package -DskipTests
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY config-server/target/config-server-1.0.0.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-Xmx256m", "-jar", "app.jar"]
