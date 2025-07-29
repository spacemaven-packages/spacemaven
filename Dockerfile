FROM gradle:8.14.3-jdk24 AS build
COPY . /build-env
RUN cd /build-env && gradle -Pspm.buildingFromDockerImage installDist

FROM eclipse-temurin:24
ENV JAVA_OPTS="-Dotel.service.name=spacemaven \
    -Dotel.resource.attributes=gcp.project_id=spacemaven \
    -Dotel.metrics.exporter=otlp \
    -Dotel.traces.exporter=otlp \
    -Dotel.logs.exporter=none \
    -Dotel.exporter.otlp.endpoint=https://telemetry.googleapis.com \
    -Dotel.exporter.otlp.protocol=http/protobuf \
    -Dgoogle.cloud.project=spacemaven"
COPY --from=build /build-env/build/install/spacemaven /app
WORKDIR /app
EXPOSE 8080:8080/tcp
ENTRYPOINT ["/app/bin/spacemaven"]
