FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :api-gateway:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd -r -u 10001 appuser
COPY --from=build /workspace/api-gateway/build/libs/api-gateway-*.jar app.jar
USER 10001
EXPOSE 8090
ENTRYPOINT ["java","-jar","/app/app.jar"]
