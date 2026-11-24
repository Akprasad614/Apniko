# Build the Spring Boot application with the repository's Maven wrapper.
FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package \
    && jar_file="$(find target -maxdepth 1 -type f -name '*.jar' ! -name 'original-*' -print -quit)" \
    && test -n "$jar_file" \
    && cp "$jar_file" /workspace/apniko.jar

# Minimal runtime image. Secrets are read from mounted Docker secret files.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S apniko && adduser -S -G apniko -h /app apniko
COPY --from=build --chown=apniko:apniko /workspace/apniko.jar /app/apniko.jar
COPY --chown=apniko:apniko scripts/docker-entrypoint.sh /usr/local/bin/apniko-entrypoint
RUN chmod 0555 /usr/local/bin/apniko-entrypoint
USER apniko
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/apniko-entrypoint"]
