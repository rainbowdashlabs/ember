#!/usr/bin/env bash
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/helpers.sh"

# A stand-in for Playwright: the first run records the stories in $FAKE/first as failed and fails
# when there are any, or when $FAKE/run-broken exists; a rerun fails when $FAKE/still-broken exists.
# Every run logs its arguments and whether it ran as the rerun.
fake=$(mktemp -d)
TEST_SCRATCH_DIRS+=("$fake")
cat > "$fake/playwright" <<'EOF'
#!/usr/bin/env bash
echo "${E2E_RERUN:-first}: $*" >> "$FAKE/calls"
if [ -n "${E2E_RERUN:-}" ]; then
    [ -f "$FAKE/still-broken" ] && exit 1
    exit 0
fi
ids=$(sed 's/.*/"&"/' "$FAKE/first" | paste -sd, -)
printf '{"status":"failed","failedTests":[%s]}\n' "$ids" > "$FAKE/last-run.json"
[ -f "$FAKE/run-broken" ] && exit 1
[ -s "$FAKE/first" ] && exit 1
exit 0
EOF
chmod +x "$fake/playwright"

group() {
    rm -f "$fake/calls"
    env -u GITHUB_ACTIONS -u E2E_RERUN FAKE="$fake" PLAYWRIGHT="$fake/playwright" \
        LAST_RUN="$fake/last-run.json" MAX_RERUN=2 \
        bash "$SCRIPTS/run-e2e-group.sh" e2e/inventory --project chromium
}

: > "$fake/first"
expect_pass "a group that passes at once" group
expect_equal "a passing group runs once" "1" "$(wc -l < "$fake/calls" | tr -d ' ')"

echo story-a > "$fake/first"
expect_pass "a story that passes on the rerun keeps the group green" group
expect_equal "a story that passes on the rerun is reported" \
    "warning: 1 stories passed only on a rerun with a single worker; the first run's log names them" \
    "$(group 2>&1 | grep '^warning:')"
expect_equal "only the failed stories are rerun, with one worker" \
    "1: test e2e/inventory --project chromium --last-failed --workers 1" "$(sed -n 2p "$fake/calls")"

touch "$fake/still-broken"
expect_fail "a story that fails again fails the group" "Rerunning 1 failed end-to-end stories" group
rm "$fake/still-broken"

printf 'story-a\nstory-b\nstory-c\n' > "$fake/first"
expect_fail "too many failed stories are not rerun" "3 end-to-end stories failed" group

: > "$fake/first"
touch "$fake/run-broken"
expect_fail "a run that fails without a failed story is not rerun" "nothing to rerun" group
rm "$fake/run-broken"

finish
