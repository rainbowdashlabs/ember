#!/bin/sh
# Startup for the dev frontend container: reconcile node_modules with the lock file, then run
# the Nuxt dev server.
#
# The install runs against the bind-mounted source the first time the container starts;
# afterwards the named-volume node_modules survives across restarts. The stamp is what makes
# that safe: a volume filled weeks ago holds whatever package.json wanted then, so a dependency
# added since is simply missing and Nuxt fails at startup asking whether it is installed.
# Recording the lock file it was installed from, and installing again when that no longer
# matches, keeps the volume honest without paying for an install on every start.
#
# Every step announces itself, because the install is the slowest part of a first start and npm
# ci prints nothing for minutes at a time. A container that logs nothing while refusing
# connections on 3000 reads as hung rather than busy, so a heartbeat keeps saying how long the
# install has been going.

set -e

stamp=node_modules/.lock-stamp
want=$(md5sum package-lock.json | cut -d' ' -f1)

if [ "$(cat "$stamp" 2>/dev/null)" = "$want" ]; then
    echo "[frontend] node_modules matches package-lock.json, no install needed."
else
    echo "[frontend] node_modules was installed from a different package-lock.json."
    echo "[frontend] Running npm ci. This takes a few minutes, and the dev server starts after it."

    (
        elapsed=0
        while true; do
            sleep 15
            elapsed=$((elapsed + 15))
            echo "[frontend] npm ci still running (${elapsed}s)"
        done
    ) &
    heartbeat=$!

    if npm ci; then
        kill "$heartbeat" 2>/dev/null || true
        printf %s "$want" > "$stamp"
        echo "[frontend] npm ci finished."
    else
        status=$?
        kill "$heartbeat" 2>/dev/null || true
        echo "[frontend] npm ci failed with exit code ${status}, so the dev server will not start."
        exit "$status"
    fi
fi

echo "[frontend] Starting the Nuxt dev server on port 3000."
exec npm run dev
