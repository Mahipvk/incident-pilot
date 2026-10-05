# Build stage: compiles the app inside Docker, so you don't need Java or Maven installed
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -DskipTests package

# Run stage: small runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/incidentpilot-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
