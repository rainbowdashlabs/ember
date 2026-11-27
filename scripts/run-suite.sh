#!/usr/bin/env bash
# Runs one backend test suite in full, and gives the test classes that failed a second chance on a
# single fork before the suite counts as failed.
#
# Many forks at once each start their own database container, and a test that loses a race for a
# port or a slow container fails without anything being wrong with the code. Rerun alone, such a
# class passes, so the job stays green and is not started again from scratch. A class that fails a
# second time fails the suite. Every class that needed the rerun is listed as a warning, so a test
# that keeps flaking stays visible. When more than MAX_RERUN classes (10) fail, or the build failed
# without a failed test, nothing is rerun: that is a real breakage, not a flake.
#
# The coverage data of both runs is joined into the one file of the suite, so the coverage gates see
# every class the suite ran. Takes the suite name and any further Gradle arguments; reads GRADLEW
# (./gradlew) and MAX_RERUN.
set -uo pipefail

suite="${1:?the suite to run, e.g. testServices}"
shift
gradlew="${GRADLEW:-./gradlew}"
max_rerun="${MAX_RERUN:-10}"
results="build/test-results/$suite"
coverage="build/jacoco/$suite.exec"

# Prints the name of every test class whose results file in the given directory records a failure
# or an error.
failed_classes() {
    grep -lE '<testsuite [^>]*(failures|errors)="[1-9]' "$1"/TEST-*.xml 2>/dev/null |
        xargs -r sed -nE 's/.*<testsuite name="([^"]+)".*/\1/p' | sort -u
}

# Writes a warning the way the CI run shows it, or plainly elsewhere.
warn() {
    if [ -n "${GITHUB_ACTIONS:-}" ]; then
        echo "::warning title=Flaky tests in $suite::$1"
    else
        echo "warning: $1" >&2
    fi
}

"$gradlew" "$suite" "$@" && exit 0

mapfile -t failed < <(failed_classes "$results")
if [ ${#failed[@]} -eq 0 ]; then
    echo "$suite failed without a failed test class, so there is nothing to rerun." >&2
    exit 1
fi
if [ ${#failed[@]} -gt "$max_rerun" ]; then
    echo "${#failed[@]} test classes of $suite failed, more than the $max_rerun that are rerun." >&2
    exit 1
fi

echo "Rerunning ${#failed[@]} failed test classes of $suite on a single fork: ${failed[*]}"
first_coverage=""
if [ -f "$coverage" ]; then
    first_coverage="$coverage.first"
    mv "$coverage" "$first_coverage"
fi

filters=()
for class in "${failed[@]}"; do filters+=(--tests "$class"); done
"$gradlew" "$suite" "${filters[@]}" "$@" -PtestForks=1
status=$?

if [ -n "$first_coverage" ]; then
    cat "$first_coverage" "$coverage" > "$coverage.joined" 2>/dev/null || cp "$first_coverage" "$coverage.joined"
    mv "$coverage.joined" "$coverage"
    rm -f "$first_coverage"
fi

if [ "$status" -eq 0 ]; then
    warn "Passed only on a rerun with a single fork: ${failed[*]}"
fi
exit "$status"
