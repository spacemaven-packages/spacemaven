FROM alpine:latest
RUN apk add openjdk21-jre
COPY build/tasks/_spacemaven_executableJarJvm/spacemaven-jvm-executable.jar /app/spacemaven.jar

WORKDIR /app
EXPOSE 8080:8080/tcp
ENTRYPOINT ["/usr/bin/java", "-jar", "/app/spacemaven.jar"]
