# Releasing Stash

Releases are GitHub Releases with a signed APK, built by
`.github/workflows/release.yml` when a version tag is pushed. The signing key
lives only in GitHub secrets and in your own safe backup, never in the repo.

## One-time setup

### 1. Create the signing key (on your Mac)

Android Studio already ships `keytool`, so nothing needs installing:

```bash
KEYTOOL="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool"

"$KEYTOOL" -genkeypair -v \
  -storetype PKCS12 \
  -keystore ~/stash-release.jks \
  -alias stash \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -dname "CN=Stash"
```

- Choose a long password when asked. With PKCS12 the key password **is** the
  keystore password, so you only set one.
- `-dname` is shown publicly in every APK's certificate; `CN=Stash` keeps your
  name out of it. Change it if you'd like your name or handle there.
- `-validity 10000` ≈ 27 years. Google Play (should you ever use it) requires a
  key valid past 2033.
- Keep the file **outside** the project folder. (`*.jks` is git-ignored anyway.)

### 2. Back it up. Losing it means losing updates.

Every future release must be signed with this exact key. If it's lost, existing
users can't update: they'd have to uninstall, which deletes their stash unless
they made a backup. Store **both** the `.jks` file and its password somewhere
safe and separate from this Mac, e.g. a password manager's secure attachment.

Note the certificate fingerprint for your records (it's also printed in every
release's notes, so users can check it):

```bash
"$KEYTOOL" -list -v -keystore ~/stash-release.jks -alias stash | grep SHA256
```

This same key can later be registered in the Android Developer Console for
developer verification; nothing here has to change.

### 3. Give it to GitHub, locked to release tags

1. **Settings › Environments › New environment**, name it `release`.
2. Under **Deployment branches and tags**, choose **Selected branches and tags**
   › **Add deployment branch or tag rule** › type **Tag**, pattern `v*`.
   Now only jobs for `v*` tags can read this environment's secrets, not
   branches and not pull requests.
3. Copy the keystore as text:
   ```bash
   base64 -i ~/stash-release.jks | pbcopy
   ```
4. In the `release` environment, under **Environment secrets**, add:

   | Name | Value |
   |---|---|
   | `STASH_KEYSTORE_BASE64` | paste (from step 3) |
   | `STASH_KEYSTORE_PASSWORD` | your keystore password |
   | `STASH_KEY_ALIAS` | `stash` |
   | `STASH_KEY_PASSWORD` | the same password again |

5. Clear the clipboard afterwards (copy something else).

Optional: **Settings › Rules › Rulesets › New tag ruleset** targeting `v*`,
restricting creation and deletion to you, so release tags can't be created or
moved by anyone else.

## Making a release

1. Make sure everything for the release is merged into `main` and CI is green.
2. Tag the commit on `main` and push the tag:
   ```bash
   git switch main && git pull
   git tag -a v0.2.0 -m "Stash 0.2.0"
   git push origin v0.2.0
   ```
3. Watch **Actions › Release**. When it's done, the release appears under
   **Releases** with `stash-0.2.0.apk`, its `.sha256`, the signing certificate
   fingerprint, and notes generated from the merged PRs.

### Version numbers

The tag is the only place a version is written:

| Tag | versionName | versionCode |
|---|---|---|
| `v0.2.0-beta.1` | 0.2.0-beta.1 (marked pre-release) | 20001 |
| `v0.2.0-rc.1` | 0.2.0-rc.1 (marked pre-release) | 20051 |
| `v0.2.0` | 0.2.0 | 20099 |
| `v1.0.0` | 1.0.0 | 1000099 |

Codes always increase in that order (Android refuses to "update" to a lower
one). Limits: minor and patch ≤ 99, `beta.1`–`beta.49`, `rc.1`–`rc.48`.
The workflow rejects any other tag format, and any tag not on `main`.

### If something goes wrong

- **"No signing key" / "Unsigned build"**: a secret is missing or misnamed in
  the `release` environment.
- **Release failed after the tag was pushed**: fix on `main` via a PR, then
  delete the tag (`git push origin :refs/tags/v0.2.0 && git tag -d v0.2.0`)
  and tag again. Only do this if no release was published for that tag.
- Local builds never need the key: without the `STASH_KEYSTORE_*` variables,
  `./gradlew assembleRelease` makes an unsigned APK.
