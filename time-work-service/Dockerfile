FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :time-work-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=build /workspace/time-work-service/build/libs/time-work-service-*.jar app.jar
USER 10001
EXPOSE 8087
ENTRYPOINT ["java","-jar","/app/app.jar"]
