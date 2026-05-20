# jdt2jar

`jdt2jar` compiles a JTD schema into a standalone validator JAR at build time.

## CLI

```bash
jdt2jar <schema.json> [options]
```

Options:

- `--output <path>`: output JAR path
- `--package <name>`: generated package name
- `--class <name>`: validator class name
- `--main`: include a standalone `java -jar` entry point
- `--runtime <version>`: target bytecode version
- `--include-sources`: write a companion `.java` file next to the JAR
- `--help`: show usage

## Container

Build with the root `Dockerfile` and run it as a non-root distroless image.

```bash
docker build -t jdt2jar .
docker run --rm jdt2jar --help
syft packages image:jdt2jar
grype jdt2jar
```
