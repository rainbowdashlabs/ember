#!/usr/bin/env bash
# Checks the project version against the released tags and the branch it is headed for.
#
# Reads the event from the environment the workflow passes on: EVENT_NAME, REF_TYPE, REF_NAME, and
# for a pull request or a merge queue BASE_REF and HEAD_REF.
#
#   tag vX.Y.Z                          the tag equals the version
#   release/vX.Y.Z, or a pull request   the version is X.Y.Z
#     into it or from it into main
#   a pull request into main            the version is one patch above the newest release reachable
#                                       from this commit
#   fix/**                              the same for an urgent fix headed for main, or the version of
#                                       an open release branch for a fix that ships with that release
#   main, renovate/**, or a pull        the same, or still the newest release: main keeps it until
#     request from renovate/** into     the bump pull request opened after the release is merged
#     main
#   main after a feature release was    the version is one minor above the newest release, unreleased,
#     rebased and merged                with its changelog block: the release workflow tags it next
#   feature/**                          the version is the one of the open release branch, when there
#                                       is exactly one
#   a pull request into anything else   refused
#
# Apart from the tag, the version must not be released yet, unless this very commit is that release:
# that is also how a release branch opened at the release before it passes until its bump is merged.
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/shared/repository.sh"

fail() {
    echo "::error::$*"
    exit 1
}

version=$(project_version HEAD)
[ -n "$version" ] || fail "No version line found in build.gradle.kts."

require_unreleased() {
    local released
    released=$(released_commit "$version")
    [ -n "$released" ] || { echo "Version $version is not released yet."; return; }
    fail "Version $version is already released as v$version. Bump the version in build.gradle.kts."
}

require_release_branch_version() {
    local branch="$1" wanted="${1#release/v}"
    is_version "$wanted" || fail "Release branches are named release/vX.Y.Z, not $branch."
    [ "$version" = "$wanted" ] ||
        fail "The release branch $branch carries version $wanted, but build.gradle.kts says $version."
    echo "Version $version matches the release branch $branch."
    require_unreleased
}

require_next_patch() {
    local newest expected
    newest=$(newest_release_reachable_from HEAD)
    if [ -z "$newest" ]; then
        echo "No release is reachable from this commit, so any unreleased version will do."
        require_unreleased
        return
    fi
    expected=$(next_patch "$newest")
    [ "$version" = "$expected" ] ||
        fail "Version $version should be $expected, one patch above the newest release v$newest. Features go to the open release/vX.Y.Z branch."
    echo "Version $version is one patch above the newest release v$newest."
    require_unreleased
}

require_next_patch_or_newest() {
    local newest
    newest=$(newest_release_reachable_from HEAD)
    if [ -n "$newest" ] && [ "$version" = "$newest" ]; then
        echo "Version $version is the newest release v$newest; dependency updates collect here until the next fix bumps the patch."
        return
    fi
    require_next_patch
}

# main right after a release pull request was rebased and merged, before the release workflow tagged it:
# it carries the next feature release, one minor above the newest one, and that release's changelog
# block. It is judged by what it holds, so the release branch may be gone by then.
require_next_patch_newest_or_merged_release() {
    local newest
    newest=$(newest_release_reachable_from HEAD)
    if [ -n "$newest" ] && [ "$version" = "$(next_minor "$newest")" ] && [ -n "$(release_notes "$version" HEAD)" ]; then
        echo "Version $version is the feature release merged into main; the release workflow tags it once CI passes."
        require_unreleased
        return
    fi
    require_next_patch_or_newest
}

open_release_branches() {
    git ls-remote --heads origin 'release/v*' | sed 's|.*refs/heads/||' |
        while read -r branch; do
            if [ -z "$(released_commit "${branch#release/v}")" ]; then echo "$branch"; fi
        done
}

require_hotfix_or_release_fix() {
    local branch
    for branch in $(open_release_branches); do
        if [ "$version" = "${branch#release/v}" ]; then
            echo "Version $version is the one of the open release branch $branch, so this fix ships with that release."
            require_unreleased
            return
        fi
    done
    require_next_patch
}

require_open_release_version() {
    local branches
    branches=$(open_release_branches)
    case $(printf '%s' "$branches" | grep -c . || true) in
        0) echo "No release branch is open, so the version is only checked against the releases." ;;
        1)
            [ "$version" = "${branches#release/v}" ] ||
                fail "Features are released with $branches, so the version should be ${branches#release/v}, not $version."
            echo "Version $version matches the open release branch $branches."
            ;;
        *) echo "Several release branches are open (${branches//$'\n'/, }), so the version is only checked against the releases." ;;
    esac
    require_unreleased
}

if [ "${REF_TYPE:-branch}" = "tag" ]; then
    [ "$REF_NAME" = "v$version" ] ||
        fail "Tag $REF_NAME was cut from a build that says $version. Bump the version before tagging."
    echo "Tag $REF_NAME matches the project version."
    exit 0
fi

if [ "$(released_commit "$version")" = "$(git rev-parse HEAD)" ]; then
    if [[ "${REF_NAME:-}" == release/v* && "$REF_NAME" != "release/v$version" ]]; then
        echo "$REF_NAME starts at the release v$version and has no commit of its own yet. Its version bump comes by pull request."
    else
        echo "This commit is the release v$version."
    fi
    exit 0
fi

case "${EVENT_NAME:-push}" in
    pull_request | pull_request_target | merge_group)
        base="${BASE_REF#refs/heads/}"
        head="${HEAD_REF:-}"
        case "$base" in
            main)
                case "$head" in
                    release/v*) require_release_branch_version "$head" ;;
                    renovate/*) require_next_patch_or_newest ;;
                    *) require_next_patch ;;
                esac
                ;;
            release/v*) require_release_branch_version "$base" ;;
            *) fail "Pull requests go into main for fixes or into the open release/vX.Y.Z branch for features, not into $base." ;;
        esac
        ;;
    *)
        case "$REF_NAME" in
            main) require_next_patch_newest_or_merged_release ;;
            renovate/*) require_next_patch_or_newest ;;
            fix/*) require_hotfix_or_release_fix ;;
            release/v*) require_release_branch_version "$REF_NAME" ;;
            feature/*) require_open_release_version ;;
            *) require_unreleased ;;
        esac
        ;;
esac
