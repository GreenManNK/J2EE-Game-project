# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests clean package && \
    JAR_PATH="$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*original*' | head -n 1)" && \
    test -n "$JAR_PATH" && cp "$JAR_PATH" /workspace/app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    APP_EMAIL_MODE=log \
    APP_DATASOURCE_ALLOW_H2_FALLBACK=true \
    APP_DATASOURCE_H2_FILE=/app/data/game-docker

COPY --from=build /workspace/app.jar /app/app.jar

VOLUME ["/app/data"]
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
