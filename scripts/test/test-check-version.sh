#!/usr/bin/env bash
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/helpers.sh"

check() {
    env "$@" bash "$SCRIPTS/check-version.sh"
}

new_repository

expect_pass "a tag equal to the version" check REF_TYPE=tag REF_NAME=v26.20.0
expect_fail "a tag that differs from the version" "cut from a build that says 26.20.0" \
    check REF_TYPE=tag REF_NAME=v26.20.1
expect_pass "main at the commit of its release" check EVENT_NAME=push REF_NAME=main

git commit -q --allow-empty -m "Change without a bump"
expect_pass "main past its release without a bump, as dependency updates leave it" \
    check EVENT_NAME=push REF_NAME=main
expect_pass "a dependency update without a bump" check EVENT_NAME=push REF_NAME=renovate/thing
expect_pass "a dependency update pull request into main without a bump" \
    check EVENT_NAME=pull_request BASE_REF=main HEAD_REF=renovate/thing
expect_fail "a fix without a bump" "should be 26.20.1, one patch above the newest release v26.20.0" \
    check EVENT_NAME=push REF_NAME=fix/thing
expect_fail "a fix pull request into main without a bump" "should be 26.20.1" \
    check EVENT_NAME=pull_request BASE_REF=main HEAD_REF=fix/thing

set_version 26.20.1
commit_all "Bump"
expect_pass "a fix one patch above the release" check EVENT_NAME=push REF_NAME=fix/thing
expect_pass "a dependency update one patch above the release" check EVENT_NAME=push REF_NAME=renovate/thing
expect_pass "a pull request into main one patch above the release" \
    check EVENT_NAME=pull_request BASE_REF=main HEAD_REF=fix/thing
expect_pass "a merge queue run into main" check EVENT_NAME=merge_group BASE_REF=refs/heads/main

set_version 26.20.2
commit_all "Bump twice"
expect_fail "a fix two patches above the release" "should be 26.20.1" \
    check EVENT_NAME=push REF_NAME=fix/thing
expect_fail "a pull request into main two patches above the release" "should be 26.20.1" \
    check EVENT_NAME=pull_request BASE_REF=main HEAD_REF=fix/thing
expect_fail "a dependency update two patches above the release" "should be 26.20.1" \
    check EVENT_NAME=push REF_NAME=renovate/thing

set_version 26.21.0
commit_all "Open the release"
expect_pass "a release branch at its own version" check EVENT_NAME=push REF_NAME=release/v26.21.0
expect_fail "a release branch at another version" "carries version 26.22.0, but build.gradle.kts says 26.21.0" \
    check EVENT_NAME=push REF_NAME=release/v26.22.0
expect_fail "a release branch with a name that is no version" "named release/vX.Y.Z, not release/vnext" \
    check EVENT_NAME=push REF_NAME=release/vnext
expect_pass "a pull request into the release branch at its version" \
    check EVENT_NAME=pull_request BASE_REF=release/v26.21.0 HEAD_REF=feature/thing
expect_fail "a pull request into the release branch at another version" "carries version 26.22.0" \
    check EVENT_NAME=pull_request BASE_REF=release/v26.22.0 HEAD_REF=feature/thing
expect_pass "the pull request that releases the release branch" \
    check EVENT_NAME=pull_request BASE_REF=main HEAD_REF=release/v26.21.0
expect_fail "a release pull request at another version" "carries version 26.22.0" \
    check EVENT_NAME=pull_request BASE_REF=main HEAD_REF=release/v26.22.0
expect_fail "a pull request into any other branch" "not into develop" \
    check EVENT_NAME=pull_request BASE_REF=develop HEAD_REF=feature/thing

expect_pass "a feature while no release branch is open" check EVENT_NAME=push REF_NAME=feature/thing
git push -q origin 'v26.20.0^{commit}:refs/heads/release/v26.20.0'
git push -q origin HEAD:refs/heads/release/v26.21.0
expect_pass "a feature at the open release branch's version, a released one left over" check EVENT_NAME=push REF_NAME=feature/thing
expect_pass "a fix that waits for the open release, at its version" check EVENT_NAME=push REF_NAME=fix/thing
expect_pass "a fix pull request into the open release branch" \
    check EVENT_NAME=pull_request BASE_REF=release/v26.21.0 HEAD_REF=fix/thing
set_version 26.20.1
commit_all "Feature on the wrong version"
expect_fail "a feature at another version than the open release branch" \
    "released with release/v26.21.0, so the version should be 26.21.0, not 26.20.1" \
    check EVENT_NAME=push REF_NAME=feature/thing
expect_pass "an urgent fix one patch above the release while a release branch is open" \
    check EVENT_NAME=push REF_NAME=fix/thing
set_version 26.23.0
commit_all "Fix on no version at all"
expect_fail "a fix neither one patch above the release nor at an open release branch's version" \
    "should be 26.20.1" check EVENT_NAME=push REF_NAME=fix/thing
set_version 26.20.1
commit_all "Back on the patch"
git push -q origin HEAD:refs/heads/release/v26.22.0
expect_pass "a feature while several release branches are open" check EVENT_NAME=push REF_NAME=feature/thing

git checkout -q -b elsewhere v26.20.0
set_version 26.20.1
commit_all "Bump"
git tag -a v26.20.1 -m v26.20.1
git push -q origin v26.20.1
git checkout -q main
expect_fail "a fix at a version released from another commit" "already released as v26.20.1" \
    check EVENT_NAME=push REF_NAME=fix/thing

finish
