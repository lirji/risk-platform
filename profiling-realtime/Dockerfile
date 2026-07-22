FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./mvnw -B -pl profiling-realtime -am package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 risk
WORKDIR /app
COPY --from=build /workspace/profiling-realtime/target/profiling-realtime-0.0.1-SNAPSHOT.jar app.jar
USER 10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
