#!/usr/bin/env bash
# Collect screenshots while the emulator is still running, including on a test failure.
set +e
gradle --no-daemon :app:connectedDebugAndroidTest
verification_status=$?
mkdir -p screenshots
for image in home apps-all apps-search apps-media; do
  adb exec-out run-as cn.nightguard cat "files/$image.png" > "screenshots/$image.png"
  if [ $? -ne 0 ]; then rm -f "screenshots/$image.png"; fi
done
exit "$verification_status"
