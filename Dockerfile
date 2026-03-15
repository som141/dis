FROM eclipse-temurin:21-jre

WORKDIR /app

COPY build/libs/TBot1-all.jar app.jar
COPY src/main/resources ./resources

HEALTHCHECK --interval=5s --timeout=2s --retries=20 CMD test -f /tmp/ready || exit 1

ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]