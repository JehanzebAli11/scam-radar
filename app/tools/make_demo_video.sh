#!/usr/bin/env bash
# Record the demo walkthrough: render frames with Robolectric, then build an MP4.
# Usage (from app/): tools/make_demo_video.sh [out.mp4]
set -euo pipefail
cd "$(dirname "$0")/.."
out="${1:-build/scamradar-demo.mp4}"
frames="build/demo-frames"
rm -rf "$frames"
./gradlew :android:testDebugUnitTest --tests '*DemoVideoTest*' -Dscamradar.demoFrames="$PWD/$frames" "${GRADLE_ARGS[@]:-}"
mkdir -p "$(dirname "$out")"
python3 tools/make_demo_video.py "$frames" "$out"
