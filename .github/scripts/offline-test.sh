#!/usr/bin/env bash
# On the emulator: runs the on-device tests (ML Kit image labeling and the
# Gemini Nano status check, with no INTERNET permission), then force-runs the
# upload jobs ML Kit's usage-statistics library scheduled (the code path that
# would talk to the network) and fails if Stash crashed at any point.
set -u

pkg="io.github.foxsoft2005.stash"
app_apk="app/build/outputs/apk/debug/app-debug.apk"
test_apk="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

fail() {
  local title="$1" detail="$2"
  local msg
  msg=$(printf '%s' "$detail" | head -80 | sed -e 's/%/%25/g' -e 's/\r//g' | awk '{printf "%s%%0A", $0}')
  echo "$detail"
  echo "::error title=Offline test: $title::$msg"
  exit 1
}

adb uninstall "$pkg.test" >/dev/null 2>&1 || true
adb uninstall "$pkg" >/dev/null 2>&1 || true
adb install "$app_apk" || fail "install failed" "adb install $app_apk"
adb install "$test_apk" || fail "install failed" "adb install $test_apk"
adb logcat -b crash -c

out=$(adb shell am instrument -w -r "$pkg.test/androidx.test.runner.AndroidJUnitRunner" 2>&1 | tr -d '\r')
echo "$out" | grep -E "INSTRUMENTATION_STATUS: (class|test)=|INSTRUMENTATION_STATUS_CODE|INSTRUMENTATION_CODE|OK \(|FAILURES|Tests run" || true
if ! echo "$out" | grep -qE '^OK \([0-9]+ tests?\)'; then
  fail "on-device tests failed" "$(echo "$out" | grep -A30 -E 'INSTRUMENTATION_STATUS: stack=|FAILURES|Process crashed' | head -60)"
fi
passed=$(echo "$out" | grep -oE '^OK \([0-9]+ tests?\)')

# ML Kit's statistics library (datatransport) schedules its uploads with
# JobScheduler. Run them now instead of whenever Android would.
jobs=$(adb shell dumpsys jobscheduler | tr -d '\r' | grep -E "JOB #u[0-9]+a[0-9]+/-?[0-9]+: [0-9a-f]+ $pkg/" | sed -E 's/.*JOB #u[0-9]+a[0-9]+\/(-?[0-9]+):.*/\1/' | sort -u)
count=0
for id in $jobs; do
  echo "Force-running job $id"
  adb shell cmd jobscheduler run -f "$pkg" "$id" || true
  count=$((count + 1))
done
sleep 10

crash=$(adb logcat -b crash -d | tr -d '\r')
if [ -n "$crash" ]; then
  fail "Stash crashed (with no INTERNET permission)" "$crash"
fi
echo "::notice title=Offline test::$passed on-device; $count scheduled ML Kit upload job(s) force-run without INTERNET; no crash."
