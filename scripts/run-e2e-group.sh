#!/usr/bin/env bash
# Runs end-to-end stories, and gives the stories that failed a second chance with a single worker
# before the run counts as failed. Run from frontend/, with the arguments Playwright is to get.
#
# Four workers share one backend, and a story that loses a race against another one fails without
# anything being wrong with it. Rerun alone, such a story passes, so the job stays green and is not
# started again from scratch. A story that fails a second time fails the run, and the rerun keeps its
# trace and video for the report. When more than MAX_RERUN stories (10) fail, or the run failed
# without a failed story, nothing is rerun: that is a real breakage, not a flake.
#
# Reads PLAYWRIGHT (npx playwright), MAX_RERUN and LAST_RUN (e2e/results/.last-run.json, where
# Playwright records what failed).
set -uo pipefail

read -ra playwright <<< "${PLAYWRIGHT:-npx playwright}"
max_rerun="${MAX_RERUN:-10}"
last_run="${LAST_RUN:-e2e/results/.last-run.json}"

# Prints how many stories the last run recorded as failed, 0 when it recorded none.
failed_count() {
    node -e '
        const fs = require("node:fs")
        let failed = []
        try { failed = JSON.parse(fs.readFileSync(process.argv[1], "utf8")).failedTests ?? [] } catch {}
        console.log(failed.length)
    ' "$last_run"
}

# Writes a warning the way the CI run shows it, or plainly elsewhere.
warn() {
    if [ -n "${GITHUB_ACTIONS:-}" ]; then
        echo "::warning title=Flaky end-to-end stories::$1"
    else
        echo "warning: $1" >&2
    fi
}

"${playwright[@]}" test "$@" && exit 0

failed=$(failed_count)
if [ "$failed" -eq 0 ]; then
    echo "The end-to-end run failed without a failed story, so there is nothing to rerun." >&2
    exit 1
fi
if [ "$failed" -gt "$max_rerun" ]; then
    echo "$failed end-to-end stories failed, more than the $max_rerun that are rerun." >&2
    exit 1
fi

echo "Rerunning $failed failed end-to-end stories with a single worker"
E2E_RERUN=1 "${playwright[@]}" test "$@" --last-failed --workers 1
status=$?
if [ "$status" -eq 0 ]; then
    warn "$failed stories passed only on a rerun with a single worker; the first run's log names them"
fi
exit "$status"
