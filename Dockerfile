FROM maven:3.9.6-eclipse-temurin-17-alpine 

RUN apk add --no-cache libstdc++ gcompat bash git make curl

# Install the parent pom and the explorer module, which the library depends on
WORKDIR /reynard
COPY ./pom.xml ./pom.xml
COPY ./explorer ./explorer
RUN mvn -N install && mvn -f explorer/pom.xml install -DskipTests

WORKDIR /app
COPY ./library .

RUN mvn clean install -DskipTests
