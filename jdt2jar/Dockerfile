# syntax=docker/dockerfile:1

FROM eclipse-temurin:24-jdk AS build
WORKDIR /build
COPY . .
RUN ["./mvnw", "-pl", "jdt2jar", "-am", "package", "-DskipTests", "-Dsurefire.failIfNoSpecifiedTests=false"]
RUN ["java", "-cp", "/build/jdt2jar/target/jdt2jar.jar", "json.java21.jdt2jar.build.DockerImageBuilder", "/build/jdt2jar/target/jdt2jar.jar", "/opt/jre"]
RUN ["mkdir", "-p", "/empty-work/tmp", "/empty-app"]

FROM gcr.io/distroless/base-debian13:nonroot
COPY --from=build --chown=65532:65532 /empty-work /work
COPY --from=build --chown=65532:65532 /empty-app /app
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.io.tmpdir=/work/tmp -XX:+ExitOnOutOfMemoryError"
COPY --from=build /opt/jre /jre
COPY --from=build /build/jdt2jar/target/jdt2jar.jar /app/jdt2jar.jar
ENTRYPOINT ["/jre/bin/java","-jar","/app/jdt2jar.jar"]
