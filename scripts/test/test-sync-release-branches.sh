#!/usr/bin/env bash
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/helpers.sh"

fake_bin=$(mktemp -d)
TEST_SCRATCH_DIRS+=("$fake_bin")
export GH_LOG="$fake_bin/gh.log"
cat > "$fake_bin/gh" << 'EOF'
#!/usr/bin/env bash
echo "$*" >> "$GH_LOG"
EOF
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

expect_pass "the release branch is rebased" sync
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
expect_pass "a second run has nothing to do" sync

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
expect_equal "the branch is left as it was" "$release_head" "$(git ls-remote origin refs/heads/release/v26.21.0 | cut -f1)"
expect_equal "an issue names the branch and the file" "yes" \
    "$(grep -q 'issue create --title Rebase of release/v26.21.0 onto main needs a hand' "$GH_LOG" &&
        grep -qx -- '- Code.java' "$GH_LOG" && echo yes)"

git push -q origin --delete release/v26.21.0
git checkout -q -B release/v26.20.0 v26.20.0
git commit -q --allow-empty -m "Left behind after the release"
git push -q origin release/v26.20.0
leftover_head=$(git rev-parse HEAD)
git checkout -q main
expect_pass "a released branch left over is skipped" sync
expect_equal "the released branch is left as it was" "$leftover_head" \
    "$(git ls-remote origin refs/heads/release/v26.20.0 | cut -f1)"

finish
