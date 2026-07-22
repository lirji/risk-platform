# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -pl fraud-decision-log -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 risk
WORKDIR /app
COPY --from=build /workspace/fraud-decision-log/target/fraud-decision-log-0.0.1-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
