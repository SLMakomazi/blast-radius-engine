FROM docker.io/library/maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
# Cache dependencies before copying source so instrumentation edits do not redownload them.
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress clean verify

FROM docker.io/library/maven:3.9.16-eclipse-temurin-21 AS agent
ADD --checksum=sha256:bbf83c151b6400709e2f225bdd07a04f839d9d13b8b93464241333fd25d3e3ba \
    https://repo.maven.apache.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/2.31.1/opentelemetry-javaagent-2.31.1.jar /otel-agent.jar
RUN echo "bbf83c151b6400709e2f225bdd07a04f839d9d13b8b93464241333fd25d3e3ba  /otel-agent.jar" | sha256sum -c -

FROM docker.io/library/eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/payment-service.jar ./app.jar
COPY --from=agent --chown=app:app /otel-agent.jar /app/otel-agent.jar
USER app:app
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD curl --fail --silent http://localhost:8081/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
