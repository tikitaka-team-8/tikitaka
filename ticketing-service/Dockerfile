FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY gateway/build.gradle gateway/build.gradle
COPY platform-service/build.gradle platform-service/build.gradle
COPY ticketing-service/build.gradle ticketing-service/build.gradle
COPY payment-notification-service/build.gradle payment-notification-service/build.gradle
COPY ticketing-service/src ticketing-service/src
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :ticketing-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=builder --chown=spring:spring /workspace/ticketing-service/build/libs/*.jar app.jar
USER spring:spring
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
