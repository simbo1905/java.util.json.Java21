# jdt2jar

`jdt2jar` compiles a JTD schema into a standalone validator JAR at build time. The generated JAR runs on JDK 21+ with no JDK 24+ runtime dependency.

## CLI

```bash
jdt2jar <schema.json> [options]
```

Options:

- `--output <path>`: output JAR path (default: `<schema-name>-validator.jar`)
- `--package <name>`: generated package name (default: `jtd.generated`)
- `--class <name>`: validator class name (default: `SchemaValidator`)
- `--main`: include a standalone `java -jar` entry point
- `--runtime <version>`: target bytecode version (default: 21)
- `--include-sources`: write a companion `.java` file next to the JAR
- `--help`: show usage

## Container Image

A minimal distroless container image is available for offline schema compilation without a full JDK.

### Pre-built Image (GitHub Container Registry)

```bash
# Pull the latest image
docker pull ghcr.io/simbo1905/java.util.json.java21/jdt2jar:latest

# Pull a specific release version
docker pull ghcr.io/simbo1905/java.util.json.java21/jdt2jar:2026.02.05
```

### Build Locally

Requires Docker and JDK 24+ (for the build stage). Build from the repository root:

```bash
docker build -t jdt2jar -f jdt2jar/Dockerfile .
```

### Usage

```bash
# Show help
docker run --rm ghcr.io/simbo1905/java.util.json.java21/jdt2jar:latest --help

# Compile a schema to a validator JAR (using docker cp for file I/O)
cid=$(docker create --name jdt2jar-build ghcr.io/simbo1905/java.util.json.java21/jdt2jar:latest /work/person.jtd.json --output /work/person-validator.jar --main)
docker cp person.jtd.json jdt2jar-build:/work/person.jtd.json
docker start -a jdt2jar-build
docker cp jdt2jar-build:/work/person-validator.jar .
docker rm jdt2jar-build

# Validate a payload with the generated JAR
java -jar person-validator.jar --validate payload.json
# Or validate inside a container
cid=$(docker create --name jdt2jar-validate --entrypoint /jre/bin/java ghcr.io/simbo1905/java.util.json.java21/jdt2jar:latest -jar /work/person-validator.jar --validate /work/payload.json)
docker cp person-validator.jar jdt2jar-validate:/work/person-validator.jar
docker cp payload.json jdt2jar-validate:/work/payload.json
docker start -a jdt2jar-validate
docker rm jdt2jar-validate
```

### Image Properties

- **Base**: `gcr.io/distroless/base-debian13:nonroot`
- **Runtime**: jlink-minimized JDK 24 (~40 MB)
- **Total size**: ~111 MB disk / ~31 MB content
- **User**: `nonroot` (uid 65532)
- **Shell**: none (distroless)
- **Writable directories**: `/work` (for schema input and JAR output)

### Security Scanning

```bash
syft packages image:ghcr.io/simbo1905/java.util.json.java21/jdt2jar:latest
grype ghcr.io/simbo1905/java.util.json.java21/jdt2jar:latest
```
