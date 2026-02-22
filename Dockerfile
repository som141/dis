FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY build/libs/TBot1-all.jar app.jar
COPY src/main/resources ./resources

ENTRYPOINT ["java", "-jar", "app.jar"]
