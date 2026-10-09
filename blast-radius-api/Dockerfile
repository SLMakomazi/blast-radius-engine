# Build the Spring Boot application (the project targets Java 21).
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean verify -B

# Run only the compiled application.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/blast-radius-api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
