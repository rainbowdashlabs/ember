#!/usr/bin/env bash
# Prepares the versions that follow the newest release on main. Nothing it opens is merged
# automatically: both bump pull requests wait for a human.
#
# Usage: open-next-versions.sh [--dry-run | --check]
#
# After every release, while main still carries the released version X.Y.Z, it opens a pull request
# from chore/bump-X.Y.(Z+1) into main whose one commit bumps the version to the next patch.
#
# After a feature release X.Y.0, it also opens the next release branch release/vX.(Y+1).0 at the
# released commit, without a commit of its own, and a pull request from chore/bump-X.(Y+1).0 into it
# whose one commit bumps the version to X.(Y+1).0. A release branch for any later version that is
# already there means the next release is open, and nothing is created. Once the release branch has a
# commit of its own, it opens the branch's release pull request into main as a draft, which stays
# open until the release is labelled.
#
# Run on a developer's machine: the bump commits carry the developer's git identity and are signed
# when commit.gpgsign is on. They are made in a scratch worktree, so the checkout it is started from is
# left as it is. A signature that cannot be made stops it before anything is pushed, without a retry.
#
# A bump that is already open as a pull request, or already made, is left as it is, so running it
# twice does nothing the second time. --dry-run prepares the commits locally, unsigned, and neither
# pushes nor opens pull requests. --check is what CI runs after a release instead, and it writes
# nothing to any branch: it lists what is still to be opened in one issue, and closes that issue once
# nothing is left, as a local run does when it is done.
set -euo pipefail

SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPTS/shared/repository.sh"
source "$SCRIPTS/shared/issues.sh"
source "$SCRIPTS/shared/local-commits.sh"
source "$SCRIPTS/shared/pull-requests.sh"

dry_run=0
check=0
case "${1:-}" in
    --dry-run) dry_run=1 ;;
    --check) check=1 ;;
    "") ;;
    *) echo "usage: open-next-versions.sh [--dry-run | --check]" >&2; exit 2 ;;
esac

ISSUE_TITLE="Next versions to open"
TEMP_BRANCHES="release-next/"
work=$(mktemp -d)
tree="$work/tree"
trap 'remove_scratch_worktree "$tree" "$TEMP_BRANCHES"; rm -rf "$work"' EXIT
needed=()

# Runs a command, or only prints it on a dry run.
act() {
    if [ "$dry_run" -eq 1 ]; then
        echo "Would run: $*"
    else
        "$@"
    fi
}

# Notes a step a check found still to be taken.
note_needed() {
    needed+=("$1")
    echo "Still to be opened: $1"
}

# Whether the first version is above the second.
is_above() {
    [ "$1" != "$2" ] && [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -n 1)" = "$1" ]
}

# Commits the version bump on top of the given commit, pushes it as chore/bump-<version> and opens its
# pull request into the given base, unless that pull request is open already.
open_bump() {
    local version="$1" start="$2" base="$3" branch="chore/bump-$1" existing
    existing=$(open_pull_request "$branch" "$base")
    if [ -n "$existing" ]; then
        echo "Pull request #$existing already bumps $base to $version."
        return
    fi
    if [ "$check" -eq 1 ]; then
        note_needed "the pull request \`$branch\` into \`$base\`, bumping the version to $version"
        return
    fi
    git -C "$tree" checkout -q -B "$TEMP_BRANCHES$branch" "$start"
    sed -i "s/^version = \".*\"$/version = \"$version\"/" "$tree/build.gradle.kts"
    if ! git -C "$tree" commit -q "$(signing_option)" -m "Bump version to $version" build.gradle.kts; then
        echo "Committing the bump to $version failed, so nothing more was pushed. When the key did not sign, run it again once it does." >&2
        exit 1
    fi
    act git -C "$tree" push -q -f origin "HEAD:refs/heads/$branch"
    act gh pr create --base "$base" --head "$branch" --title "Bump version to $version" \
        --body "Opens $version on \`$base\`. Merge it by hand."
    [ "$dry_run" -eq 1 ] || echo "Pushed $branch with one $(signing_description) commit and opened its pull request into $base."
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

    if [ -z "$later" ] && [ "$check" -eq 1 ]; then
        note_needed "the release branch \`$branch\` at the release v$released"
    elif [ -z "$later" ]; then
        echo "Opening $branch at the release v$released."
        act git push -q origin "$(released_commit "$released"):refs/heads/$branch"
    elif [ "$later" != "$branch" ]; then
        echo "The next release is open already (${later//$'\n'/, }), so $branch is not created."
        return
    fi
    if [ -n "$later" ] && [ "$(project_version "origin/$branch")" = "$version" ]; then
        echo "$branch carries $version already."
    else
        open_bump "$version" "$(released_commit "$released")" "$branch"
    fi
    if [ "$check" -eq 0 ]; then
        open_release_pull_request "$branch"
    elif has_own_commit "$branch" && [ -z "$(open_pull_request "$branch" main)" ]; then
        note_needed "the draft release pull request from \`$branch\` into \`main\`"
    fi
}

# Lists what is still to be opened in the issue, or closes the issue when nothing is.
report_needed() {
    local released="$1" body
    if [ "${#needed[@]}" -eq 0 ]; then
        close_issue "$ISSUE_TITLE" "Nothing is left to open after v$released."
        return
    fi
    body=$(cat << EOF
After the release v$released these are still to be opened:

$(printf -- '- %s\n' "${needed[@]}")

Run \`./toolchain.sh release-next\` locally. It makes each bump as a signed commit, pushes the
branches and opens the pull requests. This issue is closed once that is done.
EOF
)
    upsert_issue "$ISSUE_TITLE" "$body"
}

git fetch -q origin "+refs/heads/main:refs/remotes/origin/main" "+refs/heads/release/*:refs/remotes/origin/release/*"
released=$(newest_release_reachable_from "$MAIN_REF")
[ -n "$released" ] || { echo "main contains no release, so there is nothing to follow up."; exit 0; }
echo "The newest release on main is v$released."
[ "$check" -eq 1 ] || add_scratch_worktree "$tree" "$MAIN_REF"

bump_main "$released"
if [[ "$released" == *.0 ]]; then
    open_release_branch "$released"
else
    echo "v$released is a fix release, so no release branch is opened."
fi

if [ "$check" -eq 1 ]; then
    report_needed "$released"
elif [ "$dry_run" -eq 0 ]; then
    close_issue "$ISSUE_TITLE" "The next versions after v$released are opened."
fi
