# syntax=docker/dockerfile:1

# ==============================================================================
# Stage 1: Build Multi-Module Spring Boot 4 Backend
# ==============================================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS backend-builder
WORKDIR /app

# Cache Maven dependencies by copying pom files first
COPY backend/pom.xml ./backend/pom.xml
COPY backend/duespilot-core/pom.xml ./backend/duespilot-core/pom.xml
COPY backend/duespilot-api/pom.xml ./backend/duespilot-api/pom.xml
COPY backend/duespilot-bench/pom.xml ./backend/duespilot-bench/pom.xml

# Copy backend source trees
COPY backend/duespilot-core/src ./backend/duespilot-core/src
COPY backend/duespilot-bench/src ./backend/duespilot-bench/src
COPY backend/duespilot-api/src ./backend/duespilot-api/src

# Package backend executable fat-jar
WORKDIR /app/backend
RUN mvn clean package -DskipTests

# ==============================================================================
# Stage 2: Minimal Production JRE Runtime
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Hardened security: run as unprivileged user (Rule R-53, R-55)
RUN addgroup -S duespilot && adduser -S duespilot -G duespilot && \
    mkdir -p /app/data/uploads /app/benchmark-data && \
    chown -R duespilot:duespilot /app

USER duespilot

# Copy executable jar from backend-builder stage
COPY --from=backend-builder --chown=duespilot:duespilot /app/backend/duespilot-api/target/duespilot-api-0.0.1-SNAPSHOT-exec.jar /app/app.jar


# Default Environment Variables (overridable at deployment)
ENV PORT=8080 \
    SERVER_PORT=8080 \
    DEMO_MODE=replay \
    RATE_LIMIT_RPM=60 \
    STORAGE_DIR=/app/data/uploads \
    DAILY_COST_CAP_MICROS=50000000 \
    PER_CASE_COST_CAP_MICROS=2000000 \
    LLM_MAX_INFLIGHT=8

EXPOSE 8080

# Memory-conscious JVM flags:
# - MaxRAMPercentage=75.0 (respects container cgroup limit e.g. 1Gi or 2Gi on Cloud Run / Render)
# - ExitOnOutOfMemoryError (ensures immediate pod restart on OOM)
# - Generational ZGC for low-latency virtual thread handling
# - Honours $PORT injected by Cloud Run / Render via exec shell expansion
# - exec ensures java is PID 1 to receive SIGTERM for graceful shutdown
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError -XX:+UseZGC -XX:+ZGenerational -Dserver.port=${PORT:-8080} -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar"]
