# syntax=docker/dockerfile:1
FROM ghcr.io/graalvm/native-image-community:25 AS build
WORKDIR /workspace
COPY --chmod=755 mvnw ./mvnw
COPY .mvn ./.mvn
COPY pom.xml catalog-info.yaml ./
COPY src ./src
# Test fixtures are used by the JVM tests, not copied into the runtime image.
COPY data ./data
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp test && \
    ./mvnw -B -ntp -Pnative -DskipTests native:compile

FROM gcr.io/distroless/cc-debian13:nonroot
WORKDIR /app
COPY --from=build --chown=65532:65532 /workspace/target/ReferenceData /app/reference-data
EXPOSE 8080
USER 65532:65532
ENTRYPOINT ["/app/reference-data"]
