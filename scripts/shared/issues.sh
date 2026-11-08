# shellcheck shell=bash
# Keeps one GitHub issue per title for good: the release checks open it once, then reopen, update and
# close that same issue, so the checks after every release keep using one issue instead of leaving a
# new one behind each time. Sourced by the scripts next to it.

# The number of the open issue with exactly the given title, empty when there is none.
open_issue() {
    gh issue list --state open --search "\"$1\" in:title" --json number,title \
        --jq ".[] | select(.title == \"$1\") | .number" | head -n 1
}

# The number of the newest issue with exactly the given title, open or closed, empty when there is none.
newest_issue() {
    gh issue list --state all --search "\"$1\" in:title" --json number,title \
        --jq "[.[] | select(.title == \"$1\") | .number] | max // empty"
}

# Gives the issue with the given title the given body: the open one is updated, a closed one is opened
# again, and an issue is only created where there has never been one by that title.
upsert_issue() {
    local title="$1" body="$2" existing
    existing=$(open_issue "$title")
    if [ -n "$existing" ]; then
        gh issue edit "$existing" --body "$body" > /dev/null
        echo "Updated issue #$existing: $title"
        return
    fi
    existing=$(newest_issue "$title")
    if [ -n "$existing" ]; then
        gh issue edit "$existing" --body "$body" > /dev/null
        gh issue reopen "$existing" > /dev/null
        echo "Reopened issue #$existing: $title"
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
