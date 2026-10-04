#!/usr/bin/env bash
# Releases a version: tags it, publishes its GitHub release from the changelog, and for a feature
# release fast-forwards main to the release branch first.
#
# Usage: release.sh check <pull request>                  the checks of a feature release, nothing else
#        release.sh feature <pull request> [--dry-run]     a feature release from its pull request
#        release.sh fix [--commit <sha>] [--wait] [--dry-run]
#                                                         a fix release of main's head or the given commit
#
# A feature release needs a pull request from release/vX.Y.Z into main whose branch carries version
# X.Y.Z, is not released, has a changelog block, passed CI on its head commit, and contains main. main
# then moves to that commit without a merge commit, which is also what marks the pull request merged.
#
# A fix release tags a commit on main with the version it carries, once CI passed on it. With --wait
# it waits for that CI run instead of refusing while it is still going.
#
# Every finding is printed, and appended as a line to the file RELEASE_REPORT names when it is set, so
# a workflow can post them on the pull request.
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/shared/repository.sh"

problems=0

say() {
    echo "$*"
    [ -z "${RELEASE_REPORT:-}" ] || printf '%s\n' "$*" >> "$RELEASE_REPORT"
}

problem() {
    say "- $*"
    problems=$((problems + 1))
}

refuse() {
    say "- $*"
    exit 1
}

stop_on_problems() {
    [ "$problems" -eq 0 ] || exit 1
}

# Runs a command, or only prints it on a dry run.
act() {
    if [ "$dry_run" -eq 1 ]; then
        echo "Would run: $*"
    else
        "$@"
    fi
}

# The status of the newest Verify run triggered by a push of the given commit, as "status conclusion
# url id", empty when there is none. A run still going has no conclusion yet and says "pending" there:
# an empty field would let `read` close the gap and shift the url and the id one place to the left.
verify_run() {
    gh run list --commit "$1" --workflow verify.yml --event push --limit 1 \
        --json status,conclusion,url,databaseId \
        --jq '.[0] | select(. != null)
            | "\(.status) \(if (.conclusion // "") == "" then "pending" else .conclusion end) \(.url) \(.databaseId)"'
}

# Waits until a Verify run of the given commit exists and has finished.
wait_for_verify() {
    local commit="$1" run attempt
    for attempt in $(seq 1 30); do
        run=$(verify_run "$commit")
        [ -n "$run" ] && break
        echo "No CI run for $commit yet, waiting ($attempt of 30)."
        sleep 20
    done
    [ -n "$run" ] || return 0
    read -r _ _ _ id <<< "$run"
    gh run watch "$id" --interval 30 > /dev/null || true
}

require_green_ci() {
    local commit="$1" status conclusion url
    read -r status conclusion url _ <<< "$(verify_run "$commit")"
    if [ -z "$status" ]; then
        problem "CI has not run on $commit. Push the branch and wait for the Verify workflow."
    elif [ "$status" != "completed" ]; then
        problem "CI is still running on $commit ($url). Release again once it is green."
    elif [ "$conclusion" != "success" ]; then
        problem "CI failed on $commit ($url)."
    else
        say "- CI passed on $commit."
    fi
}

require_unreleased() {
    local version="$1"
    if [ -n "$(released_commit "$version")" ]; then
        problem "Version $version is already released as v$version."
    else
        say "- Version $version is not released yet."
    fi
}

require_release_notes() {
    local version="$1" commit="$2"
    if [ -z "$(release_notes "$version" "$commit")" ]; then
        problem "CHANGELOG.md has no block for v$version, which the release notes are taken from."
    else
        say "- CHANGELOG.md has a block for v$version."
    fi
}

check_feature_release() {
    local pr="$1" state base cross_repository
    IFS=$'\t' read -r state base head commit cross_repository < <(gh pr view "$pr" \
        --json state,baseRefName,headRefName,headRefOid,isCrossRepository \
        --jq '[.state, .baseRefName, .headRefName, .headRefOid, .isCrossRepository] | @tsv')

    say "Release checks for pull request #$pr ($head into $base):"
    [ "$state" = "OPEN" ] || refuse "The pull request is ${state,,}, not open."
    [ "$base" = "main" ] || refuse "A release goes into main, not into $base."
    [ "$cross_repository" = "false" ] || refuse "A release branch lives in this repository, not in a fork."
    version="${head#release/v}"
    if [[ "$head" != release/v* ]] || ! is_version "$version"; then
        refuse "A release comes from a branch named release/vX.Y.Z, not from $head."
    fi

    git fetch -q origin "+refs/heads/main:refs/remotes/origin/main" "+refs/heads/$head:refs/remotes/origin/$head"
    [ "$(git rev-parse "origin/$head")" = "$commit" ] ||
        refuse "$head moved while the release was starting. Release again."

    if [ "$(project_version "$commit")" = "$version" ]; then
        say "- The branch carries version $version."
    else
        problem "The branch carries version $(project_version "$commit"), but its name says $version."
    fi
    require_unreleased "$version"
    require_release_notes "$version" "$commit"
    if git merge-base --is-ancestor origin/main "$commit"; then
        say "- main can be fast-forwarded to $commit."
    else
        problem "main has commits $head lacks, so it cannot be fast-forwarded. The Release Sync workflow rebases the branch onto main; run it, or rebase by hand."
    fi
    require_green_ci "$commit"
    stop_on_problems
}

check_fix_release() {
    local requested="$1" wait="$2"
    git fetch -q origin "+refs/heads/main:refs/remotes/origin/main"
    commit=$(git rev-parse "${requested:-origin/main}^{commit}")
    version=$(project_version "$commit")

    say "Release checks for $commit on main:"
    git merge-base --is-ancestor "$commit" origin/main || refuse "$commit is not on main."
    is_version "$version" || refuse "build.gradle.kts carries no version at $commit."
    say "- The commit carries version $version."
    require_unreleased "$version"
    require_release_notes "$version" "$commit"
    [ "$wait" -eq 0 ] || wait_for_verify "$commit"
    require_green_ci "$commit"
    stop_on_problems
}

publish() {
    local fast_forward="$1" notes
    notes=$(mktemp)
    release_notes "$version" "$commit" > "$notes"

    act git tag -a "v$version" -m "v$version" "$commit"
    if [ "$fast_forward" -eq 1 ]; then
        act git push --atomic origin "$commit:refs/heads/main" "refs/tags/v$version"
    else
        act git push origin "refs/tags/v$version"
    fi
    act gh release create "v$version" --title "v$version" --notes-file "$notes" --verify-tag

    if [ "$dry_run" -eq 1 ]; then
        echo "Release notes:"
        cat "$notes"
    else
        say ""
        [ "$fast_forward" -eq 0 ] || say "Moved main to $commit."
        say "Tagged v$version on $commit, unsigned, and published the release v$version."
    fi
    rm -f "$notes"
}

mode="${1:-}"
shift || true
dry_run=0
wait=0
requested=""
pr=""
while [ $# -gt 0 ]; do
    case "$1" in
        --dry-run) dry_run=1 ;;
        --wait) wait=1 ;;
        --commit) requested="$2"; shift ;;
        *) pr="$1" ;;
    esac
    shift
done

case "$mode" in
    check)
        [ -n "$pr" ] || refuse "Name the pull request of the release, e.g. release.sh check 220."
        check_feature_release "$pr"
        say "The release v$version is ready."
        ;;
    feature)
        [ -n "$pr" ] || refuse "Name the pull request of the release, e.g. release.sh feature 220."
        check_feature_release "$pr"
        publish 1
        ;;
    fix)
        check_fix_release "$requested" "$wait"
        publish 0
        ;;
    *)
        echo "usage: release.sh check <pull request> | feature <pull request> [--dry-run] | fix [--commit <sha>] [--wait] [--dry-run]" >&2
        exit 2
        ;;
esac
