#!/usr/bin/env bash
# Fails if a built APK requests a permission Stash must never have. Libraries
# can add permissions to the merged manifest without any change in our code
# (ML Kit adds INTERNET), so this checks the final APKs, not our manifest.
#
# Usage: check-permissions.sh <apk>...
set -u

forbidden=(android.permission.INTERNET)
aapt2=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)/aapt2
status=0

for apk in "$@"; do
  perms=$("$aapt2" dump permissions "$apk" | grep -oE "name='[^']+'" | sed "s/name='//; s/'//" | grep -v '^io\.github\.foxsoft2005\.stash$' | sort -u)
  echo "$apk:"; echo "$perms" | sed 's/^/  /'
  for p in "${forbidden[@]}"; do
    if echo "$perms" | grep -qx "$p"; then
      echo "::error title=Forbidden permission in $(basename "$apk")::$p is in the built APK. A library added it to the merged manifest; strip it with tools:node=\"remove\" in AndroidManifest.xml."
      status=1
    fi
  done
  echo "::notice title=Permissions in $(basename "$apk")::$(echo "$perms" | sed 's/android.permission.//' | paste -sd, - | sed 's/,/, /g')"
done
exit $status
