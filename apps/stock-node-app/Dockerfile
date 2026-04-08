FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY apps/stock-node-app/build/libs/stock-node-app.jar app.jar

HEALTHCHECK --interval=5s --timeout=2s --retries=20 CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
