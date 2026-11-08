FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :leave-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=build /workspace/leave-service/build/libs/leave-service-*.jar app.jar
USER 10001
EXPOSE 8086
ENTRYPOINT ["java","-jar","/app/app.jar"]
