# Automatic builds and 0.1 Beta releases

The Build APK workflow runs on pushes to main, pull requests into main, matching v* tags and Actions → Build APK → Run workflow. Download the APK bundle from the completed run's Artifacts section.

## One-time signing setup

To produce updates compatible with the existing local app, add the existing signing key as a repository Actions secret:

1. On GitHub, open Settings → Secrets and variables → Actions → New repository secret.
2. Name it `PICO_CAPTURE_KEYSTORE_BASE64`.
3. In a local PowerShell terminal at the project root, copy the encoded key directly to your clipboard:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path 'signing/debug.keystore').Path)) | Set-Clipboard
```

4. Paste into the GitHub secret field and save. Clear your clipboard afterward. Never paste the value into chat, source files or a commit.

This workflow expects the existing local debug keystore's `androiddebugkey` alias and `android` store/key passwords, as already configured in the app build. It does not use an SSH key or SSH passphrase. A differently configured keystore requires a corresponding signing configuration change.

Without this secret, ordinary builds still produce an installable temporary-key debug APK and an unsigned release APK. Temporary debug keys can differ between runners, so those APKs may not update each other or the local installation. Pull requests never receive the project signing key. Tagged releases require the secret and fail rather than publishing an unsigned APK.

## Publish 0.1 Beta

First commit and push the workflow and version changes. Then run:

```powershell
git tag v0.1-beta
git push origin v0.1-beta
```

After the build passes, the workflow creates a GitHub prerelease containing the signed `PicoCapture-0.1-beta.apk`, debug APK, checksums, build metadata and license notices. The organization must allow GitHub Actions and the workflow's release job to write repository contents.

The tag must match the appVersionName in gradle.properties. For future versions, update appVersionName and increase appVersionCode there before tagging. Version code 12 keeps this 0.1-beta build newer than the previous local build's code 11; it is separate from the displayed version.

Automatic builds verify full debug lint, compilation, release vital lint and packaging. They do not establish recording performance or compatibility on an actual headset.
