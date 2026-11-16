#!/usr/bin/env bash
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/helpers.sh"

IMAGES="$SCRIPTS/ci-images.sh"
scratch=$(mktemp -d)
TEST_SCRATCH_DIRS+=("$scratch")
mkdir -p "$scratch/bin"

# A docker that answers the compose configuration with two pulled services and a built one, and holds
# only the database image.
cat > "$scratch/bin/docker" <<'FAKE'
#!/usr/bin/env bash
case "$1 $2" in
    "compose -f")
        printf '{"services":{"db":{"image":"postgres:18"},"store":{"image":"rustfs:1"},'
        printf '"app":{"image":"ember-app","build":{"context":"."}},"db2":{"image":"postgres:18"}}}\n'
        ;;
    "image inspect") [ "$3" = "postgres:18" ] ;;
    "pull --quiet") exit 0 ;;
    *) exit 1 ;;
esac
FAKE
chmod +x "$scratch/bin/docker"
export PATH="$scratch/bin:$PATH"

expect_equal "the list names every pulled image once and leaves built ones out" \
    "postgres:18
rustfs:1" "$(bash "$IMAGES" list)"

key=$(bash "$IMAGES" key)
expect_equal "the key carries the month" "$(date -u +%Y-%m)" "${key: -7}"
expect_equal "and stays the same for the same list" "$key" "$(bash "$IMAGES" key)"

expect_equal "missing names only the images this machine does not hold" "rustfs:1" "$(bash "$IMAGES" missing)"

output=$(bash "$IMAGES" pull-missing 2>&1 >/dev/null; true)
expect_equal "pulling the missing ones names each image it fetches" \
    "ci-images: rustfs:1 is not on this machine, pulling it" "$(printf '%s\n' "$output" | head -1)"

output=$(bash "$IMAGES" load "$scratch/none.tar" 2>&1)
expect_equal "loading without an archive says so and carries on" \
    "ci-images: no archive at $scratch/none.tar, the images are pulled as they are needed" "$output"

finish
