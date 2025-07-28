FROM gradle:8.14.3-jdk24 AS build
COPY . /build-env
RUN cd /build-env && gradle -Pspm.buildingFromDockerImage installDist

FROM eclipse-temurin:24
COPY --from=build /build-env/build/install/spacemaven /app
WORKDIR /app
EXPOSE 8080:8080/tcp
ENTRYPOINT ["/app/bin/spacemaven"]
