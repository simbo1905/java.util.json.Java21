# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
COPY . .
RUN ["./mvnw", "-pl", "jtd2jar", "-am", "package", "-DskipTests", "-Dsurefire.failIfNoSpecifiedTests=false"]
RUN ["java", "-cp", "/build/jtd2jar/target/jtd2jar.jar", "json.java21.jtd2jar.build.DockerImageBuilder", "/build/jtd2jar/target/jtd2jar.jar", "/opt/jre"]
RUN ["mkdir", "-p", "/empty-work/tmp", "/empty-app"]

FROM gcr.io/distroless/base-debian13:nonroot
COPY --from=build --chown=65532:65532 /empty-work /work
COPY --from=build --chown=65532:65532 /empty-app /app
WORKDIR /work
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.io.tmpdir=/work/tmp -XX:+ExitOnOutOfMemoryError"
COPY --from=build /opt/jre /jre
COPY --from=build /build/jtd2jar/target/jtd2jar.jar /app/jtd2jar.jar
ENTRYPOINT ["/jre/bin/java","-jar","/app/jtd2jar.jar"]
