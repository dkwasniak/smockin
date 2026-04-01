FROM eclipse-temurin:17-jdk AS fetch
WORKDIR /tmp
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*
RUN mvn dependency:copy -Dartifact=com.h2database:h2:2.4.240 -DoutputDirectory=/tmp/lib

FROM eclipse-temurin:17-jre
RUN mkdir -p /h2/data
WORKDIR /h2
COPY --from=fetch /tmp/lib/h2-2.4.240.jar /h2/h2.jar
COPY h2-entrypoint.sh /h2/h2-entrypoint.sh
RUN chmod +x /h2/h2-entrypoint.sh
EXPOSE 9092
ENTRYPOINT ["/h2/h2-entrypoint.sh"]
