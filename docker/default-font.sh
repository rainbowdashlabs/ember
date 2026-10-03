#!/bin/sh
# Fetches the default font of generated documents, then starts whatever command it was given.
#
# The default font is Berlin Type, published by the State of Berlin for everybody to use but not ours to
# hand out. It is therefore never committed and never baked into an image: every backend container fetches
# it on its first start and keeps it in the directory the application reads the default font from, which
# lies in the data directory, so a restart finds it there and fetches nothing. Only the two Office files,
# regular and bold, are taken from the archive; the web files may only be used on a web page.
#
# Nothing here stops the application. A download that fails, an archive whose checksum does not match or a
# directory that cannot be written says so in one line, and documents print in Liberation Sans until a
# later start succeeds.
#
#   DEFAULT_FONT_DOWNLOAD     false switches the download off, for an instance without internet access
#   DEFAULT_FONT_URL          the archive to fetch
#   DEFAULT_FONT_SHA256       the SHA-256 the archive must have
#   DOCUMENTS_DEFAULTFONTDIR  where the files go, the same directory the application reads
set -u

url="${DEFAULT_FONT_URL:-https://wir.berlin/fileadmin/downloads/Wir.Berlin_Schrift.zip}"
expected="$(printf '%s' "${DEFAULT_FONT_SHA256:-9a61571989f344ec0e255eda8e758df3be20c1dd13e1b7ec410ed2f5aa829dff}" | tr 'A-F' 'a-f')"
dir="${DOCUMENTS_DEFAULTFONTDIR:-data/default-font}"
files="BerlinTypeOffice-Regular.ttf BerlinTypeOffice-Bold.ttf"
archive_sum="$dir/.archive.sha256"
file_sums="$dir/.files.sha256"

say() {
    echo "default-font: $*" >&2
}

in_place() {
    [ -f "$archive_sum" ] && [ -f "$file_sums" ] || return 1
    [ "$(cat "$archive_sum")" = "$expected" ] || return 1
    (cd "$dir" && sha256sum -c "${file_sums##*/}" >/dev/null 2>&1)
}

download() {
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL --retry 2 --max-time 120 -o "$2" "$1"
    else
        wget -q -T 120 -O "$2" "$1"
    fi
}

fetch() {
    if ! mkdir -p "$dir" 2>/dev/null || [ ! -w "$dir" ]; then
        say "cannot write $dir, documents print in Liberation Sans"
        return 1
    fi
    work="$(mktemp -d "$dir/.fetch.XXXXXX")" || return 1
    if ! download "$url" "$work/archive.zip" 2>/dev/null; then
        say "could not download $url, documents print in Liberation Sans"
        rm -rf "$work"
        return 1
    fi
    actual="$(sha256sum "$work/archive.zip" | cut -d' ' -f1)"
    if [ "$actual" != "$expected" ]; then
        say "$url has the SHA-256 $actual, not $expected, so it is not used and documents print in Liberation Sans"
        rm -rf "$work"
        return 1
    fi
    for name in $files; do
        if ! unzip -p "$work/archive.zip" "*/$name" > "$work/$name" 2>/dev/null || [ ! -s "$work/$name" ]; then
            say "the archive from $url holds no $name, documents print in Liberation Sans"
            rm -rf "$work"
            return 1
        fi
    done
    for name in $files; do
        mv -f "$work/$name" "$dir/$name"
    done
    (cd "$dir" && sha256sum $files > "${file_sums##*/}")
    printf '%s\n' "$expected" > "$archive_sum"
    rm -rf "$work"
    say "fetched Berlin Type into $dir"
}

case "$(printf '%s' "${DEFAULT_FONT_DOWNLOAD:-true}" | tr 'A-Z' 'a-z')" in
    false | no | off | 0) say "the download is switched off" ;;
    *) in_place || fetch ;;
esac

[ "$#" -gt 0 ] && exec "$@"
exit 0
