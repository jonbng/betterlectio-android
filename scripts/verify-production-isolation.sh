#!/bin/sh
set -eu

[ "$#" -eq 1 ] || { echo "usage: $0 <release.apk|release.aab>" >&2; exit 2; }
ARTIFACT=$1
[ -f "$ARTIFACT" ] || { echo "error: artifact not found" >&2; exit 2; }

TEXT=$(mktemp)
trap 'rm -f "$TEXT"' EXIT
unzip -p "$ARTIFACT" | strings > "$TEXT"

fail_if_present() {
  if grep -Fq -- "$1" "$TEXT"; then
    echo "error: production artifact contains forbidden admin marker: $2" >&2
    exit 1
  fi
}

fail_if_present "/api/mobile/lectio-sessions" "mobile admin route"
fail_if_present "BetterLectio Admin" "admin app label"
fail_if_present "admin-android" "admin auth platform"
fail_if_present "Admin sessions" "admin session picker"

ADMIN_ENV="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)/admin/.env.local"
if [ -f "$ADMIN_ENV" ]; then
  TOKEN=$(awk -F= '$1 == "ADMIN_MOBILE_TOKEN" { sub(/^[^=]*=/, ""); gsub(/^['\''"]|['\''"]$/, ""); print; exit }' "$ADMIN_ENV")
  ORIGIN=$(awk -F= '$1 == "ADMIN_MOBILE_API_ORIGIN" { sub(/^[^=]*=/, ""); gsub(/^['\''"]|['\''"]$/, ""); print; exit }' "$ADMIN_ENV")
  [ -z "$TOKEN" ] || fail_if_present "$TOKEN" "configured admin token"
  [ -z "$ORIGIN" ] || fail_if_present "$ORIGIN" "configured admin origin"
fi

echo "Production artifact contains no admin markers or configured mobile secret."
