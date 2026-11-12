# Runtime-only image for a Spring Boot service whose jar was built ONCE by CI (guide §9:
# build each artifact once, promote the same image digest). Local compose keeps using the
# per-service Dockerfiles (they compile inside Docker).
#
#   docker build -f docker/java-runtime.Dockerfile --build-arg JAR=people-service/build/libs/people-service-1.0.0-SNAPSHOT.jar \
#                --build-arg PORTS="8081 9081" -t qii-hrms-people-service .
FROM eclipse-temurin:21-jre

ARG JAR
ARG PORTS="8080"
ARG VERSION=dev
ARG REVISION=unknown

LABEL org.opencontainers.image.title="qii-hrms" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.vendor="Qii Creative"

WORKDIR /app
RUN useradd -r -u 10001 -s /usr/sbin/nologin appuser
COPY --chown=root:root ${JAR} /app/app.jar
USER 10001
EXPOSE ${PORTS}
# Container-aware heap, fail fast on OOM (the platform restarts the container), UTC clock,
# never read a .env file inside the image.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC -Duser.timezone=UTC -Dfile.encoding=UTF-8" \
    PLATFORM_DOTENV_DISABLED=true
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
