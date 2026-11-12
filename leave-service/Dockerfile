FROM eclipse-temurin:24-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :leave-service:bootJar --no-daemon

FROM eclipse-temurin:24-jre
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=build /workspace/leave-service/build/libs/leave-service-*.jar app.jar
USER 10001
EXPOSE 8086
# Guide §9: container-aware heap, fail fast on OOM (platform restarts the pod), UTC clock.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC -Duser.timezone=UTC -Dfile.encoding=UTF-8"     PLATFORM_DOTENV_DISABLED=true
ENTRYPOINT ["java","-jar","/app/app.jar"]
