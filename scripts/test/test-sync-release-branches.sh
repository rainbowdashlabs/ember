#!/usr/bin/env bash
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/helpers.sh"

fake_bin=$(mktemp -d)
TEST_SCRATCH_DIRS+=("$fake_bin")
export GH_LOG="$fake_bin/gh.log"
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
touch "$GH_LOG"

drafts() {
    grep -c '^pr create --draft --base main --head release/v26.21.0 --title Release v26.21.0' "$GH_LOG"
}
chmod +x "$fake_bin/gh"
export PATH="$fake_bin:$PATH"

sync() {
    bash "$SCRIPTS/sync-release-branches.sh" "$@"
}

changelog() {
    printf '# Changelog\n\n'
    for block in "$@"; do printf '%b\n' "$block"; done
}

dir=src/main/resources/database/postgresql/1
released='## v26.20.0\n\nFirst release.\n\n### Fixes\n\n- **A fix.** It works.\n'
feature='## v26.21.0\n\nThe focus.\n\n### New Features\n\n- **New.** It is new.\n'
fix='## v26.20.1\n\n### Fixes\n\n- **Fixed.** It works again.\n'

new_repository
printf 'shared line\n' > Code.java
commit_all "Add code"
git push -q origin main

git checkout -q -b release/v26.21.0
set_version 26.21.0
changelog "$feature" "$released" > CHANGELOG.md
commit_all "Open the release"
add_patch 3 "create table feature ();"
printf 'class T { String P = "patch_3.sql"; }\n' > src/test/java/T.java
commit_all "Feature with schema"
add_patch 3 "create table feature (id int);"
commit_all "Second feature edits the release patch"
git push -q origin release/v26.21.0
release_head=$(git rev-parse HEAD)

git checkout -q main
set_version 26.20.1
add_patch 3 "create table hotfix ();"
changelog "$fix" "$released" > CHANGELOG.md
commit_all "Fix with schema"
git push -q origin main

expect_pass "a dry run" sync --dry-run
expect_equal "a dry run pushes nothing" "$release_head" "$(git ls-remote origin refs/heads/release/v26.21.0 | cut -f1)"
expect_equal "a dry run opens no release pull request" "0" "$(drafts)"

expect_pass "the release branch is rebased" sync
expect_equal "the missing release pull request is opened as a draft" "1" "$(drafts)"
git fetch -q origin
expect_equal "main is in the release branch" "yes" \
    "$(git merge-base --is-ancestor origin/main origin/release/v26.21.0 && echo yes)"
expect_equal "main's patch stays" "create table hotfix ();" "$(git show origin/release/v26.21.0:$dir/patch_3.sql)"
expect_equal "the branch's patch moves above it" "create table feature (id int);" \
    "$(git show origin/release/v26.21.0:$dir/patch_4.sql)"
expect_equal "the version file names the branch's patch" "1.4" \
    "$(git show origin/release/v26.21.0:src/main/resources/database/version)"
expect_equal "the test follows the patch" 'class T { String P = "patch_4.sql"; }' \
    "$(git show origin/release/v26.21.0:src/test/java/T.java)"
expect_equal "the release branch keeps its version" 'version = "26.21.0"' \
    "$(git show origin/release/v26.21.0:build.gradle.kts | grep '^version')"
expect_equal "the changelog keeps both blocks, the release's on top" "$(changelog "$feature" "$fix" "$released")" \
    "$(git show origin/release/v26.21.0:CHANGELOG.md)"
expect_equal "every commit of the branch is kept" "3" "$(git rev-list --count origin/main..origin/release/v26.21.0)"
expect_pass "a second run has nothing to do" env FAKE_OPEN_HEADS=release/v26.21.0 bash "$SCRIPTS/sync-release-branches.sh"
expect_equal "an open release pull request is not opened again" "1" "$(drafts)"

git checkout -q -B release/v26.21.0 origin/release/v26.21.0
printf 'release line\n' > Code.java
commit_all "Change code on the release branch"
git push -q origin release/v26.21.0
release_head=$(git rev-parse HEAD)
git checkout -q main
set_version 26.20.2
printf 'fix line\n' > Code.java
commit_all "Change the same code on main"
git push -q origin main

expect_fail "a conflict in code stops the rebase" "Could not rebase release/v26.21.0" sync
expect_equal "the stopped rebase names the file" "yes" "$(output=$(sync 2>&1); grep -qx -- '  - Code.java' <<< "$output" && echo yes)"
expect_equal "the branch is left as it was" "$release_head" "$(git ls-remote origin refs/heads/release/v26.21.0 | cut -f1)"

git push -q origin --delete release/v26.21.0
git checkout -q -B release/v26.20.0 v26.20.0
git commit -q --allow-empty -m "Left behind after the release"
git push -q origin release/v26.20.0
leftover_head=$(git rev-parse HEAD)
git checkout -q main
expect_pass "a released branch left over is skipped" sync
expect_equal "the released branch is left as it was" "$leftover_head" \
    "$(git ls-remote origin refs/heads/release/v26.20.0 | cut -f1)"

git push -q origin 'v26.20.0^{commit}:refs/heads/release/v26.21.0'
expect_equal "a release branch without a commit of its own waits for its bump" "yes" \
    "$(sync | grep -q 'release/v26.21.0 has no commit of its own yet' && echo yes)"
expect_equal "the release branch without a commit of its own is left as it was" "$(git rev-parse 'v26.20.0^{commit}')" \
    "$(git ls-remote origin refs/heads/release/v26.21.0 | cut -f1)"

git checkout -q -B release/v26.21.0 'v26.20.0^{commit}'
set_version 26.21.0
commit_all "Open the release"
git push -q origin release/v26.21.0
git checkout -q main
hook="$(git remote get-url origin)/hooks/pre-receive"
printf '#!/usr/bin/env bash\necho "- Cannot force-push to this branch"\nexit 1\n' > "$hook"
chmod +x "$hook"
expect_fail "a push refused by a repository rule names the rule" \
    "the push was refused: Cannot force-push to this branch" sync
rm "$hook"
release_head=$(git rev-parse release/v26.21.0)

pull_requests_before=$(grep -c '^pr ' "$GH_LOG")
expect_pass "a check of a branch behind main" sync --check
expect_equal "the check marks the branch head as failing" "yes" \
    "$(grep -q "^api --silent repos/{owner}/{repo}/statuses/$release_head -f state=failure -f context=Release branch contains main -f description=.*release-sync" "$GH_LOG" && echo yes)"
expect_equal "the check pushes nothing" "$release_head" "$(git ls-remote origin refs/heads/release/v26.21.0 | cut -f1)"
expect_equal "the check opens no pull request" "$pull_requests_before" "$(grep -c '^pr ' "$GH_LOG")"

key="$(mktemp -d)/key"
TEST_SCRATCH_DIRS+=("${key%/key}")
ssh-keygen -q -t ed25519 -N '' -f "$key"
git config gpg.format ssh
git config commit.gpgsign true
git config user.signingkey "$key.missing"
expect_fail "a signature that cannot be made stops the sync" "signing a commit failed" sync
expect_equal "the branch is left as it was without a signature" "$release_head" \
    "$(git ls-remote origin refs/heads/release/v26.21.0 | cut -f1)"

git config user.signingkey "$key"
printf 'uncommitted\n' > Code.java
expect_pass "the sync signs as configured" sync
git fetch -q origin
expect_equal "every rewritten commit is signed" "" \
    "$(for commit in $(git rev-list origin/main..origin/release/v26.21.0); do
        git cat-file commit "$commit" | grep -q '^gpgsig' || echo "$commit"; done)"
expect_equal "the committer is the one who ran it" "test" "$(git log -1 --format=%cn origin/release/v26.21.0)"
expect_equal "the checkout keeps its branch" "main" "$(git rev-parse --abbrev-ref HEAD)"
expect_equal "the checkout keeps its changes" "uncommitted" "$(cat Code.java)"
expect_equal "no scratch branch is left behind" "" "$(git for-each-ref refs/heads/release-sync/)"
git checkout -q -- Code.java

expect_pass "a check once the branch contains main" sync --check
expect_equal "the check marks the new branch head as passing" "yes" \
    "$(grep -q "^api --silent repos/{owner}/{repo}/statuses/$(git rev-parse origin/release/v26.21.0) -f state=success" "$GH_LOG" && echo yes)"
expect_equal "nothing touches an issue" "0" "$(grep -c '^issue' "$GH_LOG")"

finish
