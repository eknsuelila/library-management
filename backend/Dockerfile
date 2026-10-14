# =====================================================================================================
# Backend container image, built in two stages ("multi-stage build").
#   Stage 1 has the full JDK + Maven and compiles the jar.
#   Stage 2 starts from a small JRE-only image and copies in just the jar.
# The final image therefore contains no compiler, no Maven, no source code: smaller and safer.
# Build:  docker build -t library-backend .     (docker compose does this for you)
# =====================================================================================================

# ---------- Stage 1: build the jar ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Copy ONLY pom.xml first and download dependencies. Docker caches each step ("layer"); as long as pom.xml
# doesn't change, this slow download step is reused and only the steps below re-run after a code change.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
# Tests already ran in CI (and need no Docker), so skip them here to keep image builds fast.
RUN mvn -q -B package -DskipTests

# ---------- Stage 2: run it ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Never run as root inside a container: if the app were compromised, the attacker would not be root.
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/*.jar app.jar
USER app
# Documentation only: the prod profile listens on $PORT, default 8080.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
