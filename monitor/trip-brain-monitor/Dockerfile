# Build stage
FROM gradle:8.11-jdk21 AS builder
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle bootJar --no-daemon

# Execution stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

ENV PORT=8085
EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]
