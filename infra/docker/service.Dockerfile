FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY app.jar app.jar
RUN chown app:app app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
