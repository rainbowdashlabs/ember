#!/usr/bin/env bash
#
# Common build and verification commands for this repository.
#
# Every command runs from the repository root regardless of the caller's working
# directory, so callers never need their own `cd`.
#
# Every command also runs inside the project's nix environment via `direnv exec`, so the JDK,
# node and the external binaries the backend shells out to (cwebp, typst, pandoc, libreoffice,
# qpdf) are the versions shell.nix pins, and the *_BIN variables it exports are set. Without
# that, a caller whose shell has not entered the directory silently gets whatever is on PATH -
# which is how WebP variant generation ends up skipped in one run and exercised in the next.
#
# Usage: ./toolchain.sh <command> [args...]
#        ./toolchain.sh <group> <command> [args...]
#        ./toolchain.sh help

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND="$ROOT/frontend"
NODE_HEAP="--max-old-space-size=8192"

# One number for the checkout at the given path: the same on every call from it, and a different one
# for every other path. Both the compose project name and the block of ports come from it, so a
# checkout cannot end up with one checkout's name and another's ports.
checkout_hash() {
    printf '%s' "$1" | cksum | cut -d' ' -f1
}

# The compose project the end-to-end stack of the checkout at the given path runs under.
#
# Derived from the whole absolute path and not from the directory name: worktrees are named after
# what they are for, and two of them are called the same often enough. The directory name is kept in
# front of the digits anyway, so `docker ps` says which checkout a container belongs to without
# anybody having to work it out. A compose project name takes lowercase letters, digits, hyphen and
# underscore, and must start with a letter or a digit.
e2e_project_name() {
    local path="$1" slug
    slug="$(printf '%s' "${path##*/}" | tr '[:upper:]' '[:lower:]' | tr -c 'a-z0-9_-' '-')"
    printf 'ember-e2e-%s-%s' "${slug:0:24}" "$(checkout_hash "$path")"
}

# Gives this checkout an end-to-end stack of its own, and tells the suite where it is.
#
# Every checkout on this machine used to share one stack, because the compose project name falls
# out of the directory name - `docker` in every worktree alike - and the services named their
# containers and their published ports outright. Whoever ran `up` last owned the stack; everybody
# else was then testing somebody else's code against somebody else's database, or watching a
# twenty-minute run be torn down halfway through. Separate stacks remove the question instead of
# scheduling it, which is why the lock no longer covers any of this.
#
# Each checkout gets a block of eight ports, of which four are in use: the spare four are what lets a
# fifth published port be added later without every checkout's block moving. The range sits above
# what a developer machine normally publishes and below 32768, where the kernel starts handing out
# ephemeral ports. Everything downstream reads the ports from here: the compose file publishes them,
# Playwright and its fixtures take the addresses. Nothing is derived twice, so nothing can disagree
# about which port the run is on.
e2e_environment() {
    local base
    base=$((22000 + ($(checkout_hash "$ROOT") % 1000) * 8))

    export COMPOSE_PROJECT_NAME
    COMPOSE_PROJECT_NAME="$(e2e_project_name "$ROOT")"
    export EMBER_E2E_NETWORK="${COMPOSE_PROJECT_NAME}_net"
    export EMBER_E2E_WEB_PORT="$base"
    export EMBER_E2E_API_PORT="$((base + 1))"
    export EMBER_E2E_PEER_PORT="$((base + 2))"
    export EMBER_E2E_DB_PORT="$((base + 3))"
    export E2E_BASE_URL="http://localhost:$EMBER_E2E_WEB_PORT"
    export NUXT_BACKEND_URL="http://localhost:$EMBER_E2E_API_PORT"
    export E2E_PEER_URL="http://localhost:$EMBER_E2E_PEER_PORT"
}

# Runs a command inside the project's direnv/nix environment, falling back to running it
# directly when direnv is not installed so the script still works on a plain checkout.
run() {
    if [ -f "$ROOT/.envrc" ] && command -v direnv >/dev/null 2>&1; then
        # A checkout direnv has not been told to trust refuses every command with its own wording,
        # which reads like a broken toolchain rather than a one-off approval. A fresh git worktree
        # is always in that state, so say what it is and what to do about it, once.
        if [ -z "${DIRENV_APPROVED:-}" ]; then
            if ! direnv exec "$ROOT" true >/dev/null 2>&1; then
                echo "toolchain: this checkout's .envrc has not been approved, so nothing runs in the" >&2
                echo "           project environment. Approve it once with:" >&2
                echo "               direnv allow $ROOT" >&2
                echo "           A fresh git worktree always needs this, even though its .envrc is" >&2
                echo "           identical to the one already approved in the main checkout." >&2
                exit 1
            fi
            DIRENV_APPROVED=1
        fi
        direnv exec "$ROOT" "$@"
    else
        "$@"
    fi
}

usage() {
    cat <<'EOF'
Usage: ./toolchain.sh <command> [args...]
       ./toolchain.sh <group> <command> [args...]

The first hyphen of a name also reads as a space, so `docker app` and `docker-app` are the same
command. `./toolchain.sh docker` lists what is in a group.

Frontend
  fe-build              Full verification: formatting, unit tests, all linters, vue-tsc, build
  fe-format             Apply license headers and whitespace rules to Vue/TypeScript/locales
  fe-typecheck          vue-tsc only (silent on success)
  fe-audit              All linters, non-gating; prints the warning backlog
  fe-help-index         Rewrite the help centre's search index from the pages that exist
  fe-lint <name> [args] One linter, e.g. `fe-lint style` runs scripts/lint-style.mjs. Trailing
                        arguments reach the script, e.g. `fe-lint component-size --error=30`
  fe-dev                Dev server
  fe-prepare            Write .nuxt, the generated tsconfig the tests and the type-check need, for
                        a checkout where npm did not run the postinstall hook
  fe-install            npm install - reconciles node_modules and the lock file with package.json,
                        which is how a conflict in the generated lock file is resolved
  fe-preview [port]     Serve the last build (default port 3000), the steady target for the stories

Frontend tests
  fe-test [args]        Unit, component and SSR tests (vitest run)
  fe-test1 <pattern>    One test file or name pattern, e.g. `fe-test1 MemberName`
  fe-test-watch         vitest in watch mode
  fe-coverage           Tests with coverage and the threshold gate
  fe-e2e [project]      End-to-end tests, default project chromium. Starts the e2e stack (its own
                        database and backend) and serves the last build in front of it; set
                        E2E_NO_SERVER=1 when they already run. Every port is derived from this
                        checkout's path, so a run here takes nothing away from another checkout
  fe-e2e1 <file> [args] One end-to-end spec, e.g. `fe-e2e1 account`
  fe-e2e-ssr            The JavaScript-disabled project, which is what proves the public routes
                        really are server-rendered
  fe-e2e-built [proj]   Rebuild the frontend first, then run the stories
  fe-e2e-fresh [proj]   Throw the e2e database away, rebuild the stack and the frontend, and run the
                        stories. The one to use after a backend change: a stack that is already up
                        still runs the sources it started with
  fe-e2e-list           List every end-to-end story without running anything or starting a server
  fe-e2e-report         Open the last end-to-end report
  fe-e2e-install        Download the Playwright browser binaries (once per machine)

Backend
  be-verify             spotlessApply, all four test suites, and the coverage gate
  be-test               All four test suites, no filter
  be-test1 <pattern> [suite]
                        One test class, e.g. be-test1 '*PageServiceTest*'. Defaults to the
                        testServices suite. A --tests filter must target a single suite: Gradle
                        fails any suite the pattern matches nothing in.
                        Suites: testServices, testRepositories, testOther, testTracking
  be-compile            Compile main and test sources
  be-spotless           Apply Java formatting
  be-coverage           Coverage gate only (needs a prior test run)
  be-report             Generate the full JaCoCo report
  be-javadoc            Build the javadoc, which is its own gate in CI and not part of be-verify
  be-federation-version Regenerate the federation contract version
  be-data-tracking      Refresh data_tracking.json from the live DB schema (testcontainer)

Docker
  docker-frontend       Build the frontend image, as CI's docker job does. Worth running when a
                        linter learns to read something outside frontend/ - the image copies
                        only that directory, so the repository root is not there
  docker-backend        Build the backend image
  docker-storage        Start the dev storage stack detached: database on 5432, object storage,
                        SFTP and SMB. Add `down` arguments through docker-storage-down
  docker-app            Start the whole application from the dev images, detached: the storage
                        stack, the backend on 8888 and the frontend on 3000, both built and run
                        inside their containers from this checkout. The images are rebuilt first,
                        or compose starts the one it built last time. The frontend takes port 3000,
                        so fe-dev cannot run beside it
  docker-app-down       Stop the application again. The data survives; add -v to throw it away
  docker-app-restart    Build and start the containers again, which is how a change is picked up:
                        the backend compiles on start. Name one to restart only that, e.g.
                        `docker-app-restart ember`
  docker-e2e            Start the stack the stories run against, detached: one database, two
                        instances of the application on it, and the three storage services they
                        switch a station between. One stack per checkout, on a compose project and
                        a block of ports derived from this checkout's path, so several checkouts
                        run the stories at once without meeting. The suite starts it itself when it
                        is down, so this is for having it up in advance
  docker-e2e-down       Stop it again. Add -v to throw this checkout's e2e volumes away with it
  docker-e2e-prune      Take down the e2e stacks of checkouts that no longer exist, volumes and all.
                        A deleted worktree leaves gigabytes of gradle cache and database behind
  docker-e2e-restart    Build and start it again, which is how a backend change reaches the stories:
                        a stack that is already up keeps running the sources it started with
  docker-e2e-logs       Follow what the two instances print, which is where a story that cannot
                        reach the second one is read: name one to watch only it, e.g.
                        `docker-e2e-logs ember-e2e-peer`
  docker-app-logs       Follow what the containers print, which is where the first start is
                        watched: `up -d` returns long before the backend has finished building

Combined
  verify                be-verify then fe-build

Parallel checkouts
  The end-to-end and docker commands bind fixed ports and drive one shared stack, so they take a
  machine-wide lock and wait for each other. Everything else runs in parallel across worktrees.
  EMBER_TOOLCHAIN_NO_LOCK=1 bypasses it.
EOF
}

fe() { cd "$FRONTEND"; }

# The command names are hyphenated, and the first hyphen also reads as a group: `docker app` is
# accepted for `docker-app`, and both reach the same arm below. Naming the group alone lists what
# is in it.
COMMAND_GROUPS=(fe be docker)

is_group() {
    local candidate
    for candidate in "${COMMAND_GROUPS[@]}"; do
        [ "$1" = "$candidate" ] && return 0
    done
    return 1
}

# What is in a group, read back out of the case arms below so the listing cannot drift from what
# actually runs.
list_group() {
    sed -n 's/^    \([a-z][a-z0-9|_-]*\)).*/\1/p' "$ROOT/toolchain.sh" |
        tr '|' '\n' | sed -n "s/^$1-//p"
}

# `docker app` becomes `docker-app` before anything else looks at it, so the arms below only ever
# see one spelling. A group on its own lists what is in it rather than failing as unknown.
if is_group "${1:-}"; then
    if [ $# -ge 2 ]; then
        set -- "$1-$2" "${@:3}"
    else
        echo "Commands in '$1':" >&2
        list_group "$1" | sed "s|^|  $1 |" >&2
        exit 2
    fi
fi

cmd="${1:-help}"
shift || true

# Set EMBER_TOOLCHAIN_NO_LOCK=1 to bypass it, for a machine where the ports are known to be free.
#
# The path is fixed rather than taken from TMPDIR. What is being guarded is machine-wide. A lock that
# followed TMPDIR would give every caller with its own temporary directory a lock of its own, and
# callers holding different locks do not wait for one another at all, which is a lock that reads as
# working while guarding nothing.
LOCKFILE="/tmp/ember-toolchain.lock"

# What is left to guard is the development stack, and only that. It is the one stack still shared:
# a person runs it, it keeps its fixed container names and its fixed ports on purpose, and every
# checkout on this machine aims `docker-app` and `docker-storage` at that same one.
#
# The end-to-end commands are deliberately outside the lock. Each checkout's stack now has its own
# compose project, its own network and its own block of ports, so two of them running the stories at
# the same moment never meet: no container name, no port and no volume is shared between them.
# Making the second one queue would cost it the first one's twenty minutes and prevent nothing.
needs_lock() {
    case "$1" in
        docker-app-logs) return 1 ;;
        docker-app* | docker-storage*) return 0 ;;
        *) return 1 ;;
    esac
}

if [ -z "${EMBER_TOOLCHAIN_LOCKED:-}" ] && [ -z "${EMBER_TOOLCHAIN_NO_LOCK:-}" ] &&
    needs_lock "$cmd" && command -v flock >/dev/null 2>&1; then
    export EMBER_TOOLCHAIN_LOCKED=1
    if ! flock -n "$LOCKFILE" true 2>/dev/null; then
        echo "toolchain: another checkout is running '$cmd' or a sibling; waiting for the lock." >&2
    fi
    exec flock "$LOCKFILE" "$0" "$cmd" "$@"
fi

# Before anything reaches compose or Playwright, so that the stack a command starts and the stack the
# stories look for are the same one. Playwright starts the stack itself through its `webServer`, and
# it inherits this.
case "$cmd" in
    fe-e2e* | docker-e2e*) e2e_environment ;;
esac

case "$cmd" in
    fe-build)
        # Formatting first, mirroring be-verify: the frontend formats are Spotless tasks, so
        # nothing in the npm chain would ever see them.
        cd "$ROOT"; run ./gradlew formatFrontend
        # Then the unit tests, which take seconds and fail on the thing a linter cannot see. The
        # npm build carries the linters, the type-check and the production build after them.
        fe; NODE_OPTIONS="$NODE_HEAP" run npx vitest run
        fe; NODE_OPTIONS="$NODE_HEAP" run npm run build
        ;;
    fe-format)     cd "$ROOT"; run ./gradlew formatFrontend "$@" ;;
    fe-typecheck)  fe; NODE_OPTIONS="$NODE_HEAP" run npx nuxi typecheck ;;
    fe-audit)      fe; NODE_OPTIONS="$NODE_HEAP" run npm run lint:audit ;;
    fe-help-index)
        fe; run node scripts/generate-help-index.mjs
        ;;
    fe-lint)
        [ $# -ge 1 ] || { echo "fe-lint needs a linter name, e.g. style" >&2; exit 2; }
        linter="$1"; shift
        fe; run node "scripts/lint-$linter.mjs" "$@"
        ;;
    fe-dev)        fe; run npm run dev -- "$@" ;;
    fe-install)
        # Reconciles node_modules and the lock file with package.json. Wanted after a merge that
        # touched dependencies: the lock file is generated, so a conflict in it is resolved by
        # writing it again rather than by editing the two sides together.
        fe; NODE_OPTIONS="$NODE_HEAP" run npm install "$@"
        ;;
    fe-prepare)
        # Writes .nuxt, which holds the tsconfig the tests and the type-check resolve against.
        # npm does it on install through the postinstall hook, so this is for the checkout where
        # that hook did not run: a fresh worktree, or an install that left package scripts pending.
        fe; NODE_OPTIONS="$NODE_HEAP" run npx nuxi prepare "$@"
        ;;
    fe-preview)
        # Serves the last build. Unlike the dev server this compiles nothing on demand, which is
        # what makes it a steady target for the end-to-end suite.
        fe; NITRO_PORT="${1:-3000}" run node .output/server/index.mjs
        ;;

    fe-test)       fe; NODE_OPTIONS="$NODE_HEAP" run npx vitest run "$@" ;;
    fe-test1)
        [ $# -ge 1 ] || { echo "fe-test1 needs a file or name pattern, e.g. MemberName" >&2; exit 2; }
        pattern="$1"; shift
        fe; NODE_OPTIONS="$NODE_HEAP" run npx vitest run "$pattern" "$@"
        ;;
    fe-test-watch) fe; NODE_OPTIONS="$NODE_HEAP" run npx vitest "$@" ;;
    fe-coverage)   fe; NODE_OPTIONS="$NODE_HEAP" run npx vitest run --coverage "$@" ;;
    fe-e2e)
        # The suite serves the last build; build once when there is none yet. After changing
        # anything under src/, use fe-e2e-built - this command would otherwise run the stories
        # against the build before the change and report on code nobody is looking at.
        project="${1:-chromium}"; shift || true
        fe
        [ -f .output/server/index.mjs ] || NODE_OPTIONS="$NODE_HEAP" run npx nuxi build
        run npx playwright test --project "$project" "$@"
        ;;
    fe-e2e1)
        [ $# -ge 1 ] || { echo "fe-e2e1 needs a spec name, e.g. account" >&2; exit 2; }
        spec="$1"; shift
        fe; run npx playwright test "$spec" --project chromium "$@"
        ;;
    fe-e2e-ssr)      fe; run npx playwright test --project ssr-no-js "$@" ;;
    fe-e2e-built)
        # Rebuilds first, for when the sources moved since the last build.
        project="${1:-chromium}"; shift || true
        fe; NODE_OPTIONS="$NODE_HEAP" run npx nuxi build
        # Whatever follows the project goes in front of --project: a bare argument after it is read
        # as a second project name rather than as the spec to run.
        fe; run npx playwright test "$@" --project "$project"
        ;;
    fe-e2e-fresh)
        # Throws the database away, builds the backend again and runs the stories, which is the one
        # command to reach for after a backend change: a stack that is already up is still running
        # the sources it started with, and a database another branch migrated further refuses the
        # backend of this one outright. It has no other checkout to fear any more, since the stack
        # it restarts is this checkout's own.
        project="${1:-chromium}"; shift || true
        cd "$ROOT/docker"
        run docker compose -f compose.dev.yaml --profile e2e down
        run docker volume rm -f "${COMPOSE_PROJECT_NAME:-docker}_ember-e2e-data"
        run docker compose -f compose.dev.yaml --profile e2e up -d --build --force-recreate
        fe; NODE_OPTIONS="$NODE_HEAP" run npx nuxi build
        # Whatever follows the project goes in front of --project: a bare argument after it is read
        # as a second project name rather than as the spec to run.
        fe; run npx playwright test "$@" --project "$project"
        ;;
    fe-e2e-list)     fe; E2E_NO_SERVER=1 run npx playwright test --list "$@" ;;
    fe-e2e-report)   fe; run npx playwright show-report e2e/report "$@" ;;
    fe-e2e-install)
        # The nix shell provides the browsers already, so this only has to report where they are.
        # It stays a command because CI runs on a Debian image, where the download is the right
        # answer and PLAYWRIGHT_BROWSERS_PATH is unset.
        fe
        if [ -n "${PLAYWRIGHT_BROWSERS_PATH:-}" ] || [ -f "$ROOT/shell.nix" ]; then
            run sh -c 'echo "Browsers come from the nix shell at $PLAYWRIGHT_BROWSERS_PATH"'
        else
            run npx playwright install --with-deps chromium "$@"
        fi
        ;;

    be-verify)
        cd "$ROOT"
        run ./gradlew spotlessJavaApply testRepositories testServices testOther testTracking jacocoCoverageCheck "$@"
        ;;
    be-test)
        cd "$ROOT"
        run ./gradlew testRepositories testServices testOther testTracking "$@"
        ;;
    be-test1)
        [ $# -ge 1 ] || { echo "be-test1 needs a test pattern, e.g. '*PageServiceTest*'" >&2; exit 2; }
        pattern="$1"; shift
        suite="${1:-testServices}"; shift || true
        cd "$ROOT"
        run ./gradlew "$suite" --tests "$pattern" "$@"
        ;;
    be-compile)    cd "$ROOT"; run ./gradlew compileJava compileTestJava "$@" ;;
    be-spotless)   cd "$ROOT"; run ./gradlew spotlessJavaApply "$@" ;;
    be-coverage)   cd "$ROOT"; run ./gradlew jacocoCoverageCheck "$@" ;;
    be-report)     cd "$ROOT"; run ./gradlew jacocoFullReport "$@" ;;
    be-javadoc)    cd "$ROOT"; run ./gradlew javadoc "$@" ;;
    be-federation-version) cd "$ROOT"; run ./gradlew generateFederationVersion "$@" ;;
    be-data-tracking)      cd "$ROOT"; run ./gradlew refreshDataTracking "$@" ;;

    docker-frontend) cd "$ROOT"; run docker build . -f docker/frontend.Dockerfile "$@" ;;
    docker-backend)  cd "$ROOT"; run docker build . -f docker/backend.Dockerfile "$@" ;;
    docker-storage)
        cd "$ROOT/docker"; run docker compose -f compose.dev.yaml --profile storage up -d "$@"
        ;;
    docker-storage-down)
        cd "$ROOT/docker"; run docker compose -f compose.dev.yaml --profile storage down "$@"
        ;;
    docker-app)
        # Build first, or compose starts whatever image was built the last time and the app runs
        # on code nobody is looking at any more.
        cd "$ROOT/docker"; run docker compose -f compose.dev.yaml --profile full up -d --build "$@"
        ;;
    docker-app-down)
        cd "$ROOT/docker"; run docker compose -f compose.dev.yaml --profile full down "$@"
        ;;
    docker-e2e)
        cd "$ROOT/docker"; run docker compose -f compose.dev.yaml --profile e2e up -d --build "$@"
        ;;
    docker-e2e-down)
        # -v is allowed again. It was refused while the development and the end-to-end stack were one
        # compose project, where `down -v` under the e2e profile took the dev volumes with it -
        # object storage, the SMB share, the gradle caches - and somebody lost a session's work to
        # it. The end-to-end stack is a project of its own per checkout now, and a project's volumes
        # are prefixed with its name, so the only ones this can reach are the ones it made.
        cd "$ROOT/docker"; run docker compose -f compose.dev.yaml --profile e2e down "$@"
        ;;
    docker-e2e-reset)
        # A stack built from one branch will not start against a database another branch has already
        # migrated further: it reports that the version is ahead and stops. The database is the only
        # thing worth throwing away for that, so this takes it and leaves every other volume alone.
        cd "$ROOT/docker"
        run docker compose -f compose.dev.yaml --profile e2e down
        run docker volume rm -f "${COMPOSE_PROJECT_NAME:-docker}_ember-e2e-data"
        ;;
    docker-e2e-restart)
        # How a backend change reaches the stories: the suite reuses a stack that is already up, and
        # that one is still running the sources as they were when it started.
        cd "$ROOT/docker"
        run docker compose -f compose.dev.yaml --profile e2e up -d --build --force-recreate "$@"
        ;;
    docker-app-restart)
        # An up rather than a restart, because `docker compose restart` takes no --build: it starts
        # the containers again as they are, which is the one thing a restart after a change must
        # not do. Recreating them costs nothing, since the caches live in named volumes.
        cd "$ROOT/docker"
        run docker compose -f compose.dev.yaml --profile full up -d --build --force-recreate "$@"
        ;;
    docker-e2e-prune)
        # A stack per checkout means a checkout that is deleted leaves one behind, and nothing else
        # ever takes it away: two gradle caches, a build directory, a database and a data directory
        # per instance, which is gigabytes each. The project name says which checkout a stack belongs
        # to, so the ones still wanted are exactly the ones derived from a worktree that is still
        # there. Everything else carrying an `ember-e2e-` project goes, volumes and all.
        live=""
        while IFS= read -r path; do
            [ -n "$path" ] || continue
            live="$live $(e2e_project_name "$path")"
        done < <(git -C "$ROOT" worktree list --porcelain | sed -n 's/^worktree //p')

        # Volumes outlive their containers, so both are asked. They also answer differently: a
        # container hands out one label by name, a volume only the whole set as one string.
        found=$(
            {
                docker ps -a --format '{{.Label "com.docker.compose.project"}}'
                docker volume ls --format '{{.Labels}}' | tr ',' '\n' |
                    sed -n 's/^com.docker.compose.project=//p'
            } | sort -u | grep '^ember-e2e-' || true
        )

        for project in $found; do
            case " $live " in *" $project "*) continue ;; esac
            echo "toolchain: removing the stack of a checkout that is gone: $project"
            EMBER_E2E_NETWORK="${project}_net" run docker compose \
                -f "$ROOT/docker/compose.dev.yaml" -p "$project" --profile e2e down -v --remove-orphans
        done
        ;;
    docker-e2e-logs)
        cd "$ROOT/docker"; run docker compose -f compose.dev.yaml --profile e2e logs -f "$@"
        ;;
    docker-app-logs)
        cd "$ROOT/docker"; run docker compose -f compose.dev.yaml --profile full logs -f "$@"
        ;;

    verify)
        "$ROOT/toolchain.sh" be-verify
        "$ROOT/toolchain.sh" fe-build
        ;;

    help|-h|--help) usage ;;
    *) echo "Unknown command: $cmd" >&2; echo >&2; usage >&2; exit 2 ;;
esac
