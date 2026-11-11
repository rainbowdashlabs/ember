#!/usr/bin/env bash
# Fetches the coverage data of every backend test suite of a workflow run into one directory.
#
# Which suites ran is read from the run's own jobs, named "Verify Backend Tests (<suite>)", so the
# matrix stays the one place that lists them. Each suite uploads jacoco-<suite> holding <suite>.exec.
# An artifact the last suite uploaded seconds ago is not always listed yet, and a report built
# without it falls below the coverage gates for classes nobody touched; so what is missing is asked
# for again a few times before giving up with the suites that never arrived.
#
# Reads RUN_ID and REPOSITORY, and optionally DEST (build/jacoco), ATTEMPTS (6) and WAIT seconds (10).
set -euo pipefail

: "${RUN_ID:?RUN_ID names the workflow run}"
: "${REPOSITORY:?REPOSITORY names owner/repo}"
dest="${DEST:-build/jacoco}"
attempts="${ATTEMPTS:-6}"
wait="${WAIT:-10}"

mapfile -t suites < <(gh api --paginate "repos/$REPOSITORY/actions/runs/$RUN_ID/jobs?filter=latest&per_page=100" \
    --jq '.jobs[].name | capture("^Verify Backend Tests \\((?<suite>[^)]+)\\)$").suite')
if [ ${#suites[@]} -eq 0 ]; then
    echo "No backend test suite ran in run $RUN_ID." >&2
    exit 1
fi
mkdir -p "$dest"

missing_suites() {
    local suite
    for suite in "${suites[@]}"; do
        [ -f "$dest/$suite.exec" ] || echo "$suite"
    done
}

for ((attempt = 1; attempt <= attempts; attempt++)); do
    mapfile -t missing < <(missing_suites)
    if [ ${#missing[@]} -eq 0 ]; then
        echo "Coverage data of ${#suites[@]} suites: ${suites[*]}"
        exit 0
    fi
    [ "$attempt" -gt 1 ] && sleep "$wait"
    for suite in "${missing[@]}"; do
        gh run download "$RUN_ID" --repo "$REPOSITORY" --name "jacoco-$suite" --dir "$dest" 2>/dev/null || true
    done
done

mapfile -t missing < <(missing_suites)
if [ ${#missing[@]} -gt 0 ]; then
    echo "No coverage data arrived for: ${missing[*]}" >&2
    exit 1
fi
echo "Coverage data of ${#suites[@]} suites: ${suites[*]}"
