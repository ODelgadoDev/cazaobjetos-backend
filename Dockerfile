FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --chown=10001:10001 target/cazaobjetos-0.1.0-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENV SERVER_ADDRESS=0.0.0.0
ENTRYPOINT ["java","-jar","/app/app.jar"]
