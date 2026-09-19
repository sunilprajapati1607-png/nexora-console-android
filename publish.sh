#!/usr/bin/env bash
#
# Build the console, put the APK where the phones can fetch it, and write
# the version file the service reads.
#
#   bash publish.sh "what changed in this build"
#
# After this, committing and pushing is the whole of publishing: the
# service reads releases/latest.json, every phone sees the new version at
# its next check, and two taps install it.
#
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
NOTES="${1:-}"

export JAVA_HOME='D:\android-tools\jdk17'
export ANDROID_HOME='D:\android-tools\sdk'
GRADLE='D:\android-tools\gradle-8.7\bin\gradle.bat'

echo "== building =="
cmd //c "$GRADLE --project-dir=$(cygpath -w "$ROOT") --no-daemon --console=plain assembleDebug" | tail -3

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "no APK was produced"; exit 1; }

# The version this build actually is, read from the build file rather than
# typed again here — two places to change a version is one place too many.
CODE=$(grep -oE 'versionCode *= *[0-9]+' "$ROOT/app/build.gradle.kts" | grep -oE '[0-9]+')
NAME=$(grep -oE 'versionName *= *"[^"]+"' "$ROOT/app/build.gradle.kts" | sed 's/.*"\(.*\)"/\1/')

mkdir -p "$ROOT/releases"
OUT="$ROOT/releases/nexora-console-$NAME.apk"
cp "$APK" "$OUT"

SHA=$(sha256sum "$OUT" | cut -d' ' -f1)
SIZE=$(stat -c%s "$OUT")

# The raw address of the file just written, in this repository.
REMOTE=$(git -C "$ROOT" remote get-url origin 2>/dev/null || echo '')
SLUG=$(echo "$REMOTE" | sed -E 's#.*github.com[:/]([^/]+/[^/.]+)(\.git)?#\1#')
BRANCH=$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo main)
URL="https://raw.githubusercontent.com/$SLUG/$BRANCH/releases/nexora-console-$NAME.apk"

cat > "$ROOT/releases/latest.json" <<JSON
{
  "versionCode": $CODE,
  "versionName": "$NAME",
  "url": "$URL",
  "notes": $(node -e 'process.stdout.write(JSON.stringify(process.argv[1]||""))' "$NOTES"),
  "sha256": "$SHA",
  "sizeBytes": $SIZE,
  "publishedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
JSON

echo
echo "== $NAME (code $CODE), $(echo "scale=1; $SIZE/1048576" | bc) MB =="
echo "   $OUT"
echo "   $URL"
echo
echo "Now:  git add -A && git commit -m \"console $NAME\" && git push"
echo "Every phone offers it within about five minutes of that push."
