FROM openjdk:8-jdk-alpine
LABEL authors="slcn"
ARG JAR_FILE=./build/libs/slcnapp-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]