# iOS Monthly TestFlight Automation

The [`iOS TestFlight`](../.github/workflows/ios-testflight.yml) workflow builds the
`TirePressureManager` scheme in Release configuration and uploads it to TestFlight
at 06:00 UTC on the 1st of every month. It can also be run at any time from the
GitHub Actions tab via **Run workflow**.

The build itself is driven by [`fastlane`](../TirePressureManager-iOS/fastlane/Fastfile)
(the `beta` lane), so the exact same process can be run locally.

## One-time setup

Five repository secrets are required. Add them under
**Settings → Secrets and variables → Actions → New repository secret**.

| Secret | What it is |
| --- | --- |
| `ASC_ISSUER_ID` | App Store Connect API issuer ID (a UUID) |
| `ASC_KEY_ID` | App Store Connect API key ID (10 characters) |
| `ASC_KEY_P8_BASE64` | The `.p8` private key file, base64-encoded |
| `IOS_DIST_CERT_P12_BASE64` | Your Apple Distribution certificate and private key as a `.p12`, base64-encoded |
| `IOS_DIST_CERT_PASSWORD` | The password you set when exporting the `.p12` |

### 1. App Store Connect API key

1. Go to [App Store Connect → Users and Access → Integrations → App Store Connect API](https://appstoreconnect.apple.com/access/integrations/api).
2. Create a key with the **App Manager** role. Anything less cannot manage
   provisioning profiles, which this workflow relies on.
3. Note the **Issuer ID** and **Key ID**, and download the `.p8` file. Apple only
   allows this download once.
4. Encode it:

```bash
base64 -i AuthKey_XXXXXXXXXX.p8 | pbcopy
```

Paste the result into `ASC_KEY_P8_BASE64`.

### 2. Distribution certificate

The workflow lets Xcode create and refresh provisioning profiles on its own, but
the distribution *certificate* has to be supplied. Apple caps you at three
distribution certificates per account, and a CI machine that generates its own
would burn through that cap in three runs, so we reuse the one already in your
login keychain.

1. Open **Keychain Access → login → My Certificates**.
2. Find **Apple Distribution: … (6VD7L6T443)**, expand it to confirm it has a
   private key underneath, then right-click → **Export**.
3. Save as `.p12` and set a password. Use that password for `IOS_DIST_CERT_PASSWORD`.
4. Encode it:

```bash
base64 -i distribution.p12 | pbcopy
```

Paste the result into `IOS_DIST_CERT_P12_BASE64`.

Repeat this step when the certificate expires; the expiry date is shown in
Keychain Access and on the Apple Developer certificates page.

## Build numbers

`App/Info.plist` sets `CFBundleVersion` literally (the target has
`GENERATE_INFOPLIST_FILE = NO`), so the lane patches that file during the build.
The value is `BUILD_NUMBER_BASE + GITHUB_RUN_NUMBER`, where `BUILD_NUMBER_BASE` is
defined at the top of the workflow and currently equals `556`. The first automated
run therefore uploads build 557.

The patched `Info.plist` is not committed back, so the file in git stays at 556.

If you upload a build manually from Xcode, bump `BUILD_NUMBER_BASE` in the
workflow to stay ahead of it — TestFlight rejects a build whose number is not
higher than the previous one for the same version string.

Bumping the user-visible version (`CFBundleShortVersionString` / `MARKETING_VERSION`)
is still a manual edit, since a release version is a deliberate decision.

## Running the lane locally

```bash
cd TirePressureManager-iOS
bundle install
ASC_KEY_ID=... ASC_ISSUER_ID=... ASC_KEY_P8_BASE64="$(base64 -i AuthKey.p8)" \
  BUILD_NUMBER=557 bundle exec fastlane beta
```

Locally the lane skips the temporary keychain and certificate import, and signs
with whatever is already in your login keychain.

## Things worth knowing

- **GitHub pauses cron jobs on idle repositories.** If nothing is pushed for 60
  days, scheduled workflows are disabled and GitHub emails you a link to re-enable
  them. Running the workflow manually now and then avoids this.
- **Scheduled runs are not punctual.** GitHub queues cron jobs on a best-effort
  basis and delays of an hour or more are normal, especially on the hour. Nothing
  here depends on the exact minute.
- **macOS runners bill at 10× the Linux rate.** A run takes a few minutes, which
  is comfortably inside the free allowance for one build a month, but keep it in
  mind before increasing the frequency.
- The lane sets `skip_waiting_for_build_processing: true`, so the job finishes as
  soon as the upload completes rather than waiting for Apple to finish processing.
  Watch for the "ready to test" email, or check App Store Connect.
- Build logs are attached to each run as the `ios-build-logs` artifact, which is
  the first place to look when a build fails.
