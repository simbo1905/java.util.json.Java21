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
  if [[ -z "${GPG_KEY_ID:-}" ]]; then
    echo "No GPG_PRIVATE_KEY or GPG_KEY_ID provided. Attempting auto-detect..."
    # Find first signing-capable secret key (sec) via machine-readable output
    # Field 1=type (sec), 5=keyid, 12=capabilities (contains 's' when signing)
    CANDIDATE=$(gpg --list-secret-keys --with-colons 2>/dev/null | awk -F: '$1=="sec" && $12 ~ /s/ {print $5; exit}')
    if [[ -n "$CANDIDATE" ]]; then
      echo "Auto-detected signing key: $CANDIDATE"
      GPG_KEY_ID="$CANDIDATE"
    else
      echo "Could not auto-detect a signing key. Available secret keys:" >&2
      gpg --list-secret-keys --keyid-format=long || true
      echo "Set GPG_KEY_ID or GPG_PRIVATE_KEY and re-run." >&2
      exit 1
    fi
  fi
  echo "Exporting secret key for $GPG_KEY_ID ..."
  # Try non-interactive export using loopback pinentry and provided passphrase.
  # If the agent disallows loopback, this may still prompt; in that case, instruct manual export.
  if gpg --batch --yes --pinentry-mode loopback --passphrase "${GPG_PASSPHRASE:-}" --armor --export-secret-keys "$GPG_KEY_ID" 2>/dev/null | gh secret set GPG_PRIVATE_KEY --app actions ${REPO_FLAG:+${REPO_FLAG[@]}}; then
    :
  else
    echo "Non-interactive export failed. Listing keys and instructions:" >&2
    gpg --list-secret-keys --keyid-format=long || true
    echo "Workaround: export your key and re-run with GPG_PRIVATE_KEY env var:" >&2
    echo "  gpg --armor --export-secret-keys $GPG_KEY_ID > /tmp/secret.asc" >&2
    echo "  GPG_PRIVATE_KEY=\"\$(cat /tmp/secret.asc)\" $0 ${REPO_FLAG:+--repo ${2:-}}" >&2
    exit 1
  fi
fi

# Also persist a key name (fingerprint) for maven-gpg-plugin selection
KEY_FPR=$(gpg --with-colons --list-secret-keys "$GPG_KEY_ID" 2>/dev/null | awk -F: '$1=="fpr"{print $10; exit}')
if [[ -n "$KEY_FPR" ]]; then
  echo "Setting GPG_KEYNAME (fingerprint) for CI: $KEY_FPR"
  print -r -- "$KEY_FPR" | gh secret set GPG_KEYNAME --app actions ${REPO_FLAG:+${REPO_FLAG[@]}} || true
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
