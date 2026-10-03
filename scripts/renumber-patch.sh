#!/usr/bin/env bash
# Renames a migration this branch has not released yet, in the working tree of the current directory.
#
# Usage: renumber-patch.sh [--dry-run] <from> <to>
#
# Moves patch_<from>.sql to patch_<to>.sql, points the version file at <to> when it named <from>, and
# rewrites every test and source file that names patch_<from>.sql. Refuses a patch that main carries as
# it is here, or that existed before this branch left main: a patch on main is frozen.
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/shared/repository.sh"

dry_run=0
if [ "${1:-}" = "--dry-run" ]; then
    dry_run=1
    shift
fi

fail() {
    echo "renumber-patch: $*" >&2
    exit 1
}

[ $# -eq 2 ] || fail "usage: renumber-patch.sh [--dry-run] <from> <to>"
from="$1"
to="$2"
[[ "$from" =~ ^[0-9]+$ && "$to" =~ ^[0-9]+$ ]] || fail "patch numbers are whole numbers, not '$from' and '$to'."
[ "$from" != "$to" ] || fail "patch_$from already has that number."

source_path=$(patch_path "$from")
target_path=$(patch_path "$to")
[ -f "$source_path" ] || fail "there is no $source_path."
[ ! -e "$target_path" ] || fail "$target_path exists already."

ensure_main_ref || fail "cannot read $MAIN_REF; fetch main first."
if [ "$(git rev-parse -q --verify "$MAIN_REF:$source_path" || true)" = "$(git hash-object "$source_path")" ]; then
    fail "patch_$from is on main as it is here, and a patch on main never changes."
fi
fork_point=$(git merge-base "$MAIN_REF" HEAD)
if git cat-file -e "$fork_point:$source_path" 2> /dev/null; then
    fail "patch_$from was on main before this branch left it, and a patch on main never changes."
fi

references=$(grep -rlF "patch_$from.sql" src 2> /dev/null | grep -v "^src/main/resources/database/" || true)
version_names_it=0
[ "$(cat "$PATCH_VERSION_FILE")" = "1.$from" ] && version_names_it=1

if [ "$dry_run" -eq 1 ]; then
    echo "Would move $source_path to $target_path."
    [ "$version_names_it" -eq 1 ] && echo "Would set $PATCH_VERSION_FILE to 1.$to."
    for file in $references; do echo "Would rename patch_$from.sql to patch_$to.sql in $file."; done
    exit 0
fi

mv "$source_path" "$target_path"
echo "Moved $source_path to $target_path."
if [ "$version_names_it" -eq 1 ]; then
    printf '1.%s\n' "$to" > "$PATCH_VERSION_FILE"
    echo "Set $PATCH_VERSION_FILE to 1.$to."
fi
for file in $references; do
    sed -i "s/patch_$from\.sql/patch_$to.sql/g" "$file"
    echo "Renamed patch_$from.sql to patch_$to.sql in $file."
done
