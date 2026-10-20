# PassWall Control — stable Android APK signing

Release APKs must be signed with the SAME certificate on every build. GitHub-hosted runners generate different debug keys, so old debug APKs cannot be reliably updated in place.

## One-time GitHub setup

1. Safely store the **passwall-fixed-signing-backup.zip** supplied in the ChatGPT conversation. Do not add it to this public GitHub repository.
2. Extract `signing-secrets.txt`.
3. Open this repository's **Settings → Secrets and variables → Actions → New repository secret** and create:
   - `ANDROID_SIGNING_KEYSTORE_BASE64`: the complete long base64 line under that heading in the text file.
   - `ANDROID_SIGNING_PASSWORD`: the password line under that heading.
4. Open **Actions → Build Android APK → Run workflow**.
5. Download the `passwall-control-release-apk` artifact, unzip it, and install `app-release.apk`.

The build will fail with a clear message until both repository secrets are configured. Never place the keystore, password, or base64 value into a committed file, issue, or public Actions log.

## Updates

- Only distribute the signed **release APK** from this workflow, not a local or GitHub-generated debug APK.
- The Android `applicationId` stays `com.wangkai.passwallcontrol`.
- CI uses `GITHUB_RUN_NUMBER` for a monotonically increasing `versionCode`.
- Keep a secure backup of the original signing keystore and password. Losing the key means future APKs cannot overwrite versions signed with it.
- **One final uninstall** of the existing differently-signed debug app is required before installing the first fixed-signature release. All subsequent releases using this key can be installed over one another without uninstalling.

## Local build

Place the original keystore at `.android-signing/passwall-upload-key.p12` and supply the password through environment variable `ANDROID_SIGNING_PASSWORD`; then run `gradle assembleRelease`. Keep the key out of version control.
