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
# that, a caller whose shell has not entered the directory silently gets whatever is on PATH —
# which is how WebP variant generation ends up skipped in one run and exercised in the next.
#
# Usage: ./toolchain.sh <command> [args...]
#        ./toolchain.sh help

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND="$ROOT/frontend"
NODE_HEAP="--max-old-space-size=8192"

# Runs a command inside the project's direnv/nix environment, falling back to running it
# directly when direnv is not installed so the script still works on a plain checkout.
run() {
    if [ -f "$ROOT/.envrc" ] && command -v direnv >/dev/null 2>&1; then
        direnv exec "$ROOT" "$@"
    else
        "$@"
    fi
}

usage() {
    cat <<'EOF'
Usage: ./toolchain.sh <command> [args...]

Frontend
  fe-build              Full verification: formatting, unit tests, all linters, vue-tsc, build
  fe-format             Apply license headers and whitespace rules to Vue/TypeScript/locales
  fe-typecheck          vue-tsc only (silent on success)
  fe-audit              All linters, non-gating; prints the warning backlog
  fe-lint <name> [args] One linter, e.g. `fe-lint style` runs scripts/lint-style.mjs. Trailing
                        arguments reach the script, e.g. `fe-lint component-size --error=30`
  fe-dev                Dev server

Frontend tests
  fe-test [args]        Unit, component and SSR tests (vitest run)
  fe-test1 <pattern>    One test file or name pattern, e.g. `fe-test1 MemberName`
  fe-test-watch         vitest in watch mode
  fe-coverage           Tests with coverage and the threshold gate
  fe-e2e [project]      End-to-end tests, default project chromium. Starts the dev stack and the
                        Nuxt server itself; set E2E_NO_SERVER=1 when they already run
  fe-e2e1 <file> [args] One end-to-end spec, e.g. `fe-e2e1 account`
  fe-e2e-ssr            The JavaScript-disabled project, which is what proves the public routes
                        really are server-rendered
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
  be-federation-version Regenerate the federation contract version
  be-data-tracking      Refresh data_tracking.json from the live DB schema (testcontainer)

Docker
  docker-frontend       Build the frontend image, as CI's docker job does. Worth running when a
                        linter learns to read something outside frontend/ — the image copies
                        only that directory, so the repository root is not there
  docker-backend        Build the backend image

Combined
  verify                be-verify then fe-build
EOF
}

fe() { cd "$FRONTEND"; }

cmd="${1:-help}"
shift || true

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
    fe-lint)
        [ $# -ge 1 ] || { echo "fe-lint needs a linter name, e.g. style" >&2; exit 2; }
        linter="$1"; shift
        fe; run node "scripts/lint-$linter.mjs" "$@"
        ;;
    fe-dev)        fe; run npm run dev -- "$@" ;;

    fe-test)       fe; NODE_OPTIONS="$NODE_HEAP" run npx vitest run "$@" ;;
    fe-test1)
        [ $# -ge 1 ] || { echo "fe-test1 needs a file or name pattern, e.g. MemberName" >&2; exit 2; }
        pattern="$1"; shift
        fe; NODE_OPTIONS="$NODE_HEAP" run npx vitest run "$pattern" "$@"
        ;;
    fe-test-watch) fe; NODE_OPTIONS="$NODE_HEAP" run npx vitest "$@" ;;
    fe-coverage)   fe; NODE_OPTIONS="$NODE_HEAP" run npx vitest run --coverage "$@" ;;
    fe-e2e)
        project="${1:-chromium}"; shift || true
        fe; run npx playwright test --project "$project" "$@"
        ;;
    fe-e2e1)
        [ $# -ge 1 ] || { echo "fe-e2e1 needs a spec name, e.g. account" >&2; exit 2; }
        spec="$1"; shift
        fe; run npx playwright test "$spec" --project chromium "$@"
        ;;
    fe-e2e-ssr)      fe; run npx playwright test --project ssr-no-js "$@" ;;
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
    be-federation-version) cd "$ROOT"; run ./gradlew generateFederationVersion "$@" ;;
    be-data-tracking)      cd "$ROOT"; run ./gradlew refreshDataTracking "$@" ;;

    docker-frontend) cd "$ROOT"; run docker build . -f docker/frontend.Dockerfile "$@" ;;
    docker-backend)  cd "$ROOT"; run docker build . -f docker/backend.Dockerfile "$@" ;;

    verify)
        "$ROOT/toolchain.sh" be-verify
        "$ROOT/toolchain.sh" fe-build
        ;;

    help|-h|--help) usage ;;
    *) echo "Unknown command: $cmd" >&2; echo >&2; usage >&2; exit 2 ;;
esac
