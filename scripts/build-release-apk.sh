#!/bin/sh
# Builds the signed release APK for the elder's phone.
#   scripts/build-release-apk.sh https://your-server.example
# The server address must be https: release builds do not allow plain http.
# Output: dist-apk/medicine-reminder-<version>.apk (git-ignored). Install it on the phone directly.
set -eu
API_URL="${1:-}"
case "$API_URL" in
  https://*) ;;
  *) echo "Usage: $0 https://your-server.example   (must be https)"; exit 1 ;;
esac
ROOT=$(cd "$(dirname "$0")/.." && pwd)
PROPS="${MR_KEYSTORE_PROPERTIES:-$HOME/.medicine-reminder/keystore.properties}"
if [ ! -f "$PROPS" ]; then
  echo "No release key found. Run scripts/create-release-key.sh first."
  exit 1
fi
cd "$ROOT/app"
VITE_API_URL="$API_URL" npm run build
npx cap sync android
cd android
./gradlew --no-daemon -q assembleRelease
VERSION=$(grep -o 'versionName "[^"]*"' app/build.gradle | cut -d'"' -f2)
mkdir -p "$ROOT/dist-apk"
OUT="$ROOT/dist-apk/medicine-reminder-$VERSION.apk"
cp app/build/outputs/apk/release/app-release.apk "$OUT"
echo "Built $OUT"
