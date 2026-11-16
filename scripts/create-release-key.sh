#!/bin/sh
# Creates the signing key for the release APK, once. It is kept OUTSIDE the project:
#   ~/.medicine-reminder/release.jks and ~/.medicine-reminder/keystore.properties
# BACK BOTH UP (e.g. a USB stick or a password manager). Without them you cannot install an update over the
# existing app; you would have to uninstall it (losing the phone's saved schedule) and install again.
set -eu
DIR="$HOME/.medicine-reminder"
if [ -f "$DIR/release.jks" ]; then
  echo "A release key already exists in $DIR (not replacing it)."
  exit 0
fi
mkdir -p "$DIR"
chmod 700 "$DIR"
PASS=$(openssl rand -base64 24 | tr -d '/+=' | cut -c1-24)
keytool -genkeypair -keystore "$DIR/release.jks" -alias medicine-reminder -keyalg RSA -keysize 3072 \
  -validity 10000 -storepass "$PASS" -keypass "$PASS" -dname "CN=Medicine Reminder" >/dev/null 2>&1
cat > "$DIR/keystore.properties" <<PROPS
storeFile=$DIR/release.jks
storePassword=$PASS
keyAlias=medicine-reminder
keyPassword=$PASS
PROPS
chmod 600 "$DIR/release.jks" "$DIR/keystore.properties"
echo "Release key created in $DIR. Back up release.jks and keystore.properties."
