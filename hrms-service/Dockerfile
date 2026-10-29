FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :hrms-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/hrms-service/build/libs/hrms-service-*.jar app.jar
EXPOSE 8081 9081
ENTRYPOINT ["java","-jar","/app/app.jar"]
