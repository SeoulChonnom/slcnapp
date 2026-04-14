FROM bellsoft/liberica-openjdk-alpine:17
LABEL authors="slcn"
ENV TZ=Asia/Seoul
ARG JAR_FILE=./slcn-boot/build/libs/slcn-boot.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]