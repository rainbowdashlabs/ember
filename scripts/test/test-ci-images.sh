#!/usr/bin/env bash
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/helpers.sh"

IMAGES="$SCRIPTS/ci-images.sh"
scratch=$(mktemp -d)
TEST_SCRATCH_DIRS+=("$scratch")
digest=0960390462a4441dbb63698d7c185b76a41ffcee7b78ff4adf275f3e66f9c475
pinned="atmoz/sftp:latest@sha256:$digest"

# A repository with a compose file naming one image through a variable and a test container class naming
# another, and a docker that answers for the stack's services, holds only the database image and the
# tag a saved pinned image gets, and records what it pulls, tags and saves.
mkdir -p "$scratch/bin" "$scratch/repo/docker" "$scratch/repo/src/test/java/x"
printf 'x-sftp:\n  image: ${EMBER_SFTP_IMAGE:-%s}\n' "$pinned" > "$scratch/repo/docker/compose.dev.yaml"
printf 'class StoreContainers { String s = "rustfs/rustfs:1.0.0"; String t = "19:00"; }\n' \
    > "$scratch/repo/src/test/java/x/StoreContainers.java"
cat > "$scratch/bin/docker" <<FAKE
#!/usr/bin/env bash
log="$scratch/docker.log"
case "\$1 \$2" in
    "compose -f")
        printf '{"services":{"db":{"image":"postgres:18"},"sftp":{"image":"%s"},' "\${EMBER_SFTP_IMAGE:-$pinned}"
        printf '"app":{"image":"ember-app","build":{"context":"."}}}}\n'
        ;;
    "image inspect") [ "\$3" = "postgres:18" ] || [ "\$3" = "ci-images/atmoz-sftp:0960390462a4" ] ;;
    "pull --quiet") echo "pull \$3" >> "\$log" ;;
    tag*) echo "tag \$2 \$3" >> "\$log" ;;
    save*) shift 3; echo "save \$*" >> "\$log" ;;
    *) exit 1 ;;
esac
FAKE
chmod +x "$scratch/bin/docker"
export PATH="$scratch/bin:$PATH" CI_IMAGES_ROOT="$scratch/repo"

expect_equal "the list names the stack's images and the test containers' once, built ones left out" \
    "$pinned
postgres:18
rustfs/rustfs:1.0.0" "$(bash "$IMAGES" list)"

key=$(bash "$IMAGES" key)
expect_equal "the key carries the month" "$(date -u +%Y-%m)" "${key: -7}"
expect_equal "and stays the same for the same list" "$key" "$(bash "$IMAGES" key)"

expect_equal "missing names only the stack's images this machine does not hold" \
    "$pinned" "$(bash "$IMAGES" missing)"

bash "$IMAGES" save "$scratch/images.tar"
expect_equal "saving keeps a pinned image under a tag made from its digest" \
    "pull $pinned
tag $pinned ci-images/atmoz-sftp:0960390462a4
pull postgres:18
pull rustfs/rustfs:1.0.0
save ci-images/atmoz-sftp:0960390462a4 postgres:18 rustfs/rustfs:1.0.0" "$(cat "$scratch/docker.log")"

expect_equal "the variables name the tags that were loaded" \
    "EMBER_SFTP_IMAGE=ci-images/atmoz-sftp:0960390462a4" "$(bash "$IMAGES" env)"

expect_equal "with them set, the stack finds every image" "" "$(EMBER_SFTP_IMAGE=ci-images/atmoz-sftp:0960390462a4 bash "$IMAGES" missing)"

rm -f "$scratch/docker.log"
output=$(bash "$IMAGES" pull-missing 2>&1 >/dev/null; true)
expect_equal "pulling the missing ones names each image it fetches" \
    "ci-images: $pinned is not on this machine, pulling it" "$output"

output=$(bash "$IMAGES" load "$scratch/none.tar" 2>&1)
expect_equal "loading without an archive says so and carries on" \
    "ci-images: no archive at $scratch/none.tar, the images are pulled as they are needed" "$output"

finish
