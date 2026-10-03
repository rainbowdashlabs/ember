#!/usr/bin/env bash
# Rebases every open release branch onto main, so the fixes released from main reach it.
#
# Usage: sync-release-branches.sh [--dry-run]
#
# A release branch whose version is already tagged is never touched, even when it was not deleted
# after its release, and neither is one without a commit of its own, as a release branch is until its
# version bump is merged. For each other release/v* branch on origin that main has moved past:
#   1. When main gained a patch at or above the branch's own patch number, the branch's own patch is
#      renumbered above main's newest in every commit of the branch, through renumber-patch.sh.
#   2. The branch is rebased onto main. Conflicts that only ever come from releasing in parallel are
#      resolved: the migration version file takes the newest patch, build.gradle.kts keeps the release
#      branch's version, and two version blocks added at the top of a changelog keep both, the release
#      branch's above main's.
#   3. The result is checked with check-migration-order.sh and pushed with a lease on the commit it
#      started from, so a push to the branch in the meantime is never overwritten.
# Any other conflict, or a failed check or push, leaves the branch as it was and opens an issue (or
# comments on the open one) naming the branch and the files. --dry-run does all of it locally and
# neither pushes nor opens issues.
set -euo pipefail

SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPTS/shared/repository.sh"

dry_run=0
[ "${1:-}" = "--dry-run" ] && dry_run=1

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
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

# Rebases one release branch onto main and pushes it. Prints the files it could not resolve, if any.
sync_branch() {
    local branch="$1" old="$2" fork_point unresolved refusal
    git checkout -q -B "$branch" "$old"
    fork_point=$(git merge-base "$MAIN_REF" HEAD)

    if ! renumber_own_patches "$fork_point" >&2; then
        echo "(renumbering the branch's own patch failed)"
        return
    fi

    if ! git -c merge.conflictStyle=diff3 rebase -q "$MAIN_REF" > /dev/null 2>&1; then
        while [ -d "$(git rev-parse --git-path rebase-merge)" ]; do
            unresolved=$(resolve_conflicts)
            if [ -n "$unresolved" ]; then
                git rebase --abort
                printf '%s\n' "$unresolved"
                return
            fi
            if GIT_EDITOR=true git -c merge.conflictStyle=diff3 rebase --continue > /dev/null 2>&1; then
                break
            fi
            if [ -z "$(git diff --name-only --diff-filter=U)" ] && [ -d "$(git rev-parse --git-path rebase-merge)" ]; then
                git rebase --abort
                echo "(the rebase stopped for a reason other than a conflict)"
                return
            fi
        done
    fi

    if ! EVENT_NAME=push REF_NAME="$branch" bash "$SCRIPTS/check-migration-order.sh" >&2; then
        echo "(the rebased branch fails the migration check)"
        return
    fi

    if [ "$dry_run" -eq 1 ]; then
        echo "Would push $branch at $(git rev-parse HEAD), leased on $old." >&2
    elif ! refusal=$(git push -q --force-with-lease="refs/heads/$branch:$old" origin "HEAD:refs/heads/$branch" 2>&1); then
        printf '%s\n' "$refusal" >&2
        push_refusal "$refusal"
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

# Opens an issue about a branch that could not be rebased, or comments on the one already open.
report_failure() {
    local branch="$1" files="$2" title body existing
    title="Rebase of $branch onto main needs a hand"
    body=$(cat << EOF
The release sync could not rebase \`$branch\` onto \`main\` and left the branch as it was.

What stopped it:

$(printf '%s\n' "$files" | sed 's/^/- /')

Rebase the branch onto \`main\` by hand and resolve these. Renumber its own patch above main's newest
with \`./toolchain.sh db-renumber-patch\` if needed, and push it with \`--force-with-lease\`.
EOF
)
    if [ "$dry_run" -eq 1 ]; then
        echo "Would open the issue: $title" >&2
        printf '%s\n' "$body" >&2
        return
    fi
    existing=$(gh issue list --state open --search "\"$title\" in:title" --json number,title \
        --jq ".[] | select(.title == \"$title\") | .number" | head -n 1)
    if [ -n "$existing" ]; then
        gh issue comment "$existing" --body "$body" > /dev/null
        echo "Commented on issue #$existing." >&2
    else
        gh issue create --title "$title" --body "$body" > /dev/null
        echo "Opened an issue: $title" >&2
    fi
}

git fetch -q origin "+refs/heads/main:refs/remotes/origin/main" "+refs/heads/release/*:refs/remotes/origin/release/*"
start=$(git rev-parse --abbrev-ref HEAD)
failed=0

for branch in $(git for-each-ref --format='%(refname:strip=3)' 'refs/remotes/origin/release/v*'); do
    old=$(git rev-parse "origin/$branch")
    if [ -n "$(released_commit "${branch#release/v}")" ]; then
        echo "$branch is released as ${branch#release/}; it is left alone."
        continue
    fi
    if git merge-base --is-ancestor "$MAIN_REF" "$old"; then
        echo "$branch already contains main."
        continue
    fi
    if git merge-base --is-ancestor "$old" "$MAIN_REF"; then
        echo "$branch has no commit of its own yet; it is rebased once its version bump is merged."
        continue
    fi
    echo "Rebasing $branch onto main."
    problems=$(sync_branch "$branch" "$old")
    if [ -n "$problems" ]; then
        echo "Could not rebase $branch:"
        printf '  %s\n' "$problems"
        report_failure "$branch" "$problems"
        failed=1
    elif [ "$dry_run" -eq 0 ]; then
        echo "Rebased and pushed $branch."
    fi
done

git checkout -q "$start" 2> /dev/null || true
exit "$failed"
