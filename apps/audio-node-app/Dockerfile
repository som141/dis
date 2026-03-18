FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY apps/audio-node-app/build/libs/audio-node-app.jar app.jar

HEALTHCHECK --interval=5s --timeout=2s --retries=20 CMD test -f /tmp/ready || exit 1

ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
