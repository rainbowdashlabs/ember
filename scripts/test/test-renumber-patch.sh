#!/usr/bin/env bash
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/helpers.sh"

renumber() {
    bash "$SCRIPTS/renumber-patch.sh" "$@"
}

new_repository
git fetch -q origin
dir=src/main/resources/database/postgresql/1

git checkout -q -b release/v26.21.0
add_patch 3 "create table c ();"
printf 'class T { String PATCH = "database/postgresql/1/patch_3.sql"; String OTHER = "patch_13.sql"; }\n' \
    > src/test/java/T.java
commit_all "Feature with schema"

expect_fail "a patch that does not exist" "there is no $dir/patch_9.sql" renumber 9 10
expect_fail "a target that exists" "$dir/patch_2.sql exists already" renumber 3 2
expect_fail "a patch that is on main" "patch_2 is on main as it is here" renumber 2 5
expect_fail "something that is no number" "whole numbers" renumber three 4

expect_pass "a dry run" renumber --dry-run 3 4
expect_equal "a dry run moves nothing" "1.3" "$(cat src/main/resources/database/version)"

expect_pass "the branch's own patch" renumber 3 4
expect_equal "the patch moves" "create table c ();" "$(cat $dir/patch_4.sql)"
expect_equal "the old file is gone" "no" "$([ -e $dir/patch_3.sql ] && echo yes || echo no)"
expect_equal "the version file follows" "1.4" "$(cat src/main/resources/database/version)"
expect_equal "tests naming the patch follow, others stay" \
    'class T { String PATCH = "database/postgresql/1/patch_4.sql"; String OTHER = "patch_13.sql"; }' \
    "$(cat src/test/java/T.java)"

git checkout -q -f main
git checkout -q -b fix/changed
printf 'create table changed ();\n' > $dir/patch_2.sql
commit_all "Change a patch of main"
expect_fail "a patch of main changed on the branch" "was on main before this branch left it" renumber 2 5

finish
