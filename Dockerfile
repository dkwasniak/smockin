# Dockerfile
#
# docker compose up -d
#

# Stage 1: Build
FROM eclipse-temurin:17-jdk AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
COPY install ./install
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
ARG APP_VERSION_ARG='2.21.0'
RUN mkdir -p /app/log
WORKDIR /app
COPY --from=build /root/.m2/repository/com/h2database/h2/2.4.240/h2-2.4.240.jar /app/h2.jar
COPY --from=build /build/target/smockin-${APP_VERSION_ARG}.jar /app/smockin-${APP_VERSION_ARG}.jar
COPY launch.sh /app/launch.sh
EXPOSE 8000 8001 8002 8003
RUN chmod +x /app/launch.sh
ENTRYPOINT ["/app/launch.sh"]
