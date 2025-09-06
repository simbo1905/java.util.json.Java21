# Tag-Triggered Maven Central Release (GitHub Actions)

- Trigger: push tag `release/X.Y.Z` (no leading `v`).
- CI creates a GitHub Release from the tag, then deploys to Maven Central.
- CI opens a PR back to `main` from `release-bot-YYYYMMDD-HHMMSS` (no version bumps).

## Workflow (drop-in)

```yaml
name: Release on Tag
on:
  push:
    tags:
      - 'release/[0-9]*.[0-9]*.[0-9]*'
permissions:
  contents: write
  pull-requests: write
jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
          server-id: central
          server-username: ${{ secrets.CENTRAL_USERNAME }}
          server-password: ${{ secrets.CENTRAL_PASSWORD }}
          gpg-private-key: ${{ secrets.GPG_PRIVATE_KEY }}
          gpg-passphrase: ${{ secrets.GPG_PASSPHRASE }}
      - uses: softprops/action-gh-release@v2
        with:
          tag_name: ${{ github.ref_name }}
          generate_release_notes: true
      - name: Build and Deploy to Central (release profile)
        env:
          CENTRAL_USERNAME: ${{ secrets.CENTRAL_USERNAME }}
          CENTRAL_PASSWORD: ${{ secrets.CENTRAL_PASSWORD }}
        run: |
          KN="${{ secrets.GPG_KEYNAME }}"; EXTRA=""; [ -n "$KN" ] && EXTRA="-Dgpg.keyname=$KN";
          mvn -B -ntp -P release -Dgpg.passphrase="${{ secrets.GPG_PASSPHRASE }}" $EXTRA clean deploy
      - name: Configure Git identity
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
      - name: Create branch from tag and PR to main
        env: { GH_TOKEN: ${{ github.token }} }
        run: |
          BRANCH_NAME="release-bot-$(date +%Y%m%d-%H%M%S)"
          git checkout -B "$BRANCH_NAME" $GITHUB_SHA
          git push origin "$BRANCH_NAME"
          gh pr create \
            --title "chore: merge release ${{ github.ref_name }} to main" \
            --body "Automated PR created from tag ${{ github.ref_name }}." \
            --base main \
            --head "$BRANCH_NAME" || true
```

## Secrets (one-time)

- CENTRAL_USERNAME, CENTRAL_PASSWORD (Central Portal token)
- GPG_PRIVATE_KEY (ASCII-armored secret key), GPG_PASSPHRASE
- GPG_KEYNAME (optional; fingerprint of signing key — helper script sets it; if absent, default key is used)

zsh helper (uses gh, gpg) — auto-detects a signing key if not provided:

```zsh
#!/usr/bin/env zsh
set -euo pipefail
export CENTRAL_USERNAME=your_user
export CENTRAL_PASSWORD=your_pass
export GPG_PASSPHRASE=your_passphrase
export GPG_KEY_ID=YOUR_KEY_ID   # or export GPG_PRIVATE_KEY="$(gpg --armor --export-secret-keys YOUR_KEY_ID)"
./scripts/setup-release-secrets.zsh

# If you don't set GPG_KEY_ID or GPG_PRIVATE_KEY, the script tries to
# auto-detect a signing key. To see candidates explicitly:
gpg --list-secret-keys --keyid-format=long
```

## Trigger a Release

```bash
git tag 'release/0.1.0'
git push origin 'release/0.1.0'
```

## Publish this doc as a Gist

```bash
gh gist create -p -d "Tag-triggered Maven Central release via GitHub Actions" RELEASE-GIST.md
```
