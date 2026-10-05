# shellcheck shell=bash
# Keeps one open GitHub issue per title: the release checks open it, update it and close it, so a
# check that runs after every push never opens the same issue twice. Sourced by the scripts next to it.

# The number of the open issue with exactly the given title, empty when there is none.
open_issue() {
    gh issue list --state open --search "\"$1\" in:title" --json number,title \
        --jq ".[] | select(.title == \"$1\") | .number" | head -n 1
}

# Opens an issue with the given title and body, or replaces the body of the one already open.
upsert_issue() {
    local title="$1" body="$2" existing
    existing=$(open_issue "$title")
    if [ -n "$existing" ]; then
        gh issue edit "$existing" --body "$body" > /dev/null
        echo "Updated issue #$existing: $title"
    else
        gh issue create --title "$title" --body "$body" > /dev/null
        echo "Opened an issue: $title"
    fi
}

# Closes the open issue with the given title, if there is one, with the given comment.
close_issue() {
    local title="$1" comment="$2" existing
    existing=$(open_issue "$title")
    [ -n "$existing" ] || return 0
    gh issue close "$existing" --comment "$comment" > /dev/null
    echo "Closed issue #$existing: $title"
}
