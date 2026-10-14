# Automatic builds and beta releases

The Build APK workflow runs on pushes to main, pull requests into main, matching v* tags and Actions â†’ Build APK â†’ Run workflow. Download the APK bundle from the completed run's Artifacts section.

## One-time signing setup

To produce updates compatible with the existing local app, add the existing signing key as a repository Actions secret:

1. On GitHub, open Settings â†’ Secrets and variables â†’ Actions â†’ New repository secret.
2. Name it `PICO_CAPTURE_KEYSTORE_BASE64`.
3. In a local PowerShell terminal at the project root, copy the encoded key directly to your clipboard:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path 'signing/debug.keystore').Path)) | Set-Clipboard
```

4. Paste into the GitHub secret field and save. Clear your clipboard afterward. Never paste the value into chat, source files or a commit.

This workflow expects the existing local debug keystore's `androiddebugkey` alias and `android` store/key passwords, as already configured in the app build. It does not use an SSH key or SSH passphrase. A differently configured keystore requires a corresponding signing configuration change.

Without this secret, ordinary builds still produce an installable temporary-key debug APK and an unsigned release APK. Temporary debug keys can differ between runners, so those APKs may not update each other or the local installation. Pull requests never receive the project signing key. Release commits and tagged releases require the secret; they fail if it is missing.

## Release with your own changelog

The published notes come from the matching version heading in CHANGELOG.md, not GitHub's pull-request summaries or an AI guess. The existing `## [0.1-beta]` section contains the initial beta notes.

For future versions:

1. Set appVersionName in gradle.properties, for example `0.2-beta.2`, and increase appVersionCode.
2. Write the changes under `## [0.2-beta.2]` in CHANGELOG.md. An Unreleased section is for future work and is not published.
3. Commit normally to build only, or use `[release] VERSION` as the commit subject to publish. The version must match gradle.properties.

For the current 0.2 beta, commit and push from main:

```powershell
git add -A
git commit -m "[release] 0.2-beta"
git push origin main
```

The workflow builds, checks signing, creates the matching `v0.2-beta` tag at that exact commit, and publishes a prerelease with your authored notes and APKs. A local commit alone does not trigger Actions; it must be pushed.

Alternatively, push a matching tag after a normal commit:

```powershell
git tag v0.2-beta
git push origin v0.2-beta
```

Use one release trigger per version. The release stops if the version's notes are empty/missing, the signing secret is missing, or an existing tag points to a different commit. An existing published version is not overwritten: use a new version for the next release.

Public releases attach only the signed install APK, LICENSES.txt and SHA256SUMS.txt. The changelog is displayed in the release description. Debug APKs, build-info.json and RELEASE_NOTES.md stay in Actions artifacts, whose checksums cover the full build bundle. GitHub also provides source ZIP/tar archives automatically. The organization must allow Actions and the release job's repository write permission. Version code 13 keeps 0.2-beta newer than 0.1-beta (code 12); the internal code is separate from the displayed version.

Automatic builds verify full debug lint, compilation, release vital lint and packaging. They do not establish recording performance or compatibility on an actual headset.
