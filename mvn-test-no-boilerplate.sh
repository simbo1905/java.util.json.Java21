#!/bin/bash

# Strip Maven test boilerplate - show compile errors and test results only
# Usage: ./mvn-test-no-boilerplate.sh [maven test arguments]
# 
# Examples:
#   ./mvn-test-no-boilerplate.sh -Dtest=RefactorTests
#   ./mvn-test-no-boilerplate.sh -Dtest=RefactorTests#testList -Djava.util.logging.ConsoleHandler.level=INFO
#   ./mvn-test-no-boilerplate.sh -Dtest=RefactorTests#testList -Djava.util.logging.ConsoleHandler.level=FINER
#
# For running tests in a specific module:
#   ./mvn-test-no-boilerplate.sh -pl json-java21-api-tracker -Dtest=CompilerApiLearningTest
#
# The script automatically detects if mvnd is available, otherwise falls back to mvn
# Presets: pass a first arg token to run a curated flow. Currently supported:
#   run_schema_check_with_clean
#     - Cleans and then verifies only the json-java21-schema IT
#     - Runs JsonSchemaCheckIT in strict mode with CSV metrics
#     - Uses mvnd -q when available, prints DONE/FAILED based on exit status

# Detect if mvnd is available, otherwise use mvn
if command -v mvnd &> /dev/null; then
    MVN_CMD="mvnd"
else
    MVN_CMD="mvn"
fi

PRESET="$1"
QUIET_FLAG=""
DO_CLEAN=false
MVN_ARGS=""

if [ "$PRESET" = "run_schema_check_with_clean" ]; then
  DO_CLEAN=true
  QUIET_FLAG="-q"
  # Ignore all user-supplied args for preset; use curated defaults
  echo "[INFO] Preset detected: run_schema_check_with_clean"
  echo "[INFO] Ignoring passed parameters; running curated schema IT flow"
  MVN_ARGS="-pl json-java21-schema -Dit.test=JsonSchemaCheckIT -Djson.schema.strict=true -Djson.schema.metrics=csv -Djava.util.logging.ConsoleHandler.level=INFO -DfailIfNoTests=false -e -DtrimStackTrace=false -Dorg.slf4j.simpleLogger.log.org.apache.maven.plugins.failsafe=debug"
  shift # consume preset token
else
  # No preset: forward all args as-is
  MVN_ARGS="$@"
fi

echo "[INFO] Running: $MVN_CMD ${QUIET_FLAG} generate-test-resources failsafe:integration-test failsafe:verify ${MVN_ARGS}"

set -o pipefail

if $DO_CLEAN; then
  timeout 120 $MVN_CMD ${QUIET_FLAG} clean || exit $?
fi

timeout 120 $MVN_CMD ${QUIET_FLAG} generate-test-resources failsafe:integration-test failsafe:verify ${MVN_ARGS} 2>&1 | awk '
BEGIN { 
    scanning_started = 0
    compilation_section = 0
    test_section = 0
}

# Skip all WARNING lines before project scanning starts
/INFO.*Scanning for projects/ { 
    scanning_started = 1 
    print
    next
}

# Before scanning starts, skip WARNING lines
!scanning_started && /^WARNING:/ { next }

# Show compilation errors
/COMPILATION ERROR/ { compilation_section = 1 }
/BUILD FAILURE/ && compilation_section { compilation_section = 0 }

# Show test section
/INFO.*T E S T S/ { 
    test_section = 1
    print "-------------------------------------------------------"
    print " T E S T S"
    print "-------------------------------------------------------"
    next
}

# In compilation error section, show everything
compilation_section { print }

# In test section, show everything - let user control logging with -D arguments
test_section {
    print
}

# Before test section starts, show important lines only
!test_section && scanning_started {
    if (/INFO.*Scanning|INFO.*Building|INFO.*resources|INFO.*compiler|INFO.*surefire|INFO.*failsafe|ERROR|FAILURE/) {
        print
    }
    # Show compilation warnings/errors
    if (/WARNING.*COMPILATION|ERROR.*/) {
        print
    }
}
'

STATUS=${PIPESTATUS[0]}
if [ $STATUS -eq 0 ]; then
  echo DONE
else
  echo FAILED
fi
exit $STATUS
