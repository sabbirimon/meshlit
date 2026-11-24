#!/bin/sh
set -eu
# Local developer launcher for the optional VS Code workspace handoff.
repo=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ "$#" -ne 2 ] || [ "$1" != "--workspace" ] || [ ! -d "$2" ]; then
  echo 'Usage: launch-studio-dev.sh --workspace /absolute/project' >&2
  exit 2
fi
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21); export JAVA_HOME
fi
if [ -z "${ANDROID_HOME:-}" ] && [ -d "$HOME/Library/Android/sdk" ]; then
  ANDROID_HOME="$HOME/Library/Android/sdk"; export ANDROID_HOME
fi
cd "$repo"
if [ "$(uname -s)" = Darwin ]; then
  exec python3 "$repo/scripts/launch-studio-macos.py" --workspace "$2"
fi
exec ./gradlew :desktopApp:run "-Pmeshlit.workspace=$2" --no-configuration-cache --no-daemon --no-parallel --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process
