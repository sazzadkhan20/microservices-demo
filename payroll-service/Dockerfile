FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :payroll-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=build /workspace/payroll-service/build/libs/payroll-service-*.jar app.jar
USER 10001
EXPOSE 8082
# Guide §9: container-aware heap, fail fast on OOM (platform restarts the pod), UTC clock.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC -Duser.timezone=UTC -Dfile.encoding=UTF-8"     PLATFORM_DOTENV_DISABLED=true
ENTRYPOINT ["java","-jar","/app/app.jar"]
