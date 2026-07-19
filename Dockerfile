FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw \
  && ./mvnw -q -DskipTests dependency:go-offline

COPY src src

RUN ./mvnw -q -DskipTests package \
  && JAR_FILE="$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*.jar.original' | head -n 1)" \
  && test -n "$JAR_FILE" \
  && cp "$JAR_FILE" /tmp/app.jar

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=release

RUN mkdir -p /app/data

COPY --from=builder /tmp/app.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
