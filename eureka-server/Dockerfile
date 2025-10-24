FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src

FROM openjdk:17-jdk-slim
COPY /target/eureka-server-1.0.0.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]