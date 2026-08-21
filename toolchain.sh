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
       ./toolchain.sh <group> <command> [args...]

The first hyphen of a name also reads as a space, so `docker app` and `docker-app` are the same
command. `./toolchain.sh docker` lists what is in a group.

Frontend
  fe-build              Full verification: formatting, unit tests, all linters, vue-tsc, build
  fe-format             Apply license headers and whitespace rules to Vue/TypeScript/locales
  fe-typecheck          vue-tsc only (silent on success)
  fe-audit              All linters, non-gating; prints the warning backlog
  fe-lint <name> [args] One linter, e.g. `fe-lint style` runs scripts/lint-style.mjs. Trailing
                        arguments reach the script, e.g. `fe-lint component-size --error=30`
  fe-dev                Dev server
  fe-install            npm install - reconciles node_modules and the lock file with package.json,
                        which is how a conflict in the generated lock file is resolved
  fe-preview [port]     Serve the last build (default port 3000), the steady target for the stories

Frontend tests
  fe-test [args]        Unit, component and SSR tests (vitest run)
  fe-test1 <pattern>    One test file or name pattern, e.g. `fe-test1 MemberName`
  fe-test-watch         vitest in watch mode
  fe-coverage           Tests with coverage and the threshold gate
  fe-e2e [project]      End-to-end tests, default project chromium. Starts the e2e stack (its own
                        database and backend on 8899) and serves the last build on 3010; set
                        E2E_NO_SERVER=1 when they already run
  fe-e2e1 <file> [args] One end-to-end spec, e.g. `fe-e2e1 account`
  fe-e2e-ssr            The JavaScript-disabled project, which is what proves the public routes
                        really are server-rendered
  fe-e2e-built [proj]   Rebuild the frontend first, then run the stories
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
  docker-app-logs       Follow what the containers print, which is where the first start is
                        watched: `up -d` returns long before the backend has finished building

Combined
  verify                be-verify then fe-build

Shell
  completion install [shell]
                        Install tab completion where the shell already looks, so nothing has to be
                        added to an rc file. Run it once; open a new shell and it is there
  completion uninstall [shell]
                        Remove what install wrote
  completion [zsh|bash] Print the function instead of installing it, to eval or to inspect
                        Shell defaults to the one named in $SHELL
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

# --- Completion ----------------------------------------------------------------------------------
#
# The candidates are worked out here rather than in the emitted shell function, so both shells get
# the same answers and neither has to know anything about this repository.

# Every command the case below dispatches, read back out of this file. A new command is completable
# the moment it is written, and one that is removed stops being offered, without a list to update.
# The flag spellings of help are dropped: they work, but nobody needs them offered.
list_commands() {
    sed -n 's/^    \([a-z][a-z0-9|_-]*\)).*/\1/p' "$ROOT/toolchain.sh" | tr '|' '\n' | grep -v '^-'
}

# What can be typed first: the groups, then whatever belongs to no group.
list_top_level() {
    printf '%s\n' "${COMMAND_GROUPS[@]}"
    list_commands | grep -vE "^($(IFS='|'; echo "${COMMAND_GROUPS[*]}"))-"
}

# What can follow a group, with the group's own prefix taken off.
list_group() {
    list_commands | sed -n "s/^$1-//p"
}

# Prints the part of each matching path that names it, e.g. lint-style.mjs -> style. A glob that
# matches nothing yields the pattern itself, which -e filters out.
list_names() {
    local strip_prefix="$1" strip_suffix="$2" path name
    shift 2
    for path in "$@"; do
        [ -e "$path" ] || continue
        name="${path##*/}"
        name="${name#"$strip_prefix"}"
        printf '%s\n' "${name%"$strip_suffix"}"
    done
}

list_e2e_projects() {
    sed -n "s/.*name: *'\([a-z0-9-]*\)'.*/\1/p" "$FRONTEND/playwright.config.ts" 2> /dev/null
}

list_compose_services() {
    awk '/^services:/ {inside = 1; next}
         /^[a-z]/ {inside = 0}
         inside && /^  [a-z0-9_-]+:/ {gsub(/[ :]/, ""); print}' \
        "$ROOT/docker/compose.dev.yaml" 2> /dev/null
}

list_test_classes() {
    find "$ROOT/src/test/java" -name '*Test.java' -printf '%f\n' 2> /dev/null | sed 's/\.java$//'
}

# Candidates for argument $2 (1 for the first) of command $1, where $1 is always the hyphenated
# name. Silence means "no idea", which the shells turn into their own default of completing
# filenames.
complete_args() {
    local cmd="$1" position="$2"
    case "$cmd" in
        fe-lint)
            [ "$position" = 1 ] && list_names "lint-" ".mjs" "$FRONTEND"/scripts/lint-*.mjs
            ;;
        fe-e2e1)
            [ "$position" = 1 ] && list_names "" ".e2e.ts" "$FRONTEND"/e2e/*.e2e.ts
            ;;
        fe-e2e|fe-e2e-built)
            [ "$position" = 1 ] && list_e2e_projects
            ;;
        be-test1)
            case "$position" in
                1) list_test_classes ;;
                2) printf '%s\n' testServices testRepositories testOther testTracking ;;
            esac
            ;;
        docker-app-restart|docker-app-logs)
            list_compose_services
            ;;
        completion)
            case "$position" in
                1) printf '%s\n' install uninstall zsh bash ;;
                2) printf '%s\n' zsh bash ;;
            esac
            ;;
    esac
    return 0
}

# Candidates for the word after the ones already typed, which the shells hand over verbatim. Both
# spellings arrive here, so `docker app <TAB>` and `docker-app <TAB>` complete alike.
complete_words() {
    if [ $# -eq 0 ]; then
        list_top_level
    elif is_group "$1"; then
        if [ $# -eq 1 ]; then
            list_group "$1"
        else
            # The group and its subcommand are two words for one name, so the first argument
            # after them is still argument one.
            complete_args "$1-$2" "$(($# - 1))"
        fi
    else
        complete_args "$1" "$#"
    fi
}

# Where each shell looks by itself, so that installing means dropping a file rather than asking
# anyone to edit an rc file. On a nix or home-manager machine the rc file is a read-only symlink
# into the store, and an appended line there would be lost at the next rebuild anyway.
#
# For zsh: the first directory that is both on fpath and writable. oh-my-zsh keeps one under its
# cache for exactly this, and a plain zsh usually has ~/.local/share/zsh/site-functions.
zsh_completion_dir() {
    local dir
    while read -r dir; do
        [ -n "$dir" ] && [ -d "$dir" ] && [ -w "$dir" ] && { printf '%s\n' "$dir"; return 0; }
    done < <(zsh -ic 'print -l $fpath' 2> /dev/null)
    return 1
}

bash_completion_dir() {
    printf '%s\n' "${XDG_DATA_HOME:-$HOME/.local/share}/bash-completion/completions"
}

# The file zsh autoloads. The #compdef line is what registers it, which is why nothing has to run
# at shell start. All three spellings are named because zsh looks the command up as it was typed.
print_zsh_function() {
    local self="$ROOT/toolchain.sh"
    cat <<EOF
#compdef toolchain.sh ./toolchain.sh $self
# Written by \`toolchain.sh completion install\`. Run that again after moving the checkout.

local script=$self
[[ -x \$script ]] || return 1

local -a candidates
candidates=(\${(f)"\$(\$script __complete \${words[2,CURRENT-1]})"})
(( \${#candidates} )) && compadd -- \$candidates
EOF
}

install_completion() {
    local shell="${1:-$(basename "${SHELL:-bash}")}" dir target
    case "$shell" in
        zsh)
            dir=$(zsh_completion_dir) || {
                echo "No writable directory on zsh's fpath." >&2
                echo "Add one to fpath, then run this again:" >&2
                echo "  fpath+=(\$HOME/.local/share/zsh/site-functions)" >&2
                return 1
            }
            target="$dir/_toolchain"
            print_zsh_function > "$target"
            # compinit reads a cached dump and would not notice a new file until it expires.
            rm -f "$HOME"/.zcompdump*
            echo "Installed $target"
            echo "Open a new shell and the completion is there."
            ;;
        bash)
            dir=$(bash_completion_dir)
            mkdir -p "$dir"
            # bash-completion loads a file named after the command, on first use of that command.
            target="$dir/toolchain.sh"
            print_completion bash > "$target"
            echo "Installed $target"
            echo "Open a new shell and the completion is there (needs the bash-completion package)."
            ;;
        *) echo "No completion for '$shell'. Supported: zsh, bash" >&2; return 2 ;;
    esac
}

uninstall_completion() {
    local shell="${1:-$(basename "${SHELL:-bash}")}" dir target
    case "$shell" in
        zsh)
            dir=$(zsh_completion_dir) || return 0
            target="$dir/_toolchain"
            ;;
        bash) target="$(bash_completion_dir)/toolchain.sh" ;;
        *) echo "No completion for '$shell'. Supported: zsh, bash" >&2; return 2 ;;
    esac
    if [ -f "$target" ]; then
        rm -f "$target"
        [ "$shell" = zsh ] && rm -f "$HOME"/.zcompdump*
        echo "Removed $target"
    else
        echo "Nothing installed at $target"
    fi
}

# The emitted functions call back into this script, so they stay this short and never go stale.
print_completion() {
    local shell="${1:-$(basename "${SHELL:-bash}")}" self="$ROOT/toolchain.sh"
    case "$shell" in
        zsh)
            cat <<EOF
_toolchain() {
    local -a candidates
    candidates=(\${(f)"\$('$self' __complete \${words[2,CURRENT-1]})"})
    (( \${#candidates} )) && compadd -- \$candidates
}
compdef _toolchain toolchain.sh ./toolchain.sh '$self'
EOF
            ;;
        bash)
            cat <<EOF
_toolchain() {
    local candidates
    candidates="\$('$self' __complete "\${COMP_WORDS[@]:1:COMP_CWORD-1}")"
    mapfile -t COMPREPLY < <(compgen -W "\$candidates" -- "\${COMP_WORDS[COMP_CWORD]}")
}
complete -F _toolchain toolchain.sh ./toolchain.sh '$self'
EOF
            ;;
        *) echo "No completion for '$shell'. Supported: zsh, bash" >&2; return 2 ;;
    esac
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
    fe-install)
        # Reconciles node_modules and the lock file with package.json. Wanted after a merge that
        # touched dependencies: the lock file is generated, so a conflict in it is resolved by
        # writing it again rather than by editing the two sides together.
        fe; NODE_OPTIONS="$NODE_HEAP" run npm install "$@"
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
        fe; run npx playwright test --project "$project" "$@"
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
    docker-app-restart)
        # An up rather than a restart, because `docker compose restart` takes no --build: it starts
        # the containers again as they are, which is the one thing a restart after a change must
        # not do. Recreating them costs nothing, since the caches live in named volumes.
        cd "$ROOT/docker"
        run docker compose -f compose.dev.yaml --profile full up -d --build --force-recreate "$@"
        ;;
    docker-app-logs)
        cd "$ROOT/docker"; run docker compose -f compose.dev.yaml --profile full logs -f "$@"
        ;;

    verify)
        "$ROOT/toolchain.sh" be-verify
        "$ROOT/toolchain.sh" fe-build
        ;;

    completion)
        case "${1:-}" in
            install)   shift; install_completion "$@" ;;
            uninstall) shift; uninstall_completion "$@" ;;
            *)         print_completion "$@" ;;
        esac
        ;;
    # Not in the command list: the shells call it, nobody types it.
    __complete)   complete_words "$@" ;;

    help|-h|--help) usage ;;
    *) echo "Unknown command: $cmd" >&2; echo >&2; usage >&2; exit 2 ;;
esac
