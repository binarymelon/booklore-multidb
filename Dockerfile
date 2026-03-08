# Stage 1: Build the Angular app
FROM node:24-alpine AS angular-build

WORKDIR /angular-app

COPY ./upstream/booklore-ui/package.json ./upstream/booklore-ui/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm config set registry https://registry.npmjs.org/ \
    && npm ci --force

COPY ./upstream/booklore-ui /angular-app/

RUN npm run build --configuration=production

# Stage 2: Build the Spring Boot app with Gradle
FROM gradle:9.3.1-jdk25-alpine AS springboot-build

WORKDIR /springboot-app

# Copy build files first to cache dependency resolution
COPY build.gradle settings.gradle gradlew ./
COPY gradle/ ./gradle/

# Copy upstream sources (needed by source overlay)
COPY upstream/booklore-api/build.gradle upstream/booklore-api/settings.gradle ./upstream/booklore-api/

# Download dependencies (cached layer)
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle dependencies --no-daemon || true

# Copy all sources for the overlay build
COPY upstream/booklore-api/src ./upstream/booklore-api/src
COPY src ./src

# Copy Angular dist into Spring Boot static resources so it's embedded in the JAR
COPY --from=angular-build /angular-app/dist/booklore/browser ./src/main/resources/static

# Inject version into application.yaml using yq
ARG APP_VERSION
RUN apk add --no-cache yq && \
    yq eval '.app.version = strenv(APP_VERSION)' -i ./src/main/resources/application.yaml

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle clean build -x test --no-daemon --parallel

# Stage 3: Final image
FROM eclipse-temurin:25-jre-alpine

ARG APP_VERSION
ARG APP_REVISION

LABEL org.opencontainers.image.title="BookLore MultiDB" \
      org.opencontainers.image.description="BookLore with multi-database support (MariaDB, PostgreSQL, SQLite). A self-hosted digital library with smart shelves, auto metadata, and built-in readers." \
      org.opencontainers.image.version=$APP_VERSION \
      org.opencontainers.image.revision=$APP_REVISION \
      org.opencontainers.image.licenses="GPL-3.0" \
      org.opencontainers.image.base.name="docker.io/library/eclipse-temurin:25-jre-alpine"

ENV JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:+UseCompactObjectHeaders -XX:+UseStringDeduplication -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ARG TARGETARCH
RUN apk update && apk add --no-cache su-exec libstdc++ libgcc && \
    mkdir -p /bookdrop

COPY upstream/docker/unrar/unrar-${TARGETARCH} /usr/local/bin/unrar
RUN chmod 755 /usr/local/bin/unrar

COPY upstream/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
COPY --from=springboot-build /springboot-app/build/libs/booklore-multidb-0.0.1-SNAPSHOT.jar /app/app.jar

ARG BOOKLORE_PORT=6060
EXPOSE ${BOOKLORE_PORT}

ENTRYPOINT ["entrypoint.sh"]
CMD ["java", "-jar", "/app/app.jar"]
