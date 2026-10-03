# shellcheck shell=bash disable=SC2034
# What the script tests share: a scratch repository with an origin of its own, and the checks that
# report each case. Sourced by every test file; run.sh runs them.

SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FAILURES=0
CASES=0

export GIT_AUTHOR_NAME=test GIT_AUTHOR_EMAIL=test@example.org
export GIT_COMMITTER_NAME=test GIT_COMMITTER_EMAIL=test@example.org
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1

# Creates a repository at version 26.20.0 with patch_1 and patch_2, released as v26.20.0, in a fresh
# directory that becomes the working directory. Its origin is a bare repository next to it.
new_repository() {
    local scratch
    scratch=$(mktemp -d)
    TEST_SCRATCH_DIRS+=("$scratch")
    git init -q --bare -b main "$scratch/origin.git"
    git init -q -b main "$scratch/work"
    cd "$scratch/work" || exit 1
    git remote add origin "$scratch/origin.git"
    git config commit.gpgsign false
    git config tag.gpgsign false
    mkdir -p src/main/resources/database/postgresql/1 src/test/java
    printf 'create table a ();\n' > src/main/resources/database/postgresql/1/patch_1.sql
    printf 'create table b ();\n' > src/main/resources/database/postgresql/1/patch_2.sql
    printf '1.2\n' > src/main/resources/database/version
    set_version 26.20.0
    printf '# Changelog\n\n## v26.20.0\n\nFirst release.\n\n### Fixes\n\n- **A fix.** It works.\n' > CHANGELOG.md
    git add -A
    git commit -q -m "Start"
    git tag -a v26.20.0 -m v26.20.0
    git push -q origin main v26.20.0
}

# Writes the given version into build.gradle.kts.
set_version() {
    printf 'plugins {}\n\nversion = "%s"\n' "$1" > build.gradle.kts
}

# Commits everything in the working tree.
commit_all() {
    git add -A
    git commit -q -m "$1"
}

# Adds a patch with the given number and content, and points the version file at it.
add_patch() {
    printf '%s\n' "$2" > "src/main/resources/database/postgresql/1/patch_$1.sql"
    printf '1.%s\n' "$1" > src/main/resources/database/version
}

# Runs a command and reports whether it succeeded as expected.
expect_pass() {
    local description="$1" output
    shift
    CASES=$((CASES + 1))
    if output=$("$@" 2>&1); then
        echo "  ok    $description"
    else
        FAILURES=$((FAILURES + 1))
        echo "  FAIL  $description"
        printf '%s\n' "$output" | sed 's/^/        /'
    fi
}

# Runs a command and reports whether it failed with output matching the given pattern.
expect_fail() {
    local description="$1" pattern="$2" output
    shift 2
    CASES=$((CASES + 1))
    if output=$("$@" 2>&1); then
        FAILURES=$((FAILURES + 1))
        echo "  FAIL  $description (succeeded)"
        printf '%s\n' "$output" | sed 's/^/        /'
    elif ! printf '%s' "$output" | grep -qE -- "$pattern"; then
        FAILURES=$((FAILURES + 1))
        echo "  FAIL  $description (no match for: $pattern)"
        printf '%s\n' "$output" | sed 's/^/        /'
    else
        echo "  ok    $description"
    fi
}

# Reports whether two values are equal.
expect_equal() {
    local description="$1" expected="$2" actual="$3"
    CASES=$((CASES + 1))
    if [ "$expected" = "$actual" ]; then
        echo "  ok    $description"
    else
        FAILURES=$((FAILURES + 1))
        echo "  FAIL  $description"
        echo "        expected: $(printf '%q' "$expected")"
        echo "        actual:   $(printf '%q' "$actual")"
    fi
}

# Removes the scratch repositories and reports the result of the file.
finish() {
    local dir
    for dir in "${TEST_SCRATCH_DIRS[@]}"; do rm -rf "$dir"; done
    echo "  $CASES cases, $FAILURES failed"
    [ "$FAILURES" -eq 0 ]
}

TEST_SCRATCH_DIRS=()
