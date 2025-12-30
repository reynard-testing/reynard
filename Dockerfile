FROM maven:3.9.6-eclipse-temurin-17-alpine 

RUN apk add --no-cache libstdc++ gcompat bash git make curl

WORKDIR /app
COPY ./library .

RUN mvn clean install -DskipTests
