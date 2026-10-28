#!/bin/sh
# Sourced by the POSIX launchers so compiler and runtime use the same JDK.
if [ -n "${JAVA_HOME:-}" ]; then
    scms_java_home=$JAVA_HOME
    scms_configured_version=$("$scms_java_home/bin/java" -version 2>&1 || true)
    scms_configured_major=$(printf '%s\n' "$scms_configured_version" | sed -n '1s/.*version "\([0-9]*\).*/\1/p')
    if [ "$(uname -s)" = Darwin ] && { [ ! -x "$scms_java_home/bin/javac" ] || [ "${scms_configured_major:-0}" -lt 17 ]; }; then
        scms_java_home=$(/usr/libexec/java_home -v 17 2>/dev/null) || {
            echo 'Install JDK 17 or newer and set JAVA_HOME to its directory.' >&2
            exit 1
        }
        echo 'Using installed JDK 17 because JAVA_HOME does not contain a supported JDK.' >&2
    fi
elif [ "$(uname -s)" = Darwin ]; then
    scms_java_home=$(/usr/libexec/java_home -v 17 2>/dev/null) || {
        echo 'Install JDK 17 or newer and set JAVA_HOME to its directory.' >&2
        exit 1
    }
else
    scms_java_home=
fi
if [ -n "$scms_java_home" ]; then
    scms_java="$scms_java_home/bin/java"
    scms_javac="$scms_java_home/bin/javac"
else
    scms_java=java
    scms_javac=javac
fi
scms_java_version=$("$scms_java" -version 2>&1) || { echo "$scms_java_version" >&2; exit 1; }
scms_java_major=$(printf '%s\n' "$scms_java_version" | sed -n '1s/.*version "\([0-9]*\).*/\1/p')
case "$scms_java_major" in
    ''|*[!0-9]*) echo 'Unable to determine Java version. Set JAVA_HOME to JDK 17 or newer.' >&2; exit 1 ;;
esac
if [ "$scms_java_major" -lt 17 ]; then
    echo 'This application requires JDK 17 or newer. Set JAVA_HOME before launching.' >&2
    exit 1
fi
scms_javac_version=$("$scms_javac" -version 2>&1) || { echo "$scms_javac_version" >&2; exit 1; }
scms_javac_major=$(printf '%s\n' "$scms_javac_version" | sed -n 's/^javac \([0-9]*\).*/\1/p')
case "$scms_java_major:$scms_javac_major" in
    *[!0-9:]*|:*|*:) echo 'Unable to determine Java versions. Set JAVA_HOME to JDK 17 or newer.' >&2; exit 1 ;;
esac
if [ "$scms_java_major" -lt 17 ] || [ "$scms_javac_major" -lt 17 ]; then
    echo 'This application requires JDK 17 or newer. Set JAVA_HOME before launching.' >&2
    exit 1
fi
