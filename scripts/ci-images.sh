#!/usr/bin/env bash
# The container images a CI run starts, kept in one archive so that a run pulls each of them once at
# most, and a run that finds the archive cached pulls none.
#
# Every end-to-end group and every backend test suite runs on a runner of its own, and each of them
# used to pull the same images again: the database, the object storage, SFTP and SMB. Ten jobs a run,
# several runs a day, ran into the pull limit of Docker Hub first and then into the data limit of the
# AWS copy that replaced it.
#
# An image pinned by its digest comes out of `docker load` without that digest, so nothing asking for
# it by digest finds it. Such images are saved under a tag made from their digest instead, and `env`
# names those tags in the variables the compose file and the test containers read their image from.
#
#   list            every image of the archive: what the end-to-end stack starts and what the backend
#                   test containers name, one per line
#   key             the cache key: a hash of that list, the archive's layout and the month, so a tag
#                   that moves upstream is picked up within a month without the list changing
#   missing         the images of the end-to-end stack this machine does not hold yet
#   pull-missing    pulls those, trying a few times each, and nothing else
#   save <file>     pulls every image of the list and writes them all into one archive
#   load <file>     loads that archive, and says so and carries on when there is none
#   env             VARIABLE=image for each digest-pinned image whose tag was loaded, for GITHUB_ENV
set -euo pipefail

root="${CI_IMAGES_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
compose_file="$root/docker/compose.dev.yaml"
layout=2

stack_images() {
    docker compose -f "$compose_file" --profile e2e config --format json \
        | jq -r '.services[] | select(.build == null) | .image' \
        | sort -u
}

test_images() {
    find "$root/src/test/java" -name '*Containers.java' -print0 \
        | xargs -0 -r grep -ohE '"[a-z0-9.-]+(/[a-z0-9._-]+)+:[a-z0-9._-]+(@sha256:[0-9a-f]{64})?"' \
        | tr -d '"' \
        | sort -u
}

pinned_default() {
    grep -oE '\$\{[A-Z_]+_IMAGE:-[^}]+\}' "$compose_file" | sed -E 's/^\$\{([A-Z_]+):-(.*)\}$/\1 \2/' | sort -u
}

list() {
    {
        stack_images
        test_images
        pinned_default | cut -d' ' -f2
    } | grep -v '^ci-images/' | sort -u
}

local_tag() {
    local image="$1" name digest
    name="${image%%@*}"
    name="${name%:*}"
    digest="${image##*@sha256:}"
    printf 'ci-images/%s:%s\n' "${name//\//-}" "${digest:0:12}"
}

key() {
    printf 'ci-images-v%s-%s-%s\n' "$layout" "$(list | sha256sum | cut -c1-16)" "$(date -u +%Y-%m)"
}

missing() {
    local image
    while read -r image; do
        docker image inspect "$image" >/dev/null 2>&1 || printf '%s\n' "$image"
    done < <(stack_images)
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
    local image failed=0 pulled=0
    while read -r image; do
        echo "ci-images: $image is not on this machine, pulling it" >&2
        pull "$image" || failed=1
        pulled=$((pulled + 1))
    done < <(missing)
    [ "$pulled" -gt 0 ] || echo "ci-images: every image is on this machine already, nothing pulled" >&2
    return "$failed"
}

save() {
    local file="$1" image saved=()
    while read -r image; do
        pull "$image"
        if [[ "$image" == *@sha256:* ]]; then
            docker tag "$image" "$(local_tag "$image")"
            saved+=("$(local_tag "$image")")
        else
            saved+=("$image")
        fi
    done < <(list)
    docker save -o "$file" "${saved[@]}"
}

load() {
    local file="$1"
    if [ -f "$file" ]; then
        docker load -q -i "$file"
    else
        echo "ci-images: no archive at $file, the images are pulled as they are needed" >&2
    fi
}

env_lines() {
    local variable image tag
    while read -r variable image; do
        tag="$(local_tag "$image")"
        if docker image inspect "$tag" >/dev/null 2>&1; then
            printf '%s=%s\n' "$variable" "$tag"
        fi
    done < <(pinned_default)
}

command="${1:-}"
case "$command" in
    list | key | missing) "$command" ;;
    pull-missing) pull_missing ;;
    env) env_lines ;;
    save | load)
        [ $# -ge 2 ] || { echo "ci-images $command needs the archive's path" >&2; exit 2; }
        "$command" "$2"
        ;;
    *)
        echo "usage: ci-images list | key | missing | pull-missing | save <file> | load <file> | env" >&2
        exit 2
        ;;
esac
