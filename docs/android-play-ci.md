# Android Monthly Play Internal Testing

The [`Android Play internal`](../.github/workflows/android-play.yml) workflow builds a signed
release bundle and publishes it to the **internal testing** track on Google Play
at 06:00 UTC on the 1st of every month. It can also be run at any time from the
GitHub Actions tab via **Run workflow**.

This is the Android counterpart of the iOS TestFlight workflow. Internal testers
can install the build from the Play Store once Google finishes processing it.

## One-time setup

Five repository secrets are required under
**Settings → Secrets and variables → Actions → New repository secret**.

| Secret | What it is |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | The Play upload keystore (`.jks`), base64-encoded |
| `ANDROID_KEYSTORE_PASSWORD` | Password for that keystore |
| `ANDROID_KEY_ALIAS` | Alias of the upload key inside the keystore |
| `ANDROID_KEY_PASSWORD` | Password for that alias. Use the keystore password again if they are the same |
| `PLAY_SERVICE_ACCOUNT_JSON` | The full JSON key for a Play Console service account |

The app package is `com.nazulay.tiresairpressuremanagement`. It must already exist
in Play Console, and the internal testing track must have at least one tester.

### 1. Upload keystore

If this app is already on Play, export the **upload key** you used for the first
release. A new keystore will be rejected.

If the app has never been uploaded, create one and keep the file somewhere safe.
Losing it means you cannot ship updates.

```bash
keytool -genkeypair -v \
  -keystore upload.jks \
  -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -i upload.jks | pbcopy
```

Paste the clipboard into `ANDROID_KEYSTORE_BASE64`. The alias in that command is
`upload`.

### 2. Play Console service account

1. In [Google Cloud](https://console.cloud.google.com/), create a service account
   and download its JSON key.
2. In [Play Console → Users and permissions](https://play.google.com/console/users-and-permissions),
   invite that service account email.
3. Grant it permission to release to testing tracks for this app.
4. Paste the entire JSON file into `PLAY_SERVICE_ACCOUNT_JSON`.

## Version codes

`versionCode` must increase for every Play upload. CI sets it to
`BUILD_NUMBER_BASE + GITHUB_RUN_NUMBER`, where `BUILD_NUMBER_BASE` is at the top
of the workflow and currently equals `668`. The current version name is `2.1.0`. Play also requires target SDK 36.

Local Android Studio builds still increment `app/version.properties` on their own.
That file is not used by CI. If you upload a higher build from Android Studio,
raise `BUILD_NUMBER_BASE` so the next monthly build stays ahead of it.

The signed `.aab` is also attached to the workflow run as `android-release-aab`
for 14 days.
