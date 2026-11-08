FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :notification-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=build /workspace/notification-service/build/libs/notification-service-*.jar app.jar
USER 10001
EXPOSE 8083
ENTRYPOINT ["java","-jar","/app/app.jar"]
