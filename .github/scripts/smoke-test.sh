#!/usr/bin/env bash
# Installs an APK on the running emulator, launches Stash, and fails if it
# crashes or exits. Any crash is reported as a GitHub annotation (visible on
# the run and the PR) with the full stack trace.
#
# Usage: smoke-test.sh <apk> <label>
set -u

apk="$1"
label="$2"
pkg="io.github.foxsoft2005.stash"
wait_seconds=15

report_crash() {
  local title="$1"
  local log
  log=$(adb logcat -b crash -d 2>/dev/null)
  if [ -z "$log" ]; then
    log=$(adb logcat -d -s AndroidRuntime:E 2>/dev/null)
  fi
  echo "$log"
  # Annotation messages need %, CR and LF escaped; keep the useful top part.
  local msg
  msg=$(printf '%s' "$log" | head -80 | sed -e 's/%/%25/g' -e 's/\r//g' | awk '{printf "%s%%0A", $0}')
  echo "::error title=$label: $title::$msg"
  exit 1
}

echo "Installing $apk"
adb uninstall "$pkg" >/dev/null 2>&1 || true
if ! adb install "$apk"; then
  echo "::error title=$label: install failed::adb install $apk failed"
  exit 1
fi

adb logcat -c
adb logcat -b crash -c

echo "Launching $pkg"
adb shell am start -W -n "$pkg/.MainActivity"

sleep "$wait_seconds"

pid=$(adb shell pidof "$pkg" | tr -d '\r')
if [ -n "$(adb logcat -b crash -d 2>/dev/null)" ]; then
  report_crash "crashed on launch"
fi
if [ -z "$pid" ]; then
  report_crash "not running ${wait_seconds}s after launch"
fi

# Second launch (warm start) after the process is killed: catches crashes in
# state restoration, e.g. the saved navigation back stack.
adb shell am start -n "$pkg/.MainActivity" >/dev/null
sleep 2
adb shell input keyevent KEYCODE_HOME
sleep 1
adb shell am kill "$pkg"
sleep 1
adb shell am start -W -n "$pkg/.MainActivity"
sleep 8
if [ -n "$(adb logcat -b crash -d 2>/dev/null)" ] || [ -z "$(adb shell pidof "$pkg" | tr -d '\r')" ]; then
  report_crash "crashed when reopened after process death"
fi

echo "::notice title=$label smoke test::Launched, ran ${wait_seconds}s, survived process death and relaunch."
