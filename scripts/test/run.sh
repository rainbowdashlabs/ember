#!/usr/bin/env bash
# Runs every script test, or the ones whose file name contains the given word, and fails when one does.
set -uo pipefail

dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
failed=0

for test in "$dir"/test-*"${1:-}"*.sh; do
    echo "${test##*/}"
    bash "$test" || failed=1
done

exit "$failed"
