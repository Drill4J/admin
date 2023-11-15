FROM eclipse-temurin:11.0.21_9-jdk-nanoserver

RUN mkdir distr
RUN mkdir work

COPY build/libs/admin-core-0.8.3-all.jar ./app/admin-core-0.8.3-all.jar

EXPOSE 8090
EXPOSE 5006
EXPOSE 8453

CMD ["java", "-server", "-Djava.awt.headless=true", "-XX:+UseG1GC","-XX:+UseStringDeduplication", "-XX:MaxDirectMemorySize=2G", "-cp", "/app/admin-core-0.8.3-all.jar", "io.ktor.server.netty.EngineMain"]
