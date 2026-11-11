#!/usr/bin/env bash
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/helpers.sh"

# A stand-in for gh: the jobs come from $FAKE/jobs, and an artifact can be downloaded once its
# $FAKE/ready-<suite> file says it has been asked for that many times.
fake=$(mktemp -d)
TEST_SCRATCH_DIRS+=("$fake")
mkdir -p "$fake/bin"
cat > "$fake/bin/gh" <<'EOF'
#!/usr/bin/env bash
if [ "$1" = api ]; then
    cat "$FAKE/jobs"
    exit 0
fi
name="" dir=""
while [ $# -gt 0 ]; do
    case "$1" in
        --name) name="$2"; shift ;;
        --dir) dir="$2"; shift ;;
    esac
    shift
done
suite="${name#jacoco-}"
asked=$(( $(cat "$FAKE/asked-$suite" 2>/dev/null || echo 0) + 1 ))
echo "$asked" > "$FAKE/asked-$suite"
[ -f "$FAKE/ready-$suite" ] && [ "$asked" -ge "$(cat "$FAKE/ready-$suite")" ] || exit 1
touch "$dir/$suite.exec"
EOF
chmod +x "$fake/bin/gh"

fetch() {
    rm -rf "$fake/dest" "$fake"/asked-*
    env PATH="$fake/bin:$PATH" FAKE="$fake" RUN_ID=1 REPOSITORY=o/r DEST="$fake/dest" WAIT=0 ATTEMPTS=3 \
        bash "$SCRIPTS/fetch-coverage-data.sh"
}

printf 'testOther\ntestServices\n' > "$fake/jobs"
echo 1 > "$fake/ready-testOther"
echo 1 > "$fake/ready-testServices"
expect_pass "every suite's data at once" fetch

echo 3 > "$fake/ready-testOther"
expect_pass "a suite whose data is listed only on the third try" fetch

echo 9 > "$fake/ready-testOther"
expect_fail "a suite whose data never arrives" "No coverage data arrived for: testOther" fetch

: > "$fake/jobs"
expect_fail "a run without backend suites" "No backend test suite ran" fetch

finish
