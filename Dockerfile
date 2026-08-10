# ==========================================================
# Stage 1: Build the Angular frontend
# ==========================================================
FROM --platform=$BUILDPLATFORM node:22-alpine AS frontend-builder
WORKDIR /app

# Copy package lock and install dependencies
COPY frontend/trip-brain-frontend-app/package*.json ./
RUN npm ci

# Copy application files and build
COPY frontend/trip-brain-frontend-app/ ./
RUN npm run build

# ==========================================================
# Stage 2: Build the Spring Boot backend
# ==========================================================
FROM eclipse-temurin:25-jdk AS backend-builder
WORKDIR /app

# Copy gradle wrapper and project descriptors
COPY backend/gradlew gradlew
COPY backend/gradle gradle/
COPY backend/build.gradle build.gradle
COPY backend/settings.gradle settings.gradle

# Give execution permission to Gradle wrapper
RUN chmod +x gradlew

# Pre-fetch dependencies
RUN ./gradlew dependencies --no-daemon

# Copy backend source code
COPY backend/src src/

# Copy compiled frontend static browser files into Spring Boot's static resources
COPY --from=frontend-builder /app/dist/trip-brain-frontend-app/browser/ src/main/resources/static/

# Build Spring Boot bootJar, skipping tests and skipping localized frontend task execution
RUN ./gradlew bootJar -PskipFrontend=true -x test --no-daemon

# ==========================================================
# Stage 3: Package lightweight runtime container JRE
# ==========================================================
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the built jar file
COPY --from=backend-builder /app/build/libs/*.jar app.jar

# Create default uploads & logs directory inside container
RUN mkdir -p uploads/final_trip_pdfs /logs/tripbrain

# Default container log path
ENV LOG_PATH=/logs/tripbrain

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-Xms512m", "-Xmx896m", "-Xss512k", "-XX:MaxMetaspaceSize=256m", "-XX:ReservedCodeCacheSize=128m", "-XX:CICompilerCount=2", "-XX:+UseSerialGC", "-jar", "app.jar"]
