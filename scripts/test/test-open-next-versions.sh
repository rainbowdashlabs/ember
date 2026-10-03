#!/usr/bin/env bash
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/helpers.sh"

fake_bin=$(mktemp -d)
TEST_SCRATCH_DIRS+=("$fake_bin")
export GH_LOG="$fake_bin/gh.log"
touch "$GH_LOG"
cat > "$fake_bin/gh" << 'EOF'
#!/usr/bin/env bash
echo "$*" >> "$GH_LOG"
if [ "$1 $2" = "pr list" ]; then
    while [ $# -gt 0 ]; do
        if [ "$1" = "--head" ] && [[ " ${FAKE_OPEN_HEADS:-} " == *" $2 "* ]]; then echo 7; fi
        shift
    done
fi
EOF
chmod +x "$fake_bin/gh"
export PATH="$fake_bin:$PATH"

next() {
    bash "$SCRIPTS/open-next-versions.sh" "$@"
}

remote() {
    git ls-remote origin "refs/heads/$1" | cut -f1
}

remote_version() {
    git fetch -q origin "+refs/heads/$1:refs/remotes/origin/$1"
    git show "origin/$1:build.gradle.kts" | sed -n 's/^version = "\(.*\)"$/\1/p'
}

created() {
    grep -c '^pr create' "$GH_LOG"
}

new_repository
released=$(git rev-parse HEAD)

expect_pass "a dry run after a feature release" next --dry-run
expect_equal "a dry run pushes no bump" "" "$(remote chore/bump-26.20.1)"
expect_equal "a dry run opens no release branch" "" "$(remote release/v26.21.0)"
expect_equal "a dry run opens no pull request" "0" "$(created)"

expect_pass "the next versions after a feature release" next
expect_equal "main's bump carries the next patch" "26.20.1" "$(remote_version chore/bump-26.20.1)"
expect_equal "main's bump is one commit on main" "$released" "$(git rev-parse origin/chore/bump-26.20.1^)"
expect_equal "main's bump has its message" "Bump version to 26.20.1" "$(git log -1 --format=%s origin/chore/bump-26.20.1)"
expect_equal "main's bump is a pull request into main" "yes" \
    "$(grep -q -- '^pr create --base main --head chore/bump-26.20.1 --title Bump version to 26.20.1' "$GH_LOG" && echo yes)"
expect_equal "the next release branch starts at the release" "$released" "$(remote release/v26.21.0)"
expect_equal "the release branch's bump carries the next minor" "26.21.0" "$(remote_version chore/bump-26.21.0)"
expect_equal "the release branch's bump is one commit on the release" "$released" \
    "$(git rev-parse origin/chore/bump-26.21.0^)"
expect_equal "the release branch's bump is a pull request into it" "yes" \
    "$(grep -q -- '^pr create --base release/v26.21.0 --head chore/bump-26.21.0 --title Bump version to 26.21.0' "$GH_LOG" && echo yes)"
expect_equal "two pull requests are opened" "2" "$(created)"
expect_equal "nothing is merged into main" "$released" "$(remote main)"

expect_pass "a second run while both pull requests are open" \
    env FAKE_OPEN_HEADS="chore/bump-26.20.1 chore/bump-26.21.0" bash "$SCRIPTS/open-next-versions.sh"
expect_equal "no pull request is opened twice" "2" "$(created)"
expect_equal "the release branch stays where it was" "$released" "$(remote release/v26.21.0)"

git push -q origin origin/chore/bump-26.20.1:refs/heads/main origin/chore/bump-26.21.0:refs/heads/release/v26.21.0
expect_pass "a run once both bumps are merged" next
expect_equal "nothing more is opened" "2" "$(created)"

git fetch -q origin
git checkout -q -B main origin/main
git tag -a v26.20.1 -m v26.20.1
git push -q origin v26.20.1
expect_pass "the next versions after a fix release" next
expect_equal "main's bump carries the patch after the fix" "26.20.2" "$(remote_version chore/bump-26.20.2)"
expect_equal "only main's bump is opened after a fix release" "3" "$(created)"
expect_equal "the release branch is left alone after a fix release" "26.21.0" "$(remote_version release/v26.21.0)"

git push -q -f origin origin/release/v26.21.0:refs/heads/main
git fetch -q origin
git tag -a v26.21.0 -m v26.21.0 origin/main
git push -q origin v26.21.0
git push -q origin origin/main:refs/heads/release/v27.0.0
expect_pass "a feature release while a later release branch is open" next
expect_equal "no release branch for the next minor" "" "$(remote release/v26.22.0)"
expect_equal "only main's bump is opened then" "4" "$(created)"
expect_equal "main's bump follows the feature release" "26.21.1" "$(remote_version chore/bump-26.21.1)"

git push -q origin --delete release/v27.0.0
git push -q origin origin/main:refs/heads/release/v26.22.0
expect_pass "a release branch opened without its bump" \
    env FAKE_OPEN_HEADS="chore/bump-26.21.1" bash "$SCRIPTS/open-next-versions.sh"
expect_equal "its bump pull request is opened" "yes" \
    "$(grep -q -- '^pr create --base release/v26.22.0 --head chore/bump-26.22.0' "$GH_LOG" && echo yes)"
expect_equal "main's open bump is not opened again" "5" "$(created)"

finish
