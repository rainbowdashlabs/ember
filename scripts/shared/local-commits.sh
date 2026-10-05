# shellcheck shell=bash
# What the scripts that write commits share. They run on a developer's machine, never in CI, with the
# developer's own git identity and signing: nothing here sets user.name, user.email or a signing key.
# Their work happens in a scratch worktree, so the checkout they are started from (its branch and its
# uncommitted changes) is never touched. Sourced by the scripts next to it.

# Whether git signs new commits here, as commit.gpgsign says.
signing_enabled() {
    [ "$(git config --bool commit.gpgsign 2> /dev/null)" = "true" ]
}

# The option that makes a rebase or a commit sign as configured: every rewritten commit signed when
# commit.gpgsign is on, none otherwise. A dry run never signs, since its commits are thrown away.
signing_option() {
    if [ "${dry_run:-0}" -eq 0 ] && signing_enabled; then echo "--gpg-sign"; else echo "--no-gpg-sign"; fi
}

# Whether the given git output says that signing a commit failed.
is_signing_failure() {
    grep -qiE 'failed to sign|gpg failed|signing failed|failed to write commit object' <<< "$1"
}

# The commits in the given range without a signature, one per line, empty when signing is off.
unsigned_commits() {
    local commit
    [ "$(signing_option)" = "--gpg-sign" ] || return 0
    for commit in $(git rev-list "$1"); do
        git cat-file commit "$commit" | grep -q '^gpgsig' || echo "$commit"
    done
}

# How the commits written are signed, for the report.
signing_description() {
    if [ "$(signing_option)" = "--gpg-sign" ]; then echo "signed"; else echo "unsigned"; fi
}

# Adds a detached scratch worktree at the given directory and commit.
add_scratch_worktree() {
    git worktree add -q --detach "$1" "$2"
}

# Removes the scratch worktree at the given directory, and every branch whose name starts with the
# given prefix.
remove_scratch_worktree() {
    git worktree remove --force "$1" 2> /dev/null || true
    git worktree prune
    git for-each-ref --format='%(refname)' "refs/heads/$2" | xargs -r -n 1 git update-ref -d
}
