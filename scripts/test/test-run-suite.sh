#!/usr/bin/env bash
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/helpers.sh"

# A stand-in for Gradle: the first run writes the results named in $FAKE/first and fails when one of
# them failed; a rerun with a --tests filter fails only for the classes listed in $FAKE/broken.
# Every run adds its own line to the suite's coverage file and logs its arguments.
fake=$(mktemp -d)
TEST_SCRATCH_DIRS+=("$fake")
cat > "$fake/gradlew" <<'EOF'
#!/usr/bin/env bash
suite="$1"; shift
echo "$*" >> "$FAKE/calls"
results="build/test-results/$suite"
mkdir -p "$results" build/jacoco
classes=() status=0
while [ $# -gt 0 ]; do
    [ "$1" = --tests ] && { classes+=("$2"); shift; }
    shift
done
if [ ${#classes[@]} -eq 0 ]; then
    echo "first" >> "build/jacoco/$suite.exec"
    while read -r class outcome; do
        failures=0
        [ "$outcome" = fail ] && { failures=1; status=1; }
        printf '<testsuite name="%s" tests="1" failures="%s" errors="0">\n' "$class" "$failures" > "$results/TEST-$class.xml"
    done < "$FAKE/first"
    [ -f "$FAKE/build-broken" ] && status=1
    exit "$status"
fi
echo "rerun" >> "build/jacoco/$suite.exec"
for class in "${classes[@]}"; do
    grep -qx "$class" "$FAKE/broken" 2>/dev/null && status=1
done
exit "$status"
EOF
chmod +x "$fake/gradlew"

suite() {
    rm -rf "$fake/work" "$fake/calls"
    mkdir -p "$fake/work"
    (cd "$fake/work" && env -u GITHUB_ACTIONS FAKE="$fake" GRADLEW="$fake/gradlew" MAX_RERUN=2 \
        bash "$SCRIPTS/run-suite.sh" testServices --build-cache)
}

printf 'a.ATest pass\nb.BTest pass\n' > "$fake/first"
: > "$fake/broken"
expect_pass "a suite that passes at once" suite
expect_equal "a passing suite runs once" "1" "$(wc -l < "$fake/calls" | tr -d ' ')"

printf 'a.ATest fail\nb.BTest pass\n' > "$fake/first"
expect_pass "a class that passes on the rerun keeps the suite green" suite
expect_equal "a class that passes on the rerun is reported" \
    "warning: Passed only on a rerun with a single fork: a.ATest" "$(suite 2>&1 | grep '^warning:')"
expect_equal "only the failed class is rerun, on one fork" \
    "--tests a.ATest --build-cache -PtestForks=1" "$(sed -n 2p "$fake/calls")"
expect_equal "the coverage of both runs is kept" "first
rerun" "$(cat "$fake/work/build/jacoco/testServices.exec")"

echo a.ATest > "$fake/broken"
expect_fail "a class that fails again fails the suite" "Rerunning 1 failed test classes" suite

printf 'a.ATest fail\nb.BTest fail\nc.CTest fail\n' > "$fake/first"
expect_fail "too many failed classes are not rerun" "3 test classes of testServices failed" suite

printf 'a.ATest pass\n' > "$fake/first"
touch "$fake/build-broken"
expect_fail "a build that fails without a failed test is not rerun" "nothing to rerun" suite
rm "$fake/build-broken"

finish
