FROM eclipse-temurin:25-jre-alpine

LABEL org.opencontainers.image.title="hr-system-backend" \
      org.opencontainers.image.authors="Abdurasul"

WORKDIR /app

# The executable Spring Boot JAR is built by CI (./mvnw package) and copied in as app.jar.
COPY app.jar /app/app.jar

EXPOSE 9090

# Production JVM tuning: container-aware heap, G1GC, fail-fast on OOM with a heap dump.
ENV JAVA_OPTS="-XX:+UseG1GC \
    -XX:MaxRAMPercentage=60.0 \
    -XX:InitialRAMPercentage=40.0 \
    -XX:MaxMetaspaceSize=256m \
    -XX:ReservedCodeCacheSize=128m \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/app/heapdump.hprof \
    -Djava.security.egd=file:/dev/./urandom"

# busybox wget (alpine has no curl); non-2xx responses exit non-zero → container marked unhealthy.
# start-period is generous: on a small VPS Spring Boot 4 can take 2-3 min to boot, and the container
# must not be flagged unhealthy while it is still legitimately starting.
HEALTHCHECK --interval=30s --timeout=10s --start-period=210s --retries=5 \
    CMD wget -q -O /dev/null http://localhost:9090/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
