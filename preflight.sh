#!/usr/bin/env sh
set -eu
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
"$JAVA_HOME/bin/java" -version 2>&1 | head -n 1 | grep -Eq 'version "17[."]' || { echo 'JDK 17 required'; exit 1; }
SDK_PATH=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
test -d "$SDK_PATH/platforms" && test -d "$SDK_PATH/build-tools" || { echo 'Set ANDROID_HOME to an Android SDK'; exit 1; }
test -f "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar"
echo RESULT=success
