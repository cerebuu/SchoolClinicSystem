#!/bin/sh
set -eu
cd "$(dirname "$0")"
. ./tools/java-env.sh
sh tools/dependencies.sh
mkdir -p out
scms_sources=$(mktemp "${TMPDIR:-/tmp}/scms-sources.XXXXXX")
trap 'rm -f "$scms_sources"' EXIT HUP INT TERM
find src -name '*.java' -print > "$scms_sources"
"$scms_javac" --release 17 -encoding UTF-8 -cp 'lib/*' -d out "@$scms_sources"
mkdir -p out/clinic/app/ui/auth
cp src/clinic/app/ui/auth/uic-logo.png out/clinic/app/ui/auth/
"$scms_java" -cp 'out:lib/*' clinic.app.Main "$@"
