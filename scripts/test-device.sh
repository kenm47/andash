#!/usr/bin/env bash
# Runs the on-device tests on the attached phone.
#   scripts/test-device.sh                 # every instrumented test
#   scripts/test-device.sh DashboardUiTest # one class
# Installs with -g (see build.gradle.kts) so READ/WRITE_CALENDAR are granted, then runs
# `am instrument`. The tests create throwaway LOCAL calendars named andash-test-* and remove them.
set -euo pipefail
cd "$(dirname "$0")/.."
source ./env.sh

./gradlew installDebug installDebugAndroidTest --console=plain -q
args=()
if [[ $# -gt 0 ]]; then args=(-e class "co.lunt.andash.$1"); fi
out=$(mktemp)
adb shell am instrument -w -r ${args[@]+"${args[@]}"} co.lunt.andash.test/androidx.test.runner.AndroidJUnitRunner > "$out" 2>&1 || true
if grep -q "shortMsg" "$out"; then cat "$out" | head -30; echo "FAILED (crash)"; exit 1; fi
python3 scripts/instrument-report.py "$out" && echo PASSED || { echo FAILED; exit 1; }
