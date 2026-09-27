#!/usr/bin/env bash
# Build, tag and publish a GitHub Release with the mod jar attached.
# usage: tools/release.sh "short release notes"
# Token: fine-grained PAT for RandoDane/SCDv2 (Contents: read & write) in ~/.config/scd/github_token.
set -euo pipefail
cd "$(dirname "$0")/.."
REPO="RandoDane/SCDv2"
NOTES="${1:?release notes required}"
TOKEN="$(cat ~/.config/scd/github_token)"
VERSION="$(grep '^version=' gradle.properties | cut -d= -f2)"
TAG="v${VERSION}"
JAR="build/libs/scd-${VERSION}.jar"

export JAVA_HOME="$(ls -d /root/.jdks/jdk-25* | head -1)"
[ -s ~/.config/scd/mod_key ] || { echo "missing ~/.config/scd/mod_key (built-in scd.wtf key)"; exit 1; }
./gradlew clean build --no-daemon -q
[ -f "$JAR" ] || { echo "missing $JAR"; exit 1; }
git diff --quiet && git diff --cached --quiet || { echo "working tree not clean - commit first"; exit 1; }
if git log --format=%B -1 | grep -qiE 'claude|anthropic'; then echo "attribution found in last commit"; exit 1; fi

git tag -a "$TAG" -m "$TAG" 2>/dev/null || { echo "tag $TAG exists - bump version in gradle.properties"; exit 1; }
git push -q origin main "$TAG"

API="https://api.github.com/repos/${REPO}"
AUTH=(-H "Authorization: Bearer ${TOKEN}" -H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28")
BODY="$(python3 -c 'import json,sys; print(json.dumps({"tag_name":sys.argv[1],"name":sys.argv[1],"body":sys.argv[2]}))' "$TAG" "$NOTES")"
RESP="$(curl -sS "${AUTH[@]}" -X POST "${API}/releases" -d "$BODY")"
ID="$(python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("id") or ""); sys.exit(0 if d.get("id") else 1)' <<<"$RESP")" \
  || { echo "release creation failed: $RESP"; exit 1; }
curl -sS "${AUTH[@]}" -H "Content-Type: application/java-archive" --data-binary @"$JAR" \
  "https://uploads.github.com/repos/${REPO}/releases/${ID}/assets?name=$(basename "$JAR")" >/dev/null
echo "released ${TAG}: https://github.com/${REPO}/releases/tag/${TAG}"
