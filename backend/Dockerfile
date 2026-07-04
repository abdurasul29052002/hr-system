FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

COPY app.jar /app/app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xmx512m -Xms256m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
