#!/usr/bin/env bash
# Checks that a release branch builds its migrations on top of main's.
#
# Applies to a push to release/vX.Y.Z, a pull request into it, and the pull request that releases it
# into main; everything else passes untouched. Reads EVENT_NAME, REF_NAME, BASE_REF and HEAD_REF like
# check-version.sh. The branch must carry every patch of main unchanged, and at most one patch of its
# own, numbered above main's newest.
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/shared/repository.sh"

case "${EVENT_NAME:-push}" in
    pull_request | pull_request_target | merge_group)
        target="${BASE_REF#refs/heads/}"
        [ "$target" = "main" ] && [[ "${HEAD_REF:-}" == release/v* ]] && target="$HEAD_REF"
        ;;
    *) target="$REF_NAME" ;;
esac

if [[ "$target" != release/v* ]]; then
    echo "Not a release branch, so there is nothing to compare with main."
    exit 0
fi

ensure_main_ref
main_newest=$(newest_patch "$MAIN_REF")
problems=0

for number in $(patch_numbers "$MAIN_REF"); do
    path=$(patch_path "$number")
    if [ "$(git rev-parse "$MAIN_REF:$path")" != "$(git rev-parse -q --verify "HEAD:$path" || true)" ]; then
        echo "::error::main has $path, and this branch carries it differently or not at all."
        problems=1
    fi
done

own=()
for number in $(patch_numbers HEAD); do
    git cat-file -e "$MAIN_REF:$(patch_path "$number")" 2> /dev/null || own+=("$number")
done

if [ "${#own[@]}" -gt 1 ]; then
    echo "::error::This release branch carries ${#own[@]} patches of its own (${own[*]}). It is released as one, so they can be one file: merge them into the lowest-numbered patch, delete the rest, and set $PATCH_VERSION_FILE to that number."
    problems=1
fi

for number in "${own[@]}"; do
    if [ "$number" -le "$main_newest" ]; then
        echo "::error::This branch's own patch_$number is not above main's newest, patch_$main_newest. Rename it with ./toolchain.sh db-renumber-patch $number $((main_newest + 1))."
        problems=1
    fi
done

if [ "$problems" -ne 0 ]; then
    echo
    echo "main gained a migration this release branch lacks. After a fix release the branch is rebased onto"
    echo "main and its own patch renumbered by the Release Sync workflow; when that could not happen, rebase"
    echo "it by hand."
    exit 1
fi

echo "This release branch carries main's patches up to patch_$main_newest${own[*]:+ and its own patch_${own[*]}}."
