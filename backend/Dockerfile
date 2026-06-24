# --- Stage 1: сборка fat jar ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Сначала только файлы сборки — для кэширования зависимостей
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts backend/gradle.properties ./
COPY backend/gradle ./gradle
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Затем исходники и сборка
COPY backend/src ./src
RUN ./gradlew --no-daemon buildFatJar

# --- Stage 2: рантайм ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /src/build/libs/hookahguide-api-all.jar app.jar

# Контент базы знаний (можно переопределить volume-монтированием в compose)
COPY knowledge /content/knowledge
COPY data /content/data

ENV CONTENT_ROOT=/content \
    PORT=8080 \
    HOST=0.0.0.0

EXPOSE 8080

# health-check бьёт в /health
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/health || exit 1

CMD ["java", "-jar", "app.jar"]
