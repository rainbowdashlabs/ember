#!/bin/sh
# Adds the JVM flags the image needs on this machine, then starts whatever command it was given.
#
# On x86-64 the flags switch off the AVX-512 code of the Java runtime and its Base64 intrinsics, which is
# where CVE-2026-114938 sits. They stay until the base image carries a Java runtime with the fix. ARM
# machines get nothing: they do not run the affected code, and the JVM refuses to start with an AVX flag.
#
# The flags go in front of JAVA_OPTS, so a flag an operator sets there still has the last word.
set -u

case "$(uname -m)" in
    aarch64 | arm64 | arm*) ;;
    *) export JAVA_OPTS="-XX:UseAVX=2 -XX:-UseBASE64Intrinsics ${JAVA_OPTS:-}" ;;
esac

[ "$#" -gt 0 ] && exec "$@"
exit 0
