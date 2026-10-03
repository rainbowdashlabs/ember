# shellcheck shell=bash disable=SC2034
# Reads what the release rules are judged by: the project version, the migrations, the released tags
# on origin and the changelog. Sourced by the scripts next to it; every function reads the commit it
# is given, so nothing depends on what is checked out.

PATCH_DIR="src/main/resources/database/postgresql/1"
PATCH_VERSION_FILE="src/main/resources/database/version"
MAIN_REF="${EMBER_MAIN_REF:-origin/main}"

# The version build.gradle.kts carries at the given commit.
project_version() {
    git show "${1:-HEAD}:build.gradle.kts" | sed -n 's/^version = "\(.*\)"$/\1/p'
}

# Whether the argument is a version of the form X.Y.Z.
is_version() {
    [[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
}

# Every release tag on origin with the commit it points at, one "vX.Y.Z <sha>" per line.
released_tags() {
    git ls-remote --tags origin 'refs/tags/v*' |
        awk '{ name = $2; sub("^refs/tags/", "", name); peeled = sub("\\^\\{\\}$", "", name)
               if (peeled || !(name in commit)) commit[name] = $1 }
             END { for (name in commit) print name, commit[name] }' |
        grep -E '^v[0-9]+\.[0-9]+\.[0-9]+ ' || true
}

# The commit the release tag of the given version points at, empty when it is not released.
released_commit() {
    released_tags | awk -v tag="v$1" '$1 == tag { print $2 }'
}

# The newest release whose tag is reachable from the given commit, as X.Y.Z, empty when there is none.
newest_release_reachable_from() {
    local commit="$1" name sha
    while read -r name sha; do
        if git merge-base --is-ancestor "$sha" "$commit" 2>/dev/null; then
            printf '%s\n' "${name#v}"
            return
        fi
    done < <(released_tags | sort -t' ' -k1,1Vr)
}

# The version one patch above the given one.
next_patch() {
    local major minor patch
    IFS=. read -r major minor patch <<< "$1"
    printf '%s.%s.%s\n' "$major" "$minor" "$((patch + 1))"
}

# The patch numbers at the given commit, ascending.
patch_numbers() {
    git ls-tree --name-only "$1" "$PATCH_DIR/" 2>/dev/null |
        sed -n 's|.*/patch_\([0-9][0-9]*\)\.sql$|\1|p' | sort -n
}

# The highest patch number at the given commit, 0 when there is none.
newest_patch() {
    patch_numbers "$1" | tail -n 1 | grep . || echo 0
}

# The path of a patch by its number.
patch_path() {
    printf '%s/patch_%s.sql\n' "$PATCH_DIR" "$1"
}

# Makes sure the main branch of origin is known locally, fetching it once when it is not.
ensure_main_ref() {
    git rev-parse --verify -q "$MAIN_REF^{commit}" > /dev/null && return
    git fetch -q origin "+refs/heads/main:refs/remotes/origin/main"
}

# The changelog block of a version as release notes: the block without its heading, with `###`
# headings raised to `#` and the blank lines around it trimmed. Empty when the version has no block.
release_notes() {
    local version="$1" commit="${2:-HEAD}"
    git show "$commit:CHANGELOG.md" |
        awk -v heading="## v$version" '
            $0 == heading { inside = 1; next }
            inside && /^## / { exit }
            inside { sub(/^### /, "# "); print }' |
        sed -e '/./,$!d' | sed -e ':a' -e '/^\n*$/{$d;N;ba' -e '}'
}
