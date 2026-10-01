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

# The compose files the e2e profile is brought up from. With E2E_PREBUILT set, the backend comes from
# a distribution `./gradlew installDist` has already built rather than being compiled again inside
# its container, which is what a runner wants and a developer with warm caches does not.
# Playwright's own webServer reads the same variable, so the two cannot disagree.
e2e_compose_files() {
    if [ -n "${E2E_PREBUILT:-}" ]; then
        printf -- "-f compose.dev.yaml -f compose.e2e-prebuilt.yaml"
    else
        printf -- "-f compose.dev.yaml"
    fi
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
  fe-typecheck          vue-tsc for the application and tsc for the stories (silent on success)
  fe-bundle             Type-check and production build, without the formatting, the tests and the
                        linters; CI's build job runs it after fe-lint
  fe-audit              All linters of the registry, non-gating; prints everything they find
  fe-help-index         Rewrite the help centre's search index from the pages that exist
  fe-api-types          Rewrite src/api/generated/schema.ts from the committed API description
  fe-lint [name] [args] Every linter of the registry in scripts/linters.mjs, failing when one does, as
                        the build runs them. With a name only that one: eslint, duplication, icons,
                        helpcenter, help-index, api-types, browser-storage, em-dash, comments,
                        changelog-languages. Trailing arguments reach it, e.g. `fe-lint eslint --fix`
  fe-eslint [args]      ESLint over the frontend; arguments reach it, e.g. `fe-eslint --fix` or
                        `fe-eslint src/views`. `fe-lint eslint` runs it the way the build does
  fe-dev                Dev server
  fe-prepare            Write .nuxt, the generated tsconfig the tests and the type-check need, for
                        a checkout where npm did not run the postinstall hook
  fe-install            npm install - reconciles node_modules and the lock file with package.json,
                        which is how a conflict in the generated lock file is resolved
  fe-install-clean      npm ci - installs exactly what the lock file names and changes nothing, as CI
                        does
  fe-preview [port]     Serve the last build (default port 3000), the steady target for the stories

Frontend tests
  fe-test [args]        Unit, component and SSR tests (vitest run)
  fe-test1 <pattern>    One test file or name pattern, e.g. `fe-test1 MemberName`
  fe-test-watch         vitest in watch mode
  fe-coverage           Tests with coverage and the threshold gate
  fe-e2e [project]      End-to-end tests, default project chromium. Starts the e2e stack (its own
                        database and backend) and serves the last build in front of it; set
                        E2E_NO_SERVER=1 when they already run. Every port is derived from this
                        checkout's path, so a run here takes nothing away from another checkout.
                        With E2E_PREBUILT=1 the backend runs from build/install, rebuilt first
                        unless E2E_PREBUILT_READY=1 says it is already there
  fe-e2e1 <file> [args] One end-to-end spec, e.g. `fe-e2e1 account`
  fe-e2e-group <name>   One of the groups CI runs as a job of its own, e.g. `fe-e2e-group inventory`
  fe-e2e-groups         Check that every story is in exactly one group
  fe-e2e-ssr            The JavaScript-disabled project, which is what proves the public routes
                        really are server-rendered
  fe-e2e-built [proj]   Rebuild the frontend first, then run the stories
  fe-e2e-fresh [proj]   Throw the e2e database away, rebuild the stack and the frontend, and run the
                        stories. The one to use after a backend change: a stack that is already up
                        still runs the sources it started with
  fe-e2e-list           List every end-to-end story without running anything or starting a server
  fe-e2e-report         Open the last end-to-end report
  fe-e2e-install [args] Download the Playwright chromium binary (once per machine); in the nix shell
                        it only says where the shell's browsers are. Add --with-deps on a machine
                        that lacks the system libraries

Backend
  be-verify             Java formatting, the null check, every test suite, the coverage gates, and
                        the javadoc
  be-test               Every test suite, no filter
  be-suite <suite>      One test suite in full, e.g. `be-suite testServices`
  be-dist               Build the runnable distribution into build/install/ember, no tests
  be-test1 <pattern> [suite]
                        One test class, e.g. be-test1 '*PageServiceTest*'. Defaults to the
                        testServices suite. A --tests filter must target a single suite: Gradle
                        fails any suite the pattern matches nothing in.
                        Suites: testServices, testRepositories, testOther, testTracking
  be-refusal-baseline   Rewrite the frozen count of unnamed failures thrown outside the route
                        layer. Never raises a count, so it only ever records progress.
  be-compile            Compile main and test sources
  be-spotless           Apply Java formatting
  be-coverage           The coverage gates on what the last test run recorded, without running the
                        tests: the whole-backend floor, every repository at 95 %, and the lines
                        changed since origin/main at 80 %. Another base with -PcoverageBase=<ref>
  be-report             The null check, then every test suite and the merged JaCoCo report
  be-javadoc            Build the javadoc on its own; be-verify runs it too
  be-spotbugs           Check null use in the main classes: a null where the type says non-null,
                        or a @Nullable value used unchecked. be-verify and be-report run it too;
                        the findings are in build/reports/spotbugs/main.html
  be-rewrite-dry <recipe> [name=value ...]
                        Show what an OpenRewrite recipe of src/rewrite would change, as a patch in
                        build/reports/rewrite/rewrite.patch, without changing anything. Options
                        follow the recipe, e.g.
                        `be-rewrite-dry AddNullMarkedPackageInfo packages=dev.chojo.ember.feature.inventory`
  be-rewrite <recipe> [name=value ...]
                        Apply that recipe to the sources. Run be-spotless afterwards
  be-rewrite-test       The tests of the recipes; be-report runs them too
  be-wrapper <version>  Move the Gradle wrapper to a version, e.g. be-wrapper 9.7.1
  be-federation-version Regenerate the federation contract version
  be-api-spec           Rewrite src/main/resources/api/openapi.json from the route annotations and the
                        records they name, as the API's own mapper writes them
  be-data-tracking      Refresh data_tracking.json from the live DB schema (testcontainer)
  be-data-tracking-check
                        The data tracking suite CI runs, including the check that the committed file
                        is exactly what be-data-tracking would write
  be-cloudflare-ranges  Rewrite the committed snapshot of Cloudflare's edge ranges from cloudflare.com.
                        A running instance fetches the current list on start; the snapshot only
                        answers until that fetch lands

Docker
  docker-frontend       Build the frontend image, as CI's docker job does. It runs the production
                        build only; the linters and the type-check run in fe-build and CI
  docker-backend        Build the backend image
  docker-storage        Start the dev storage stack detached: database on 5432, object storage,
                        SFTP and SMB
  docker-storage-down   Stop it again; arguments reach `compose down`, e.g. -v
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
  docker-e2e-reset      Stop it and throw only its database away, for a database another branch has
                        already migrated further
  docker-e2e-prune      Take down the e2e stacks of checkouts that no longer exist, volumes and all.
                        A deleted worktree leaves gigabytes of gradle cache and database behind
  docker-e2e-restart    Build and start it again, which is how a backend change reaches the stories:
                        a stack that is already up keeps running the sources it started with
  docker-e2e-logs       Follow what the two instances print, which is where a story that cannot
                        reach the second one is read: name one to watch only it, e.g.
                        `docker-e2e-logs ember-e2e-peer`. Into a file it prints what is there and
                        stops, which is how CI keeps them
  docker-app-logs       Follow what the containers print, which is where the first start is
                        watched: `up -d` returns long before the backend has finished building

Combined
  verify                be-verify then fe-build
  api-types             be-api-spec then fe-api-types: the API description and the frontend types
                        after a change to a record the API sends or reads
  format-check          Every Spotless format checked, nothing changed: Java, the frontend sources,
                        the locales and the data tracking file

Parallel checkouts
  docker-app* and docker-storage* drive the one development stack with its fixed names and ports,
  so they take a machine-wide lock and wait for each other. Everything else, the end-to-end stacks
  included, runs in parallel across worktrees. EMBER_TOOLCHAIN_NO_LOCK=1 bypasses the lock.
EOF
}

fe() { cd "$FRONTEND"; }

# The distribution the stories run when E2E_PREBUILT names one, rebuilt before every run.
#
# Nothing else refreshes it: one built in the morning served the stories all evening, and what it
# failed was read as the suite's doing rather than as yesterday's backend. Gradle does nothing when
# nothing changed, so the guard costs a second and removes the question.
e2e_distribution() {
    [ -n "${E2E_PREBUILT:-}" ] && [ -z "${E2E_PREBUILT_READY:-}" ] || return 0
    (cd "$ROOT" && run ./gradlew installDist -x test -q)
}

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
    fe-typecheck)
        # Two of them: the application through Nuxt, and the stories, which its tsconfig never
        # included. A missing import in a spec used to type-check clean and fail only when it ran.
        fe; NODE_OPTIONS="$NODE_HEAP" run npx nuxi typecheck
        fe; run npx tsc -p tsconfig.e2e.json
        ;;
    fe-audit)      fe; NODE_OPTIONS="$NODE_HEAP" run npm run lint:audit ;;
    fe-help-index)
        fe; run node scripts/generate-help-index.mjs
        ;;
    fe-api-types)  fe; run node scripts/generate-api-types.mjs ;;
    fe-lint)       fe; NODE_OPTIONS="$NODE_HEAP" run node scripts/lint.mjs "$@" ;;
    fe-bundle)     fe; NODE_OPTIONS="$NODE_HEAP" run npm run build -- --skip-lint "$@" ;;
    fe-eslint)     fe; NODE_OPTIONS="$NODE_HEAP" run npx eslint "$@" ;;
    fe-dev)        fe; run npm run dev -- "$@" ;;
    fe-install)
        # Reconciles node_modules and the lock file with package.json. Wanted after a merge that
        # touched dependencies: the lock file is generated, so a conflict in it is resolved by
        # writing it again rather than by editing the two sides together.
        fe; NODE_OPTIONS="$NODE_HEAP" run npm install "$@"
        ;;
    fe-install-clean) fe; NODE_OPTIONS="$NODE_HEAP" run npm ci "$@" ;;
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
        e2e_distribution
        fe
        [ -f .output/server/index.mjs ] || NODE_OPTIONS="$NODE_HEAP" run npx nuxi build
        run npx playwright test --project "$project" "$@"
        ;;
    fe-e2e-group)
        # One of the groups CI runs as a job of its own. `fe-e2e-groups` lists them.
        [ $# -ge 1 ] || { echo "fe-e2e-group needs a group, e.g. inventory" >&2; exit 2; }
        group="$1"; shift
        e2e_distribution
        fe
        [ -f .output/server/index.mjs ] || NODE_OPTIONS="$NODE_HEAP" run npx nuxi build
        # shellcheck disable=SC2046
        # The patterns come first, because --project takes every argument that follows it.
        run npx playwright test $(node scripts/e2e-groups.mjs patterns "$group") \
            --project chromium --project ssr-no-js "$@"
        ;;
    fe-e2e-groups)   fe; run node scripts/e2e-groups.mjs check ;;
    fe-e2e1)
        [ $# -ge 1 ] || { echo "fe-e2e1 needs a spec name, e.g. account" >&2; exit 2; }
        spec="$1"; shift
        e2e_distribution
        fe; run npx playwright test "$spec" --project chromium "$@"
        ;;
    fe-e2e-ssr)      e2e_distribution; fe; run npx playwright test --project ssr-no-js "$@" ;;
    fe-e2e-built)
        # Rebuilds first, for when the sources moved since the last build.
        project="${1:-chromium}"; shift || true
        e2e_distribution
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
        e2e_distribution
        cd "$ROOT/docker"
        run docker compose $(e2e_compose_files) --profile e2e down
        run docker volume rm -f "${COMPOSE_PROJECT_NAME:-docker}_ember-e2e-data"
        run bash e2e-pull.sh
        run docker compose $(e2e_compose_files) --profile e2e up -d --build --force-recreate
        fe; NODE_OPTIONS="$NODE_HEAP" run npx nuxi build
        # Whatever follows the project goes in front of --project: a bare argument after it is read
        # as a second project name rather than as the spec to run.
        fe; run npx playwright test "$@" --project "$project"
        ;;
    fe-e2e-list)     fe; E2E_NO_SERVER=1 run npx playwright test --list "$@" ;;
    fe-e2e-report)   fe; run npx playwright show-report e2e/report "$@" ;;
    fe-e2e-install)
        # The nix shell provides the browsers already, so this only has to report where they are.
        # It stays a command because CI runs on an Ubuntu image, where the download is the right
        # answer and PLAYWRIGHT_BROWSERS_PATH is unset. Asked inside the project environment, since
        # that is where the nix shell sets it.
        fe
        run sh -c 'if [ -n "${PLAYWRIGHT_BROWSERS_PATH:-}" ]; then
            echo "Browsers come from the nix shell at $PLAYWRIGHT_BROWSERS_PATH"
        else
            exec npx playwright install chromium "$@"
        fi' sh "$@"
        ;;

    be-verify)
        cd "$ROOT"
        run ./gradlew spotlessJavaApply spotbugsMain testAll jacocoFullReport jacocoCoverageCheck patchCoverageCheck javadoc "$@"
        ;;
    be-test)
        cd "$ROOT"
        run ./gradlew testAll "$@"
        ;;
    be-suite)
        [ $# -ge 1 ] || { echo "be-suite needs a suite, e.g. testServices" >&2; exit 2; }
        cd "$ROOT"; run ./gradlew "$@"
        ;;
    be-dist)       cd "$ROOT"; run ./gradlew installDist "$@" ;;
    be-test1)
        [ $# -ge 1 ] || { echo "be-test1 needs a test pattern, e.g. '*PageServiceTest*'" >&2; exit 2; }
        pattern="$1"; shift
        suite="${1:-testServices}"; shift || true
        cd "$ROOT"
        run ./gradlew "$suite" --tests "$pattern" "$@"
        ;;
    be-refusal-baseline)
        cd "$ROOT"
        run ./gradlew testOther --tests '*RefusalCoverageTest*' \
            -Drefusal.baseline.update=true --rerun-tasks "$@"
        ;;
    be-compile)    cd "$ROOT"; run ./gradlew compileJava compileTestJava "$@" ;;
    be-spotless)   cd "$ROOT"; run ./gradlew spotlessJavaApply "$@" ;;
    be-coverage)   cd "$ROOT"; run ./gradlew jacocoFullReport jacocoCoverageCheck patchCoverageCheck "$@" ;;
    be-report)     cd "$ROOT"; run ./gradlew spotbugsMain testAll jacocoFullReport "$@" ;;
    be-javadoc)    cd "$ROOT"; run ./gradlew javadoc "$@" ;;
    be-spotbugs)   cd "$ROOT"; run ./gradlew spotbugsMain "$@" ;;
    be-rewrite-test) cd "$ROOT"; run ./gradlew testRewrite "$@" ;;
    be-rewrite-dry|be-rewrite)
        [ $# -ge 1 ] || { echo "$cmd needs a recipe, e.g. $cmd AddNullMarkedPackageInfo packages=dev.chojo.ember.feature.inventory" >&2; exit 2; }
        recipe="$1"; shift
        options=""
        for option in "$@"; do
            case "$option" in
                *=*) options="${options:+$options;}$option" ;;
                *) echo "$cmd takes recipe options as name=value, got '$option'" >&2; exit 2 ;;
            esac
        done
        task=rewriteRun
        [ "$cmd" = be-rewrite-dry ] && task=rewriteDryRun
        cd "$ROOT"
        run ./gradlew "$task" "-PrewriteRecipe=$recipe" "-PrewriteOptions=$options" \
            "-Dorg.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g"
        ;;
    be-wrapper)    cd "$ROOT"; run ./gradlew wrapper --gradle-version "$@" ;;
    be-federation-version) cd "$ROOT"; run ./gradlew generateFederationVersion "$@" ;;
    be-api-spec)           cd "$ROOT"; run ./gradlew generateApiSpec "$@" ;;
    be-data-tracking)      cd "$ROOT"; run ./gradlew refreshDataTracking spotlessJsonApply "$@" ;;
    be-data-tracking-check) cd "$ROOT"; run ./gradlew testTracking "$@" ;;
    be-cloudflare-ranges)
        cd "$ROOT"
        ranges=$(run sh -c 'set -e
            v4=$(curl -fsS https://www.cloudflare.com/ips-v4)
            v6=$(curl -fsS https://www.cloudflare.com/ips-v6)
            printf "%s\n%s\n" "$v4" "$v6"')
        printf '%s\n' "$ranges" > src/main/resources/cloudflare-ranges.txt
        ;;

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
        cd "$ROOT/docker"; run bash e2e-pull.sh
        run docker compose $(e2e_compose_files) --profile e2e up -d --build "$@"
        ;;
    docker-e2e-down)
        # -v is allowed again. It was refused while the development and the end-to-end stack were one
        # compose project, where `down -v` under the e2e profile took the dev volumes with it -
        # object storage, the SMB share, the gradle caches - and somebody lost a session's work to
        # it. The end-to-end stack is a project of its own per checkout now, and a project's volumes
        # are prefixed with its name, so the only ones this can reach are the ones it made.
        cd "$ROOT/docker"; run docker compose $(e2e_compose_files) --profile e2e down "$@"
        ;;
    docker-e2e-reset)
        # A stack built from one branch will not start against a database another branch has already
        # migrated further: it reports that the version is ahead and stops. The database is the only
        # thing worth throwing away for that, so this takes it and leaves every other volume alone.
        cd "$ROOT/docker"
        run docker compose $(e2e_compose_files) --profile e2e down
        run docker volume rm -f "${COMPOSE_PROJECT_NAME:-docker}_ember-e2e-data"
        ;;
    docker-e2e-restart)
        # How a backend change reaches the stories: the suite reuses a stack that is already up, and
        # that one is still running the sources as they were when it started.
        cd "$ROOT/docker"
        run bash e2e-pull.sh
        run docker compose $(e2e_compose_files) --profile e2e up -d --build --force-recreate "$@"
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
        follow=""
        [ -t 1 ] && follow="-f"
        cd "$ROOT/docker"; run docker compose $(e2e_compose_files) --profile e2e logs $follow "$@"
        ;;
    docker-app-logs)
        cd "$ROOT/docker"; run docker compose -f compose.dev.yaml --profile full logs -f "$@"
        ;;

    verify)
        "$ROOT/toolchain.sh" be-verify
        "$ROOT/toolchain.sh" fe-build
        ;;
    api-types)
        "$ROOT/toolchain.sh" be-api-spec
        "$ROOT/toolchain.sh" fe-api-types
        ;;
    format-check)  cd "$ROOT"; run ./gradlew spotlessCheck "$@" ;;

    help|-h|--help) usage ;;
    *) echo "Unknown command: $cmd" >&2; echo >&2; usage >&2; exit 2 ;;
esac
