FROM bellsoft/liberica-openjdk-alpine:17 AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY settings.gradle .
COPY build.gradle .
COPY slcn-spec slcn-spec
COPY slcn-aggregate slcn-aggregate
COPY slcn-auth slcn-auth
COPY slcn-rest slcn-rest
COPY slcn-boot slcn-boot

RUN chmod +x gradlew
RUN ./gradlew clean :slcn-boot:bootJar --no-daemon


FROM bellsoft/liberica-openjre-alpine:17

WORKDIR /app
LABEL authors="slcn"
ENV TZ=Asia/Seoul

COPY --from=builder /app/boot/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]