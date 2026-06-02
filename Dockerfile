# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache python3 py3-pip ffmpeg curl && \
    pip3 install --break-system-packages yt-dlp && \
    mkdir -p /tmp/downloads

WORKDIR /app
COPY --from=builder /app/target/*-jar-with-dependencies.jar ./bot.jar

CMD ["java", "-jar", "bot.jar"]
