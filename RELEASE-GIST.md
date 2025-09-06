# Tag-Triggered Maven Central Release (GitHub Actions)

- Trigger: push tag `releases/X.Y.Z` (no leading `v`).
- CI creates a GitHub Release from the tag, then deploys to Maven Central.
- CI opens a PR back to `main` from `release-bot-YYYYMMDD-HHMMSS` (no version bumps).

## Workflow (drop-in)

```yaml
name: Release on Tag
on:
  push:
    tags:
      - 'releases/*'
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
          server-username: CENTRAL_USERNAME
          server-password: CENTRAL_PASSWORD
          gpg-private-key: ${{ secrets.GPG_PRIVATE_KEY }}
          gpg-passphrase: ${{ secrets.GPG_PASSPHRASE }}
      - uses: softprops/action-gh-release@v2
        with:
          tag_name: ${{ github.ref_name }}
          generate_release_notes: true
      - name: Build and Deploy to Central
        env:
          CENTRAL_USERNAME: ${{ secrets.CENTRAL_USERNAME }}
          CENTRAL_PASSWORD: ${{ secrets.CENTRAL_PASSWORD }}
        run: mvn -B -ntp clean deploy
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

zsh helper (uses gh, gpg):

```zsh
#!/usr/bin/env zsh
set -euo pipefail
export CENTRAL_USERNAME=your_user
export CENTRAL_PASSWORD=your_pass
export GPG_PASSPHRASE=your_passphrase
export GPG_KEY_ID=YOUR_KEY_ID   # or export GPG_PRIVATE_KEY="$(gpg --armor --export-secret-keys YOUR_KEY_ID)"
./scripts/setup-release-secrets.zsh
```

## Trigger a Release

```bash
git tag 'releases/0.1.0'
git push origin 'releases/0.1.0'
```

## Publish this doc as a Gist

```bash
gh gist create -p -d "Tag-triggered Maven Central release via GitHub Actions" RELEASE-GIST.md
```
