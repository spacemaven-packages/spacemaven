FROM gradle:8.14.3-jdk-21-and-24-graal AS build
COPY . /build-env
RUN cd /build-env && gradle -Pspm.buildingFromDockerImage nativeCompile precompileJte

FROM alpine:latest
COPY --from=build /build-env/build/install/spacemaven /app
COPY --from=build /build-env/jte-classes /app/jte-classes
WORKDIR /app
EXPOSE 8080:8080/tcp
ENTRYPOINT ["/app/bin/spacemaven"]
