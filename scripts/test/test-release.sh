#!/usr/bin/env bash
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/helpers.sh"

fake_bin=$(mktemp -d)
TEST_SCRATCH_DIRS+=("$fake_bin")
export GH_LOG="$fake_bin/gh.log"
cat > "$fake_bin/gh" << 'EOF'
#!/usr/bin/env bash
echo "$*" >> "$GH_LOG"
case "$1 $2" in
    "pr view") printf '%s\n' "$FAKE_PR" ;;
    "run list") printf '%s\n' "${FAKE_RUN:-}" ;;
    "release create")
        while [ $# -gt 0 ]; do [ "$1" = "--notes-file" ] && cp "$2" "$GH_LOG.notes"; shift; done ;;
esac
EOF
chmod +x "$fake_bin/gh"
export PATH="$fake_bin:$PATH"

release() {
    bash "$SCRIPTS/release.sh" "$@"
}

pull_request() {
    printf '%s\t%s\t%s\t%s\t%s' "${2:-OPEN}" "${3:-main}" "$1" "$(git rev-parse HEAD)" false
}

new_repository
export FAKE_RUN="completed success https://ci/1 1"

git checkout -q -b release/v26.21.0
set_version 26.21.0
printf '# Changelog\n\n## v26.21.0\n\nThe focus.\n\n### New Features\n\n- **New.** It is new.\n\n## v26.20.0\n\nFirst release.\n' > CHANGELOG.md
commit_all "Open the release"
git push -q origin release/v26.21.0
FAKE_PR=$(pull_request release/v26.21.0)
export FAKE_PR

expect_pass "a release pull request that is ready" release check 1
expect_fail "a release pull request into another branch" "goes into main, not into develop" \
    env FAKE_PR="$(pull_request release/v26.21.0 OPEN develop)" bash "$SCRIPTS/release.sh" check 1
expect_fail "a pull request that is no release branch" "named release/vX.Y.Z, not from fix/thing" \
    env FAKE_PR="$(pull_request fix/thing)" bash "$SCRIPTS/release.sh" check 1
expect_fail "a closed release pull request" "is closed, not open" \
    env FAKE_PR="$(pull_request release/v26.21.0 CLOSED)" bash "$SCRIPTS/release.sh" check 1
expect_fail "a release while CI still runs" "still running" env FAKE_RUN="in_progress  https://ci/2 2" \
    bash "$SCRIPTS/release.sh" check 1
expect_fail "a release whose CI failed" "CI failed" env FAKE_RUN="completed failure https://ci/3 3" \
    bash "$SCRIPTS/release.sh" check 1
expect_fail "a release without a CI run" "CI has not run" env FAKE_RUN="" bash "$SCRIPTS/release.sh" check 1

git checkout -q main
git commit -q --allow-empty -m "Fix on main"
git push -q origin main
git checkout -q release/v26.21.0
expect_fail "a release branch behind main" "cannot be fast-forwarded" release check 1
git rebase -q origin/main
git push -q -f origin release/v26.21.0
FAKE_PR=$(pull_request release/v26.21.0)

expect_pass "a dry run changes nothing" release feature 1 --dry-run
expect_equal "main stays where it was" "$(git rev-parse main)" "$(git ls-remote origin refs/heads/main | cut -f1)"

expect_pass "a feature release" release feature 1
expect_equal "main moves to the release branch" "$(git rev-parse HEAD)" "$(git ls-remote origin refs/heads/main | cut -f1)"
expect_equal "the tag points at the release branch" "$(git rev-parse HEAD)" \
    "$(git ls-remote origin 'refs/tags/v26.21.0^{}' | cut -f1)"
expect_equal "the release notes are the changelog block" \
    "$(printf 'The focus.\n\n# New Features\n\n- **New.** It is new.')" "$(cat "$GH_LOG.notes")"
expect_fail "the same release again" "already released as v26.21.0" release check 1

git checkout -q -b fix/thing release/v26.21.0
set_version 26.21.1
printf '# Changelog\n\n## v26.21.1\n\n### Fixes\n\n- **Fixed.** It works.\n\n## v26.21.0\n\nThe focus.\n' > CHANGELOG.md
commit_all "Fix"
git push -q origin fix/thing:main
expect_pass "a fix release of main" release fix --wait
expect_equal "the fix tag points at main" "$(git rev-parse HEAD)" \
    "$(git ls-remote origin 'refs/tags/v26.21.1^{}' | cut -f1)"
expect_fail "a fix release of a released version" "already released as v26.21.1" release fix

git commit -q --allow-empty -m "Not on main"
expect_fail "a fix release of a commit not on main" "is not on main" release fix --commit HEAD

finish
