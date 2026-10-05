# shellcheck shell=bash
# Finds and opens the pull requests the release scripts keep open: the version bumps and the release
# pull request of every release branch, which stays open as a draft until the release is labelled.
# Sourced by the scripts next to it; reads `dry_run` from the script that sources it.

# The number of the open pull request from the given branch into the given base, empty when none is.
open_pull_request() {
    gh pr list --state open --head "$1" --base "$2" --json number --jq '.[0].number // empty'
}

# Whether the release branch on origin has a commit main lacks, which a pull request needs.
has_own_commit() {
    git rev-parse -q --verify "origin/$1^{commit}" > /dev/null &&
        ! git merge-base --is-ancestor "origin/$1" "$MAIN_REF"
}

# Opens the draft pull request that releases the given release branch into main, unless one is open.
# A branch without a commit of its own cannot have one yet, and gets it once its version bump is merged.
open_release_pull_request() {
    local branch="$1" version="${1#release/v}" existing
    if ! has_own_commit "$branch"; then
        echo "$branch has no commit of its own yet; its release pull request is opened by ./toolchain.sh release-sync once its version bump is merged."
        return
    fi
    existing=$(open_pull_request "$branch" main)
    if [ -n "$existing" ]; then
        echo "Pull request #$existing releases $branch."
    elif [ "${dry_run:-0}" -eq 1 ]; then
        echo "Would open the draft pull request: Release v$version"
    else
        gh pr create --draft --base main --head "$branch" --title "Release v$version" \
            --body "Releases v$version once it is labelled \`release\`." > /dev/null
        echo "Opened the draft pull request that releases $branch."
    fi
}
