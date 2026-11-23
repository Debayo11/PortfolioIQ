FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:24-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser
EXPOSE 8080
# Size the heap from the container limit and restart cleanly instead of limping on after an out-of-memory error.
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-XX:+ExitOnOutOfMemoryError","-jar","/app/app.jar"]
