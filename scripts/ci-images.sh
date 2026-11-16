#!/usr/bin/env bash
# The container images a CI run starts, kept in one archive so that a run pulls each of them once at
# most, and a run that finds the archive cached pulls none.
#
# Every end-to-end group and every backend test suite runs on a runner of its own, and each of them
# used to pull the same images again: the database, the object storage, SFTP and SMB. Ten jobs a run,
# several runs a day, ran into the pull limit of Docker Hub first and then into the data limit of the
# AWS copy that replaced it.
#
#   list            the images the end-to-end stack starts and the backend tests use, one per line
#   key             the cache key: a hash of that list and the month, so a tag that moves upstream is
#                   picked up within a month without the list changing
#   missing         the images of the list this machine does not hold yet
#   pull-missing    pulls those, trying a few times each, and nothing else
#   save <file>     pulls every image, trying a few times, and writes them all into one archive
#   load <file>     loads that archive, and says so and carries on when there is none
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

list() {
    docker compose -f "$root/docker/compose.dev.yaml" --profile e2e config --format json \
        | jq -r '.services[] | select(.build == null) | .image' \
        | sort -u
}

key() {
    printf 'ci-images-%s-%s\n' "$(list | sha256sum | cut -c1-16)" "$(date -u +%Y-%m)"
}

missing() {
    local image
    while read -r image; do
        docker image inspect "$image" >/dev/null 2>&1 || printf '%s\n' "$image"
    done < <(list)
}

pull() {
    local image="$1" attempt delay=5
    for attempt in 1 2 3 4; do
        docker pull --quiet "$image" >/dev/null && return 0
        [ "$attempt" -lt 4 ] || break
        echo "ci-images: $image did not come, trying again in ${delay}s ($attempt/4)" >&2
        sleep "$delay"
        delay=$((delay * 2))
    done
    echo "ci-images: could not pull $image" >&2
    return 1
}

pull_missing() {
    local image failed=0
    while read -r image; do
        pull "$image" || failed=1
    done < <(missing)
    return "$failed"
}

save() {
    local file="$1" image images=()
    while read -r image; do
        pull "$image"
        images+=("$image")
    done < <(list)
    docker save -o "$file" "${images[@]}"
}

load() {
    local file="$1"
    if [ -f "$file" ]; then
        docker load -q -i "$file"
    else
        echo "ci-images: no archive at $file, the images are pulled as they are needed" >&2
    fi
}

command="${1:-}"
case "$command" in
    list | key | missing) "$command" ;;
    pull-missing) pull_missing ;;
    save | load)
        [ $# -ge 2 ] || { echo "ci-images $command needs the archive's path" >&2; exit 2; }
        "$command" "$2"
        ;;
    *)
        echo "usage: ci-images list | key | missing | pull-missing | save <file> | load <file>" >&2
        exit 2
        ;;
esac
