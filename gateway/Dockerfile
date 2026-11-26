# Build context: individual Java service directory
FROM gradle:8.14.3-jdk21 AS builder
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle settings.gradle build.gradle gradlew ./
COPY --chown=gradle:gradle gradle ./gradle
COPY --chown=gradle:gradle src ./src
RUN chmod +x gradlew && \
    ./gradlew --no-daemon bootJar && \
    find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' \
    -exec cp {} /tmp/app.jar \;

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
COPY --from=builder /tmp/app.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70.0","-jar","/app/app.jar"]
