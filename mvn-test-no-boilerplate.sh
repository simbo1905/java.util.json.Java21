#!/bin/sh
#
# Wrapper that trims Maven boilerplate output. Supports a preset to run the
# strict JsonSchemaCheckIT with cleaning, metrics validation, and debugging.

set -eu

DEBUG=0
if [ "${1-}" = "--debug" ]; then
  DEBUG=1
  shift
fi

if [ "$DEBUG" -eq 1 ]; then
  set -x
fi

if command -v mvnd >/dev/null 2>&1; then
  MVN_CMD="mvnd"
else
  MVN_CMD="mvn"
fi

PRESET=""
DO_CLEAN=0
QUIET_FLAG=""

if [ "${1-}" = "run_schema_check_with_clean" ]; then
  PRESET="run_schema_check_with_clean"
  shift
  if [ "$#" -gt 0 ]; then
    echo "[INFO] Preset ignoring additional arguments: $*"
  fi
  DO_CLEAN=1
  QUIET_FLAG=""
  set -- \
    generate-test-resources \
    failsafe:integration-test \
    failsafe:verify \
    -pl json-java21-schema \
    -DskipTests=true \
    -DskipITs=false \
    -Dit.test=JsonSchemaCheckIT \
    -Djson.schema.strict=true \
    -Djson.schema.metrics=csv \
    -Djava.util.logging.ConsoleHandler.level=INFO \
    -DfailIfNoTests=false \
    -e \
    -DtrimStackTrace=false \
    -Dorg.slf4j.simpleLogger.log.org.apache.maven.plugins.failsafe=debug
fi

if [ "$DEBUG" -eq 1 ]; then
  QUIET_FLAG=""
fi

TIMEOUT_SECS=600
if ! command -v timeout >/dev/null 2>&1; then
  TIMEOUT_SECS=0
fi

TMP_FILES=""
cleanup() {
  # shellcheck disable=SC2317
  for file in $TMP_FILES; do
    if [ -f "$file" ]; then
      rm -f "$file"
    fi
  done
}
trap cleanup EXIT INT TERM

filter_output() {
  awk '
BEGIN {
  scanning_started = 0
  compilation_section = 0
  test_section = 0
}
/INFO.*Scanning for projects/ {
  scanning_started = 1
  print
  next
}
!scanning_started && /^WARNING:/ { next }
/COMPILATION ERROR/ {
  compilation_section = 1
}
/BUILD FAILURE/ && compilation_section {
  compilation_section = 0
}
/INFO.*T E S T S/ {
  test_section = 1
  print "-------------------------------------------------------"
  print " T E S T S"
  print "-------------------------------------------------------"
  next
}
compilation_section { print }
test_section { print }
!test_section && scanning_started {
  if ($0 ~ /INFO.*Scanning/ || $0 ~ /INFO.*Building/ || $0 ~ /INFO.*resources/ || $0 ~ /INFO.*compiler/ || $0 ~ /INFO.*surefire/ || $0 ~ /INFO.*failsafe/ || $0 ~ /ERROR/ || $0 ~ /FAILURE/) {
    print
  }
  if ($0 ~ /WARNING.*COMPILATION/ || $0 ~ /ERROR.*/) {
    print
  }
}
' "$1"
}

run_maven_command() {
  CMD_LOG=$(mktemp -t mvn-wrapper-cmd.XXXXXX)
  TMP_FILES="$TMP_FILES $CMD_LOG"

  if [ -n "$QUIET_FLAG" ]; then
    DISPLAY_CMD="$MVN_CMD $QUIET_FLAG $*"
  else
    DISPLAY_CMD="$MVN_CMD $*"
  fi
  echo "[INFO] Executing: $DISPLAY_CMD"

  if [ "$TIMEOUT_SECS" -gt 0 ]; then
    if [ -n "$QUIET_FLAG" ]; then
      if timeout "$TIMEOUT_SECS" "$MVN_CMD" "$QUIET_FLAG" "$@" >"$CMD_LOG" 2>&1; then
        status=0
      else
        status=$?
      fi
    else
      if timeout "$TIMEOUT_SECS" "$MVN_CMD" "$@" >"$CMD_LOG" 2>&1; then
        status=0
      else
        status=$?
      fi
    fi
  else
    if [ -n "$QUIET_FLAG" ]; then
      if "$MVN_CMD" "$QUIET_FLAG" "$@" >"$CMD_LOG" 2>&1; then
        status=0
      else
        status=$?
      fi
    else
      if "$MVN_CMD" "$@" >"$CMD_LOG" 2>&1; then
        status=0
      else
        status=$?
      fi
    fi
  fi

  if [ $status -eq 0 ]; then
    if [ "$DEBUG" -eq 1 ]; then
      cat "$CMD_LOG"
    else
      filter_output "$CMD_LOG"
    fi
    rm -f "$CMD_LOG"
    TMP_FILES=$(printf '%s\n' "$TMP_FILES" | tr ' ' '\n' | grep -v "$CMD_LOG" || true)
  else
    echo "[ERROR] Maven command failed (exit $status). Command output follows:"
    cat "$CMD_LOG"
  fi

  return $status
}

if [ $DO_CLEAN -eq 1 ]; then
  if ! run_maven_command clean; then
    echo "[ERROR] Clean phase failed."
    exit 1
  fi
fi

if ! run_maven_command "$@"; then
  echo "[ERROR] Maven execution failed."
  exit 1
fi

STATUS=0
if [ "$PRESET" = "run_schema_check_with_clean" ]; then
  CSV_PATH="json-java21-schema/target/json-schema-compat.csv"
  if [ ! -f "$CSV_PATH" ]; then
    echo "[ERROR] Expected metrics CSV not found at: $CSV_PATH"
    STATUS=2
  else
    echo "[INFO] Metrics CSV located at: $CSV_PATH"
  fi
fi

if [ $STATUS -eq 0 ]; then
  echo DONE
else
  echo FAILED
fi

exit $STATUS
