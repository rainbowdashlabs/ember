#!/usr/bin/env bash
# Fetches the images the end-to-end stack does not build itself, and keeps trying.
#
# A registry that resets the connection mid-handshake is not a broken branch, but `compose up` pulls
# as its first act and dies with the pull, so one reset on a runner failed a whole shard and read
# like a test failure. Pulling here first means the reset costs a retry instead of a run.
#
# It never fails the caller: whatever could not be fetched, the images may already be on the machine
# and the stack is what decides whether it can start. Anything genuinely missing is reported by the
# `up` that follows, where it belongs.
set -uo pipefail

compose_file="$(cd "$(dirname "$0")" && pwd)/compose.dev.yaml"
attempts=4
delay=5

for attempt in $(seq 1 "$attempts"); do
    if docker compose -f "$compose_file" --profile e2e pull --ignore-buildable --quiet; then
        exit 0
    fi
    if [ "$attempt" -lt "$attempts" ]; then
        echo "e2e-pull: the registry did not answer, trying again in ${delay}s ($attempt/$attempts)" >&2
        sleep "$delay"
        delay=$((delay * 2))
    fi
done

echo "e2e-pull: giving up on the registry, starting with whatever is on this machine" >&2
exit 0
