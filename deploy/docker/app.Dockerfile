# syntax=docker/dockerfile:1
# The single ERP image: Spring Boot application with the SPA embedded (doc §11.1, K8).
# Build from the repository root: docker build -f deploy/docker/app.Dockerfile -t erp-app:local .

FROM node:24.21.0-bookworm-slim AS web
WORKDIR /src/web
RUN corepack enable
COPY web/ ./
RUN --mount=type=cache,id=pnpm-store,target=/pnpm-store \
    pnpm install --frozen-lockfile --store-dir /pnpm-store --filter "@erp/web..."
RUN pnpm --filter @erp/web build

FROM eclipse-temurin:25-jdk-noble AS backend
WORKDIR /src
# New top-level Maven module folders (modules/, packs/, customers/) must be copied here too.
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY platform platform
COPY app app
COPY --from=web /src/web/apps/web/dist web/apps/web/dist
RUN --mount=type=cache,id=maven-repo,target=/root/.m2 \
    ./mvnw -B -ntp -pl app -am package -DskipTests
RUN java -Djarmode=tools -jar app/target/erp-app.jar extract --layers --launcher --destination /layers

FROM eclipse-temurin:25-jre-noble
ARG VERSION=dev
LABEL org.opencontainers.image.title="erp-app" \
      org.opencontainers.image.version="${VERSION}"
RUN groupadd --system --gid 10001 erp \
 && useradd --system --uid 10001 --gid erp --no-create-home --shell /usr/sbin/nologin erp
WORKDIR /app
COPY --from=backend /layers/dependencies/ ./
COPY --from=backend /layers/spring-boot-loader/ ./
COPY --from=backend /layers/snapshot-dependencies/ ./
COPY --from=backend /layers/application/ ./
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
