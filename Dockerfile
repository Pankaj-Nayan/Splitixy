FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY splitixy-web/pom.xml ./pom.xml
RUN mvn -B -DskipTests dependency:go-offline

COPY splitixy-web/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/*.jar /app/app.jar

USER 10001:10001
EXPOSE 6969
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
