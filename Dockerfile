# syntax=docker/dockerfile:1

FROM eclipse-temurin:24-jdk AS build
WORKDIR /build
COPY . .
RUN ["./mvnw", "-pl", "jdt2jar", "-am", "package", "-DskipTests", "-Dsurefire.failIfNoSpecifiedTests=false"]
RUN ["java", "-cp", "/build/jdt2jar/target/jdt2jar.jar", "json.java21.jdt2jar.build.DockerImageBuilder", "/build/jdt2jar/target/jdt2jar.jar", "/opt/jre"]
RUN ["mkdir", "-p", "/work/tmp"]
RUN ["chmod", "1777", "/work/tmp"]

FROM gcr.io/distroless/base-debian13:nonroot
WORKDIR /work
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.io.tmpdir=/work/tmp -XX:+ExitOnOutOfMemoryError"
COPY --from=build /opt/jre /jre
COPY --from=build /build/jdt2jar/target/jdt2jar.jar /app/jdt2jar.jar
COPY --from=build /work /work
ENTRYPOINT ["/jre/bin/java","-jar","/app/jdt2jar.jar"]
