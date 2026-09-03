FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY gateway/build.gradle gateway/build.gradle
COPY platform-service/build.gradle platform-service/build.gradle
COPY ticketing-service/build.gradle ticketing-service/build.gradle
COPY payment-notification-service/build.gradle payment-notification-service/build.gradle
COPY payment-notification-service/src payment-notification-service/src
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew :payment-notification-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=builder --chown=spring:spring /workspace/payment-notification-service/build/libs/*.jar app.jar
USER spring:spring
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
