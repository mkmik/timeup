#!/usr/bin/env bash
# Creates a release keystore for personal builds and prints the GitHub secrets to set.
# Usage: scripts/make-keystore.sh [path/to/release.jks]
set -euo pipefail
out="${1:-release.jks}"
alias="timeup"
if [ -e "$out" ]; then echo "$out already exists; refusing to overwrite" >&2; exit 1; fi
read -r -s -p "Keystore/key password: " pw; echo
keytool -genkeypair -v -keystore "$out" -alias "$alias" -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass "$pw" -keypass "$pw" -dname "CN=TimeUp personal build"
echo
echo "Set these repository secrets (Settings → Secrets and variables → Actions):"
echo "  TIMEUP_KEYSTORE_BASE64   = $(base64 -w0 "$out" 2>/dev/null || base64 "$out" | tr -d '\n')"
echo "  TIMEUP_KEYSTORE_PASSWORD = (the password you typed)"
echo "  TIMEUP_KEY_ALIAS         = $alias"
echo "  TIMEUP_KEY_PASSWORD      = (the password you typed)"
echo
echo "Keep $out safe and out of git (it is ignored). Every CI build signed with it can update the previous one in place."
