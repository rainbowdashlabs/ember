#!/usr/bin/env bash
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/helpers.sh"

check() {
    env "$@" bash "$SCRIPTS/check-migration-order.sh"
}

new_repository
git fetch -q origin

expect_pass "a branch that is no release branch" check EVENT_NAME=push REF_NAME=fix/thing
expect_pass "a release branch without a patch of its own" check EVENT_NAME=push REF_NAME=release/v26.21.0

git checkout -q -b release/v26.21.0
add_patch 3 "create table c ();"
commit_all "Feature with schema"
expect_pass "a release branch with its patch above main's" check EVENT_NAME=push REF_NAME=release/v26.21.0
expect_pass "a pull request into the release branch" \
    check EVENT_NAME=pull_request BASE_REF=release/v26.21.0 HEAD_REF=feature/thing
expect_pass "the release pull request into main" \
    check EVENT_NAME=pull_request BASE_REF=main HEAD_REF=release/v26.21.0

add_patch 4 "create table d ();"
commit_all "Second feature with schema"
expect_fail "a release branch with two patches of its own" "carries 2 patches of its own \(3 4\)" \
    check EVENT_NAME=push REF_NAME=release/v26.21.0
git reset -q --hard HEAD~1

git checkout -q main
add_patch 3 "create table hotfix ();"
commit_all "Fix with schema"
git push -q origin main
git checkout -q release/v26.21.0
git fetch -q origin
expect_fail "a release branch whose patch collides with main's" \
    "main has src/main/resources/database/postgresql/1/patch_3.sql, and this branch carries it differently" \
    check EVENT_NAME=push REF_NAME=release/v26.21.0

git reset -q --hard origin/main
printf 'create table changed ();\n' > src/main/resources/database/postgresql/1/patch_2.sql
commit_all "Change a patch of main"
expect_fail "a release branch that changed one of main's patches" \
    "main has src/main/resources/database/postgresql/1/patch_2.sql, and this branch carries it differently" \
    check EVENT_NAME=push REF_NAME=release/v26.21.0

git reset -q --hard origin/main
add_patch 4 "create table c ();"
commit_all "Renumbered"
expect_pass "a release branch rebased and renumbered" check EVENT_NAME=push REF_NAME=release/v26.21.0

finish
