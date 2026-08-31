#!/usr/bin/env bash
# jtd2jar helper script for environments where volume mounts are restricted
# (e.g., Colima on macOS with projects outside ~/).
#
# Usage:
#   ./scripts/jtd2jar.sh <schema.jtd.json> [options...]
#
# Example:
#   ./scripts/jtd2jar.sh .tmp/test.jtd.json --output .tmp/test.jar --main

set -euo pipefail

WORK_DIR="$HOME/tmp/jtd2jar-work"
IMAGE="${JDT2JAR_IMAGE:-jtd2jar:latest}"

mkdir -p "$WORK_DIR"

# Sync source (respecting .gitignore)
rsync -a --filter=':- .gitignore' --exclude='.git/' . "$WORK_DIR/"

# Run jtd2jar in container
docker run --rm -v "$WORK_DIR:/work" "$IMAGE" "$@"

# Sync output back (any new .jar or .java files in the work dir)
rsync -a --include='*.jar' --include='*.java' --exclude='*' "$WORK_DIR/" ./
