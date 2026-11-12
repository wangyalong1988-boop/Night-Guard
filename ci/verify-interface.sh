#!/usr/bin/env bash
# Collect screenshots while the emulator is still running, including on a test failure.
set +e
gradle --no-daemon :app:connectedDebugAndroidTest
verification_status=$?
mkdir -p screenshots
for image in home apps-all apps-search apps-media apps-keyboard; do
  adb exec-out cat "/data/local/tmp/nightguard-$image.png" > "screenshots/$image.png"
  if [ $? -ne 0 ]; then rm -f "screenshots/$image.png"; fi
done
python - <<'PYVERIFY'
from pathlib import Path
for name in ("home", "apps-all", "apps-search", "apps-media", "apps-keyboard"):
    path = Path("screenshots") / (name + ".png")
    assert path.is_file() and path.read_bytes()[:8] == b"\x89PNG\r\n\x1a\n", "Missing or invalid screenshot: " + name
PYVERIFY
capture_status=$?
if [ "$verification_status" -ne 0 ]; then exit "$verification_status"; fi
exit "$capture_status"
