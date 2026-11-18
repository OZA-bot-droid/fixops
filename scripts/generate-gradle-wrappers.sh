#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/versions.env"
command -v gradle >/dev/null || { echo 'Install a temporary Gradle launcher to generate wrappers'; exit 1; }
for dir in gateway member-service asset-service workorder-service inventory-service; do
  echo "Creating wrapper for $dir - $GRADLE_VERSION"
  (cd "$ROOT/$dir" && gradle wrapper --gradle-version "$GRADLE_VERSION" --distribution-type bin)
done
echo 'Commit all gradlew, gradlew.bat, gradle/wrapper/* files.'
