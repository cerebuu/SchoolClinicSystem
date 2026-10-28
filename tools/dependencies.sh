#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p lib
scms_checksum() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d ' ' -f 1
    else shasum -a 256 "$1" | cut -d ' ' -f 1
    fi
}
while read -r scms_name scms_path scms_expected; do
    scms_target="lib/$scms_name"
    if [ ! -f "$scms_target" ]; then
        scms_download=$(mktemp "lib/$scms_name.download.XXXXXX")
        trap 'rm -f "$scms_download"' EXIT HUP INT TERM
        curl --fail --location --silent --show-error "https://repo.maven.apache.org/maven2/$scms_path/$scms_name" -o "$scms_download"
        [ "$(scms_checksum "$scms_download")" = "$scms_expected" ] || {
            echo "$scms_name download checksum mismatch." >&2; exit 1;
        }
        mv "$scms_download" "$scms_target"
        trap - EXIT HUP INT TERM
    fi
    [ "$(scms_checksum "$scms_target")" = "$scms_expected" ] || {
        echo "$scms_name checksum mismatch. Restore the pinned dependency before launching." >&2; exit 1;
    }
done <<'ARTIFACTS'
h2-2.5.252.jar com/h2database/h2/2.5.252 90b11dc413da82070ef15fc943282c1408e6ffd34fa68998335fea8603b5ff20
angus-mail-2.0.3.jar org/eclipse/angus/angus-mail/2.0.3 6b5b6fc2ba633c53733d829c56cae51d343fb1eadee3e17b8eea01a49edc72d3
angus-core-2.0.3.jar org/eclipse/angus/angus-core/2.0.3 83eee7977cc1bed492861d5eba6d0f27a0c777f5a36a0a07937be266dab510d5
angus-activation-2.0.2.jar org/eclipse/angus/angus-activation/2.0.2 6dd3bcffc22bce83b07376a0e2e094e4964a3195d4118fb43e380ef35436cc1e
jakarta.mail-api-2.1.3.jar jakarta/mail/jakarta.mail-api/2.1.3 8051b58d75f982f9a5b963b3765426e824b2a64865ef0af17205e455b98db05c
jakarta.activation-api-2.1.3.jar jakarta/activation/jakarta.activation-api/2.1.3 01b176d718a169263e78290691fc479977186bcc6b333487325084d6586f4627
ARTIFACTS
