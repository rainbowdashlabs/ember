#!/usr/bin/env bash
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/helpers.sh"

FETCH="$SCRIPTS/../docker/default-font.sh"
scratch=$(mktemp -d)
TEST_SCRATCH_DIRS+=("$scratch")
cd "$scratch" || exit 1

# Writes an archive laid out like the published one, with stand-in files for the two fonts, the desktop
# and web files beside them and the resource forks a Mac leaves.
python3 - "$scratch/font.zip" <<'EOF'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "w") as archive:
    base = "Berlin Type V1.3/"
    archive.writestr(base + "Office (TTF)/BerlinTypeOffice-Regular.ttf", "regular")
    archive.writestr(base + "Office (TTF)/BerlinTypeOffice-Bold.ttf", "bold")
    archive.writestr(base + "Desktop (OTF)/BerlinType-Regular.otf", "desktop")
    archive.writestr(base + "WEB/BerlinTypeWeb-Regular.woff2", "web")
    archive.writestr(base + "WEB/BerlinTypeWeb-Bold.woff2", "web bold")
    archive.writestr(base + "WEB/BerlinTypeWeb-Regular.woff", "old web")
    archive.writestr("__MACOSX/" + base + "Office (TTF)/._BerlinTypeOffice-Regular.ttf", "fork")
    archive.writestr("__MACOSX/" + base + "WEB/._BerlinTypeWeb-Regular.woff2", "fork")
EOF
python3 - "$scratch/empty.zip" <<'EOF'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "w") as archive:
    archive.writestr("readme.txt", "nothing")
EOF

archive_sha=$(sha256sum font.zip | cut -d' ' -f1)
empty_sha=$(sha256sum empty.zip | cut -d' ' -f1)

# Runs the script against an archive on disk, into the given directory, starting `echo started`.
fetch() {
    local dir="$1" url="$2" sha="$3"
    shift 3
    env DOCUMENTS_DEFAULTFONTDIR="$dir" DEFAULT_FONT_URL="$url" DEFAULT_FONT_SHA256="$sha" "$@" \
        sh "$FETCH" echo started 2>&1
}

output=$(fetch fonts "file://$scratch/font.zip" "$archive_sha")
expect_equal "the archive is fetched and the command started" \
    "default-font: fetched Berlin Type into fonts
started" "$output"
expect_equal "the regular file is taken from the office files" "regular" "$(cat fonts/BerlinTypeOffice-Regular.ttf)"
expect_equal "the bold file too" "bold" "$(cat fonts/BerlinTypeOffice-Bold.ttf)"
expect_equal "the regular web file is taken for the editor" "web" "$(cat fonts/BerlinTypeWeb-Regular.woff2)"
expect_equal "the bold web file too" "web bold" "$(cat fonts/BerlinTypeWeb-Bold.woff2)"
expect_equal "nothing else is extracted" \
    ".archive.sha256 .files.sha256 BerlinTypeOffice-Bold.ttf BerlinTypeOffice-Regular.ttf BerlinTypeWeb-Bold.woff2 BerlinTypeWeb-Regular.woff2" \
    "$(cd fonts && ls -A | LC_ALL=C sort | tr '\n' ' ' | sed 's/ $//')"

output=$(fetch fonts "file://$scratch/missing.zip" "$archive_sha")
expect_equal "a later start finds the files and fetches nothing" "started" "$output"

rm fonts/BerlinTypeWeb-Regular.woff2 fonts/BerlinTypeWeb-Bold.woff2
(cd fonts && sha256sum BerlinTypeOffice-Regular.ttf BerlinTypeOffice-Bold.ttf > .files.sha256)
output=$(fetch fonts "file://$scratch/font.zip" "$archive_sha")
expect_equal "a directory fetched before the web files were taken is fetched once more" \
    "default-font: fetched Berlin Type into fonts
started" "$output"
expect_equal "and then holds the web files" "web" "$(cat fonts/BerlinTypeWeb-Regular.woff2)"

printf 'changed' > fonts/BerlinTypeOffice-Bold.ttf
output=$(fetch fonts "file://$scratch/font.zip" "$archive_sha")
expect_equal "a changed file is fetched again" "bold" "$(cat fonts/BerlinTypeOffice-Bold.ttf)"

output=$(fetch fonts "file://$scratch/font.zip" "$(printf '%s' "$archive_sha" | tr 'a-f' 'A-F')")
expect_equal "the checksum is compared without case" "started" "$output"

output=$(fetch other "file://$scratch/font.zip" "$empty_sha")
expect_equal "an archive with another checksum is refused, and the command still starts" \
    "default-font: file://$scratch/font.zip has the SHA-256 $archive_sha, not $empty_sha, so it is not used and documents print in Liberation Sans
started" "$output"
expect_equal "and nothing is written" "" "$(ls -A other)"

output=$(fetch missing "file://$scratch/missing.zip" "$archive_sha")
expect_equal "a download that fails is named, and the command still starts" \
    "default-font: could not download file://$scratch/missing.zip, documents print in Liberation Sans
started" "$output"

output=$(fetch empty "file://$scratch/empty.zip" "$empty_sha")
expect_equal "an archive without the fonts is named" \
    "default-font: the archive from file://$scratch/empty.zip holds no BerlinTypeOffice-Regular.ttf, documents print in Liberation Sans
started" "$output"
expect_equal "and leaves nothing behind" "" "$(ls -A empty)"

output=$(fetch off "file://$scratch/font.zip" "$archive_sha" DEFAULT_FONT_DOWNLOAD=false)
expect_equal "the download can be switched off" \
    "default-font: the download is switched off
started" "$output"
expect_equal "and then nothing is fetched" "no" "$([ -e off ] && echo yes || echo no)"

finish
