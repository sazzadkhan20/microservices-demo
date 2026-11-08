FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :file-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=build /workspace/file-service/build/libs/file-service-*.jar app.jar
USER 10001
EXPOSE 8084
ENTRYPOINT ["java","-jar","/app/app.jar"]
