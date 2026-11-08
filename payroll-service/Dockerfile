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
ENTRYPOINT ["java","-jar","/app/app.jar"]
