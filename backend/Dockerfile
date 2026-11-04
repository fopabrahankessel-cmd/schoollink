# Production image of the SchoolLink API (docs/DEPLOYMENT.md). Built on the server by deploy/docker-compose.yml.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
# the tests run on the developer's machine (./mvnw verify); they need Docker, which the build has not
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home schoollink
WORKDIR /app
COPY --from=build /src/target/SchoolLink-*.jar app.jar
USER schoollink
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "/app/app.jar"]
