#!/usr/bin/env zsh
set -euo pipefail

# Usage:
#   export CENTRAL_USERNAME=... CENTRAL_PASSWORD=...
#   export GPG_PASSPHRASE=...
#   # Either export a key id OR the armored secret key
#   # export GPG_KEY_ID=XXXXXXXX
#   # OR
#   # export GPG_PRIVATE_KEY="$(gpg --armor --export-secret-keys XXXXXXXX)"
#   ./scripts/setup-release-secrets.zsh [--repo <owner/repo>]

REPO_FLAG=()
if [[ ${1:-} == "--repo" && -n ${2:-} ]]; then
  REPO_FLAG=(--repo "$2")
  shift 2
fi

command -v gh >/dev/null 2>&1 || { echo "gh CLI is required" >&2; exit 1; }
command -v gpg >/dev/null 2>&1 || { echo "gpg is required" >&2; exit 1; }

echo "Checking gh auth..."
gh auth status >/dev/null 2>&1 || { echo "gh is not authenticated" >&2; exit 1; }

[[ -n "${CENTRAL_USERNAME:-}" ]] || { echo "CENTRAL_USERNAME env var required" >&2; exit 1; }
[[ -n "${CENTRAL_PASSWORD:-}" ]] || { echo "CENTRAL_PASSWORD env var required" >&2; exit 1; }
[[ -n "${GPG_PASSPHRASE:-}" ]] || { echo "GPG_PASSPHRASE env var required" >&2; exit 1; }

echo "Setting CENTRAL_USERNAME and CENTRAL_PASSWORD..."
print -r -- "$CENTRAL_USERNAME" | gh secret set CENTRAL_USERNAME --app actions ${REPO_FLAG:+${REPO_FLAG[@]}}
print -r -- "$CENTRAL_PASSWORD" | gh secret set CENTRAL_PASSWORD --app actions ${REPO_FLAG:+${REPO_FLAG[@]}}

# Handle private key: prefer provided armored key, else export from GPG_KEY_ID
if [[ -n "${GPG_PRIVATE_KEY:-}" ]]; then
  echo "Using provided GPG_PRIVATE_KEY..."
  print -r -- "$GPG_PRIVATE_KEY" | gh secret set GPG_PRIVATE_KEY --app actions ${REPO_FLAG:+${REPO_FLAG[@]}}
else
  [[ -n "${GPG_KEY_ID:-}" ]] || { echo "Provide GPG_PRIVATE_KEY or GPG_KEY_ID" >&2; exit 1; }
  echo "Exporting secret key for $GPG_KEY_ID ..."
  gpg --armor --export-secret-keys "$GPG_KEY_ID" | gh secret set GPG_PRIVATE_KEY --app actions ${REPO_FLAG:+${REPO_FLAG[@]}}
fi

print -r -- "$GPG_PASSPHRASE" | gh secret set GPG_PASSPHRASE --app actions ${REPO_FLAG:+${REPO_FLAG[@]}}

echo "Validating secrets presence..."
MISSING=0
for s in CENTRAL_USERNAME CENTRAL_PASSWORD GPG_PRIVATE_KEY GPG_PASSPHRASE; do
  if ! gh secret list --app actions ${REPO_FLAG:+${REPO_FLAG[@]}} | grep -q "^$s\b"; then
    echo "Missing secret: $s" >&2
    MISSING=1
  fi
done

if [[ $MISSING -ne 0 ]]; then
  echo "One or more secrets are missing" >&2
  exit 1
fi

echo "All secrets set. Done."

