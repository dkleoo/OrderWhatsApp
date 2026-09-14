# Build stage (Debian, not Alpine — avoids musl/toolchain issues)
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/libs.versions.toml ./gradle/libs.versions.toml
COPY gradle/wrapper ./gradle/wrapper
COPY server ./server

RUN chmod +x ./gradlew \
    && ./gradlew :server:buildFatJar --no-daemon --stacktrace -x test

# Runtime stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd --create-home --shell /bin/bash app
USER app

COPY --from=build /app/server/build/libs/app.jar ./app.jar

ENV PORT=8080
EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
