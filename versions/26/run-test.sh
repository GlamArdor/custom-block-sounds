#!/bin/sh
# usage: run-test.sh <extra gradle args>; starts the dev client, waits for the blockmap to load, then stops it
cd "$(dirname "$0")"
rm -rf run/.mixin.out run/logs/latest.log
./gradlew --no-daemon runClient "$@" > run-test.log 2>&1 &
for i in $(seq 1 90); do
	sleep 5
	grep -q "CustomBlockSounds\] Loaded" run/logs/latest.log 2>/dev/null && sleep 10 && break
	grep -qE "BUILD FAILED|Mixin apply failed|Minecraft Crash Report" run-test.log && break
done
powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | ? { \$_.CommandLine -like '*CustomBlockSounds*versions*26*' -or \$_.CommandLine -like '*customblocksounds*26*run*' } | % { Stop-Process -Id \$_.ProcessId -Force }"
