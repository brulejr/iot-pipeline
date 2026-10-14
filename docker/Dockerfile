# syntax=docker/dockerfile:1
#
# The iot-pipeline application image, used by docker/local and docker/prod.
#
# Gradle builds the jar first; this only packages it:
#   ./gradlew bootJar
#   docker build -f docker/Dockerfile -t iot-pipeline .
# The build context is the repository root. Docker reads docker/Dockerfile.dockerignore,
# which admits nothing but the jar.

FROM eclipse-temurin:21-jre AS extract
WORKDIR /build
COPY build/libs/iot-pipeline.jar app.jar
# Split into layers that change at different rates, so a code change rebuilds only the
# last one and the dependencies stay cached.
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=extract /build/extracted/dependencies/ ./
COPY --from=extract /build/extracted/spring-boot-loader/ ./
COPY --from=extract /build/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/extracted/application/ ./
USER app
# Every setting comes from the environment; see docker/prod/README.md. The jar carries
# no application-local.yml, so the local profile would have nothing to offer here.
ENV PROFILE=prod
EXPOSE 5001
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
