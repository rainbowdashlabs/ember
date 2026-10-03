#!/usr/bin/env bash
# Prepares the versions that follow the newest release on main. Nothing it opens is merged
# automatically: both bump pull requests wait for a human.
#
# Usage: open-next-versions.sh [--dry-run]
#
# After every release, while main still carries the released version X.Y.Z, it opens a pull request
# from chore/bump-X.Y.(Z+1) into main whose one commit bumps the version to the next patch.
#
# After a feature release X.Y.0, it also opens the next release branch release/vX.(Y+1).0 at the
# released commit, without a commit of its own, and a pull request from chore/bump-X.(Y+1).0 into it
# whose one commit bumps the version to X.(Y+1).0. A release branch for any later version that is
# already there means the next release is open, and nothing is created.
#
# A bump that is already open as a pull request, or already made, is left as it is, so running it
# twice does nothing the second time. --dry-run prepares the commits locally and neither pushes nor
# opens pull requests.
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/shared/repository.sh"

dry_run=0
[ "${1:-}" = "--dry-run" ] && dry_run=1

# Runs a command, or only prints it on a dry run.
act() {
    if [ "$dry_run" -eq 1 ]; then
        echo "Would run: $*"
    else
        "$@"
    fi
}

# The number of the open pull request from the given branch into the given base, empty when none is.
open_pull_request() {
    gh pr list --state open --head "$1" --base "$2" --json number --jq '.[0].number // empty'
}

# Whether the first version is above the second.
is_above() {
    [ "$1" != "$2" ] && [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -n 1)" = "$1" ]
}

# Commits the version bump on top of the given commit as chore/bump-<version>, pushes it and opens
# its pull request into the given base, unless that pull request is open already.
open_bump() {
    local version="$1" start="$2" base="$3" branch="chore/bump-$1" existing
    existing=$(open_pull_request "$branch" "$base")
    if [ -n "$existing" ]; then
        echo "Pull request #$existing already bumps $base to $version."
        return
    fi
    git checkout -q -B "$branch" "$start"
    sed -i "s/^version = \".*\"$/version = \"$version\"/" build.gradle.kts
    git commit -q -m "Bump version to $version" build.gradle.kts
    act git push -q -f origin "refs/heads/$branch"
    act gh pr create --base "$base" --head "$branch" --title "Bump version to $version" \
        --body "Opens $version on \`$base\`. Merge it by hand."
}

# Opens the pull request that moves main to the next patch, while main carries the released version.
bump_main() {
    local released="$1" carried
    carried=$(project_version "$MAIN_REF")
    if [ "$carried" != "$released" ]; then
        echo "main carries $carried, not the released $released, so it needs no bump."
        return
    fi
    open_bump "$(next_patch "$released")" "$MAIN_REF" main
}

# The release branches on origin above the given version, one per line.
later_release_branches() {
    git for-each-ref --format='%(refname:strip=3)' 'refs/remotes/origin/release/v*' |
        while read -r branch; do
            if is_version "${branch#release/v}" && is_above "${branch#release/v}" "$1"; then echo "$branch"; fi
        done
}

# Opens the next release branch after a feature release, and the pull request that bumps it.
open_release_branch() {
    local released="$1" version branch later
    version=$(next_minor "$released")
    branch="release/v$version"
    later=$(later_release_branches "$released")

    if [ -z "$later" ]; then
        echo "Opening $branch at the release v$released."
        act git push -q origin "$(released_commit "$released"):refs/heads/$branch"
    elif [ "$later" != "$branch" ]; then
        echo "The next release is open already (${later//$'\n'/, }), so $branch is not created."
        return
    elif [ "$(project_version "origin/$branch")" = "$version" ]; then
        echo "$branch carries $version already."
        return
    fi
    open_bump "$version" "$(released_commit "$released")" "$branch"
}

git fetch -q origin "+refs/heads/main:refs/remotes/origin/main" "+refs/heads/release/*:refs/remotes/origin/release/*"
start=$(git rev-parse --abbrev-ref HEAD)
released=$(newest_release_reachable_from "$MAIN_REF")
[ -n "$released" ] || { echo "main contains no release, so there is nothing to follow up."; exit 0; }
echo "The newest release on main is v$released."

bump_main "$released"
if [[ "$released" == *.0 ]]; then
    open_release_branch "$released"
else
    echo "v$released is a fix release, so no release branch is opened."
fi

git checkout -q "$start" 2> /dev/null || true
