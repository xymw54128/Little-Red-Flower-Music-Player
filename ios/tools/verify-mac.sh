#!/bin/bash
# Run from any directory on a Mac with Xcode 16 or newer and an installed iOS simulator.
set -euo pipefail
cd "$(dirname "$0")/.."
if ! xcrun --find swift >/dev/null 2>&1 || ! xcrun --find xcodebuild >/dev/null 2>&1; then
  echo "Select a full Xcode installation in Xcode > Settings > Locations first." >&2
  exit 1
fi
swift test
destination="${IOS_DESTINATION:-}"
if [ -z "$destination" ]; then
  device_id=$(xcrun simctl list devices available -j | /usr/bin/python3 -c 'import sys,json; d=json.load(sys.stdin); phones=[v for k,vs in d["devices"].items() if "iOS" in k for v in vs if v.get("isAvailable") and "iPhone" in v["name"]]; print(next((v["udid"] for v in phones if v["state"]=="Booted"), phones[0]["udid"] if phones else ""))')
  if [ -z "$device_id" ]; then
    echo "Install an iOS 17 or newer iPhone simulator in Xcode first." >&2
    exit 1
  fi
  destination="platform=iOS Simulator,id=$device_id"
fi
mkdir -p verification/mac
xcodebuild -project Xiaohonghua.xcodeproj -scheme Xiaohonghua -configuration Debug \
  -destination "$destination" -derivedDataPath DerivedData \
  -resultBundlePath "verification/mac/Tests-$(date +%Y%m%d-%H%M%S).xcresult" \
  CODE_SIGNING_ALLOWED=NO test
xcodebuild -project Xiaohonghua.xcodeproj -scheme Xiaohonghua -configuration Release \
  -destination 'generic/platform=iOS' -derivedDataPath DerivedData \
  CODE_SIGNING_ALLOWED=NO build
echo "Core tests, simulator tests and unsigned Release build passed. Device signing is a separate Xcode step."
