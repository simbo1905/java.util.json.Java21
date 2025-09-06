#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
JAVA_BIN="java"

if command -v java25 >/dev/null 2>&1; then
  JAVA_BIN="java25"
fi

echo "Using Java: $($JAVA_BIN -version 2>&1 | head -n1)" >&2

exec "$JAVA_BIN" "$HERE/RefreshFromUpstream.java"

