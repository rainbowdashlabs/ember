#!/usr/bin/env bash
# Rebases every open release branch onto main, so the fixes released from main reach it.
#
# Usage: sync-release-branches.sh [--dry-run | --check]
#
# Run on a developer's machine: the rewritten commits carry the developer's git identity and are
# signed when commit.gpgsign is on, so nothing here sets an identity or turns signing off. The work
# happens in a scratch worktree; the checkout it is started from is left as it is.
#
# A release branch whose version is already tagged is never touched, even when it was not deleted
# after its release, and neither is one without a commit of its own, as a release branch is until its
# version bump is merged. For each other release/v* branch on origin that main has moved past:
#   1. When main gained a patch at or above the branch's own patch number, the branch's own patch is
#      renumbered above main's newest in every commit of the branch, through renumber-patch.sh.
#   2. The branch is rebased onto main, every commit rewritten and signed. Conflicts that only ever
#      come from releasing in parallel are resolved: the migration version file takes the newest patch,
#      build.gradle.kts keeps the release branch's version, and two version blocks added at the top of
#      a changelog keep both, the release branch's above main's.
#   3. The result is checked with check-migration-order.sh and for a signature on every commit, and
#      pushed with a lease on the commit it started from, so a push to the branch in the meantime is
#      never overwritten.
# Any other conflict, a signature that could not be made, or a failed check or push leaves the branch
# as it was, prints what stopped it and fails. A failed signature is not retried. --dry-run does all of
# it locally, unsigned, and pushes nothing.
#
# --check is what CI runs instead, and it writes to no branch: for each release branch that would be
# rebased it opens an issue saying so (or updates the one open already), and it closes that issue once
# the branch contains main.
set -euo pipefail

SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPTS/shared/repository.sh"
source "$SCRIPTS/shared/issues.sh"
source "$SCRIPTS/shared/local-commits.sh"

dry_run=0
check=0
case "${1:-}" in
    --dry-run) dry_run=1 ;;
    --check) check=1 ;;
    "") ;;
    *) echo "usage: sync-release-branches.sh [--dry-run | --check]" >&2; exit 2 ;;
esac

TEMP_BRANCHES="release-sync/"
work=$(mktemp -d)
tree="$work/tree"
trap 'remove_scratch_worktree "$tree" "$TEMP_BRANCHES"; rm -rf "$work"' EXIT
cp -r "$SCRIPTS" "$work/scripts"

# Resolves conflict hunks in which both sides only changed the `version = "..."` line, keeping the
# side being replayed: the release branch's version. Fails when another hunk is left.
resolve_version_lines() {
    awk '
        /^<<<<<<< / { state = 1; ours = ""; theirs = ""; clean = 1; held = $0 "\n"; next }
        state == 1 && /^\|\|\|\|\|\|\| / { state = 2; held = held $0 "\n"; next }
        state && /^=======$/ { state = 3; held = held $0 "\n"; next }
        state == 3 && /^>>>>>>> / {
            held = held $0 "\n"
            if (clean && ours != "" && theirs != "") printf "%s", theirs
            else { printf "%s", held; unresolved = 1 }
            state = 0; next
        }
        state {
            held = held $0 "\n"
            if (state == 1) { ours = ours $0 "\n"; if ($0 !~ /^version = "/) clean = 0 }
            if (state == 3) { theirs = theirs $0 "\n"; if ($0 !~ /^version = "/) clean = 0 }
            next
        }
        { print }
        END { exit unresolved }' "$1" > "$1.resolved" && mv "$1.resolved" "$1"
}

# Resolves conflict hunks in which both sides inserted lines at the same place and changed nothing
# else, keeping the side being replayed above main's: a release branch's changelog block above the
# block of a fix released meanwhile. Fails when another hunk is left.
resolve_insertions() {
    awk '
        /^<<<<<<< / { state = 1; ours = ""; base = ""; theirs = ""; held = $0 "\n"; next }
        state == 1 && /^\|\|\|\|\|\|\| / { state = 2; held = held $0 "\n"; next }
        state && /^=======$/ { state = 3; held = held $0 "\n"; next }
        state == 3 && /^>>>>>>> / {
            held = held $0 "\n"
            if (base == "") printf "%s%s", theirs, ours
            else { printf "%s", held; unresolved = 1 }
            state = 0; next
        }
        state {
            held = held $0 "\n"
            if (state == 1) ours = ours $0 "\n"
            if (state == 2) base = base $0 "\n"
            if (state == 3) theirs = theirs $0 "\n"
            next
        }
        { print }
        END { exit unresolved }' "$1" > "$1.resolved" && mv "$1.resolved" "$1"
}

# Resolves what can be resolved in the stopped rebase step, and prints every file that cannot.
resolve_conflicts() {
    local file
    while IFS= read -r file; do
        case "$file" in
            "$PATCH_VERSION_FILE")
                printf '1.%s\n' "$(find "$PATCH_DIR" -name 'patch_*.sql' | sed 's|.*/patch_\([0-9]*\)\.sql|\1|' | sort -n | tail -n 1)" > "$file"
                ;;
            build.gradle.kts) resolve_version_lines "$file" || { echo "$file"; continue; } ;;
            CHANGELOG.md | CHANGELOG.de.md) resolve_insertions "$file" || { echo "$file"; continue; } ;;
            *) echo "$file"; continue ;;
        esac
        git add "$file"
    done < <(git diff --name-only --diff-filter=U)
}

# Gives the branch's own patches numbers above main's newest in every commit of the branch, when one
# of them is not above it already.
renumber_own_patches() {
    local fork_point="$1" main_newest own=() number target filter=""
    main_newest=$(newest_patch "$MAIN_REF")
    for number in $(patch_numbers HEAD); do
        git cat-file -e "$fork_point:$(patch_path "$number")" 2> /dev/null || own+=("$number")
    done
    [ "${#own[@]}" -gt 0 ] && [ "${own[0]}" -le "$main_newest" ] || return 0

    target=$((main_newest + ${#own[@]}))
    for ((index = ${#own[@]} - 1; index >= 0; index--)); do
        number="${own[$index]}"
        echo "Renumbering the branch's patch_$number to patch_$target."
        filter+="if [ -f '$(patch_path "$number")' ]; then bash '$work/scripts/renumber-patch.sh' $number $target > /dev/null; fi; "
        target=$((target - 1))
    done
    FILTER_BRANCH_SQUELCH_WARNING=1 git filter-branch -f -d "$work/rewrite" --tree-filter "$filter" -- "$fork_point..HEAD" > /dev/null
    git for-each-ref --format='%(refname)' refs/original/ | xargs -r -n 1 git update-ref -d
}

# Runs one rebase command with the conflict style the resolvers read. Fails with 1 when the rebase
# stopped. When a signature could not be made it aborts the rebase, prints why and fails with 2,
# without trying again.
rebase_step() {
    local output
    if output=$(git -c merge.conflictStyle=diff3 rebase "$@" 2>&1); then
        return 0
    fi
    if is_signing_failure "$output"; then
        git rebase --abort
        echo "(signing a commit failed, so nothing was pushed; run the sync again once the key signs)"
        printf '%s\n' "$output" | grep -iE 'sign|error|fatal' | sed 's/^/  /' >&2
        return 2
    fi
    return 1
}

# Whether a rebase is stopped in the current worktree.
rebase_in_progress() {
    [ -d "$(git rev-parse --git-path rebase-merge)" ]
}

# Rebases the checked out branch onto main, every commit rewritten and signed as configured. Prints
# the files it could not resolve, or what else stopped it, if anything.
rebase_onto_main() {
    local unresolved status=0
    rebase_step -q --force-rebase "$(signing_option)" "$MAIN_REF" || status=$?
    while [ "$status" -eq 1 ] && rebase_in_progress && [ -n "$(git diff --name-only --diff-filter=U)" ]; do
        unresolved=$(resolve_conflicts)
        if [ -n "$unresolved" ]; then
            git rebase --abort
            printf '%s\n' "$unresolved"
            return
        fi
        status=0
        GIT_EDITOR=true rebase_step --continue || status=$?
    done
    if [ "$status" -eq 1 ]; then
        if rebase_in_progress; then git rebase --abort; fi
        echo "(the rebase stopped for a reason other than a conflict)"
    fi
}

# Rebases one release branch onto main in the scratch worktree and pushes it. Prints what stopped it,
# if anything; reports what it did on standard error.
sync_branch() {
    local branch="$1" old="$2" fork_point stopped unsigned count refusal
    cd "$tree"
    git checkout -q -B "$TEMP_BRANCHES$branch" "$old"
    fork_point=$(git merge-base "$MAIN_REF" HEAD)

    if ! renumber_own_patches "$fork_point" >&2; then
        echo "(renumbering the branch's own patch failed)"
        return
    fi

    stopped=$(rebase_onto_main)
    if [ -n "$stopped" ]; then
        printf '%s\n' "$stopped"
        return
    fi

    if ! EVENT_NAME=push REF_NAME="$branch" bash "$SCRIPTS/check-migration-order.sh" >&2; then
        echo "(the rebased branch fails the migration check)"
        return
    fi

    unsigned=$(unsigned_commits "$MAIN_REF..HEAD")
    if [ -n "$unsigned" ]; then
        sed 's/.*/(commit & is not signed, so nothing was pushed)/' <<< "$unsigned"
        return
    fi

    count=$(git rev-list --count "$MAIN_REF..HEAD")
    if [ "$dry_run" -eq 1 ]; then
        echo "Would push $branch at $(git rev-parse HEAD) (commits: $count), leased on $old." >&2
    elif ! refusal=$(git push -q --force-with-lease="refs/heads/$branch:$old" origin "HEAD:refs/heads/$branch" 2>&1); then
        printf '%s\n' "$refusal" >&2
        push_refusal "$refusal"
    else
        echo "Rebased $branch onto main and pushed it with a lease: $(signing_description) commits: $count, now at $(git rev-parse --short HEAD) (was $(git rev-parse --short "$old"))." >&2
    fi
}

# Names why a push was refused: a lease that no longer holds means the branch moved while it was
# rebased; otherwise each reason the remote gave, such as a repository rule, or the bare refusal.
push_refusal() {
    local reasons
    if grep -q "stale info" <<< "$1"; then
        echo "(the push was refused: the branch moved while it was rebased; run the sync again)"
        return
    fi
    reasons=$(sed -n 's/^remote: - \(.*[^[:space:]]\)[[:space:]]*$/(the push was refused: \1)/p' <<< "$1")
    printf '%s\n' "${reasons:-(the push was refused by the remote)}"
}

# The title of the issue that says a release branch is behind main.
behind_title() {
    printf '%s is behind main\n' "$1"
}

# Opens or updates the issue saying that a release branch does not contain main yet.
report_behind() {
    local branch="$1" body
    body=$(cat << EOF
\`$branch\` does not contain \`main\` at $(git rev-parse --short "$MAIN_REF") yet.

Run \`./toolchain.sh release-sync\` locally. It rebases the branch onto \`main\`, renumbers its own
patch when needed, signs every rewritten commit and pushes the branch with a lease. This issue is
closed once the branch contains \`main\`.
EOF
)
    if [ "$dry_run" -eq 1 ]; then
        echo "Would open or update the issue: $(behind_title "$branch")"
        return
    fi
    upsert_issue "$(behind_title "$branch")" "$body"
}

# Says how to rebase a branch the sync could not rebase.
explain_failure() {
    local branch="$1" problems="$2"
    echo "Could not rebase $branch; it is left as it was:"
    printf '%s\n' "$problems" | sed 's/^/  - /'
    if grep -q "signing a commit failed" <<< "$problems"; then return; fi
    echo "  Rebase it onto main by hand, renumber its own patch above main's newest with"
    echo "  ./toolchain.sh db-renumber-patch when needed, and push it with --force-with-lease."
}

git fetch -q origin "+refs/heads/main:refs/remotes/origin/main" "+refs/heads/release/*:refs/remotes/origin/release/*"
[ "$check" -eq 1 ] || add_scratch_worktree "$tree" "$MAIN_REF"
failed=0

for branch in $(git for-each-ref --format='%(refname:strip=3)' 'refs/remotes/origin/release/v*'); do
    old=$(git rev-parse "origin/$branch")
    if [ -n "$(released_commit "${branch#release/v}")" ]; then
        echo "$branch is released as ${branch#release/}; it is left alone."
        continue
    fi
    if git merge-base --is-ancestor "$MAIN_REF" "$old"; then
        echo "$branch already contains main."
        if [ "$check" -eq 1 ]; then close_issue "$(behind_title "$branch")" "\`$branch\` contains \`main\` now."; fi
        continue
    fi
    if git merge-base --is-ancestor "$old" "$MAIN_REF"; then
        echo "$branch has no commit of its own yet; it is rebased once its version bump is merged."
        continue
    fi
    if [ "$check" -eq 1 ]; then
        echo "$branch does not contain main; it needs ./toolchain.sh release-sync."
        report_behind "$branch"
        continue
    fi
    echo "Rebasing $branch onto main."
    problems=$(sync_branch "$branch" "$old")
    if [ -n "$problems" ]; then
        explain_failure "$branch" "$problems"
        failed=1
    fi
done

exit "$failed"
