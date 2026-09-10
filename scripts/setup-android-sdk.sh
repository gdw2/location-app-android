#!/usr/bin/env bash
set -euo pipefail

# Installs the Android command-line SDK into $ANDROID_HOME (default: ./.android-sdk).
# Run inside the devbox shell: `devbox run setup-android`.

SDK_ROOT="${ANDROID_HOME:-$PWD/.android-sdk}"
CMDLINE_TOOLS_VERSION="${CMDLINE_TOOLS_VERSION:-11076708}"
API_LEVEL="${API_LEVEL:-35}"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-35.0.0}"

mkdir -p "$SDK_ROOT"

if [ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Downloading Android command-line tools ($CMDLINE_TOOLS_VERSION)…"
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/cmdline-tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
  unzip -q "$tmp/cmdline-tools.zip" -d "$tmp"
  mkdir -p "$SDK_ROOT/cmdline-tools"
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  rm -rf "$tmp"
fi

export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"

echo "Accepting licenses…"
yes | sdkmanager --licenses >/dev/null 2>&1 || true

echo "Installing platform-tools, platforms;android-${API_LEVEL}, build-tools;${BUILD_TOOLS_VERSION}…"
sdkmanager --install \
  "platform-tools" \
  "platforms;android-${API_LEVEL}" \
  "build-tools;${BUILD_TOOLS_VERSION}"

echo "Android SDK ready at $SDK_ROOT"
