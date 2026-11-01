#!/bin/sh
set -eu

PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$PROJECT_ROOT"

if ! command -v posthog-cli >/dev/null 2>&1; then
  echo "error: posthog-cli is required for release mapping uploads." >&2
  echo "Install it with: npm install --global @posthog/cli@^0.7.12" >&2
  exit 1
fi

if [ -z "${POSTHOG_CLI_API_KEY:-}" ]; then
  if [ ! -t 0 ]; then
    echo "error: POSTHOG_CLI_API_KEY must be set for non-interactive builds." >&2
    exit 1
  fi

  printf "PostHog personal API key: " >&2
  trap 'stty echo 2>/dev/null || true' EXIT HUP INT TERM
  stty -echo
  IFS= read -r POSTHOG_CLI_API_KEY
  stty echo
  trap - EXIT HUP INT TERM
  printf '\n' >&2

  if [ -z "$POSTHOG_CLI_API_KEY" ]; then
    echo "error: PostHog personal API key cannot be empty." >&2
    exit 1
  fi
  export POSTHOG_CLI_API_KEY
fi

exec ./gradlew :app:bundleRelease "$@"
