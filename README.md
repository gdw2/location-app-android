# Donda Location (Android)

A no-UI-in-normal-use Android app that receives a **high-priority FCM push**, grabs a
single fresh location fix, and posts it to a [Dawarich](https://dawarich.app) instance.
It is configured once via a `locationapp://setup` deep link and then runs in the
background. It has no dependency on the Dawarich or OwnTracks apps.

Part of the Donda Alexa skill: the skill triggers a push, waits a few seconds, then reads
the fresh point back from Dawarich.

## How it works

```
setup link (locationapp://setup?api=...&token=...)
        │
        ▼
MainActivity ──enroll──▶ Donda server  ──returns──▶ Dawarich URL + API key
        │
        │ (FirebaseMessagingService.onNewToken)
        └──register──▶ Donda server

FCM "locate" push (data-only, priority HIGH)
        │
        ▼
LocationMessagingService ──▶ LocationFetchService (foreground, transient notif)
        │
        ▼
FusedLocationProvider (fresh fix, last-known fallback)
        │
        ▼
POST {dawarich}/api/v1/points?api_key=...
```

Battery profile is good: the app is idle except when a push arrives; FCM rides the shared
Google Play Services connection, and each request performs one location fix.

## Requirements

- Android 8.0+ (minSdk 26), Google Play Services.
- A Firebase project with an Android app registered for `com.gdw2.locationapp`.
- The Donda server running with a Firebase service account (see the Donda repo).

## Setup screen

Launching the app (or opening a setup link) shows a screen that:

- shows enrollment status and the Dawarich/server URLs;
- walks through each permission with a status and a button to grant it:
  - Notifications
  - Location (while using)
  - Location (background / "Allow all the time")
  - Battery optimization exemption
- offers **Register with server** (re-push the FCM token) and **Send location now**, which
  fetches a fix and posts it immediately, showing the result (HTTP status + accuracy).

## Deep link

Preferred (tappable in email/browser; unverified App Link, no assetlinks needed):

```
https://donda.gdw2.com/loc/setup?token=<one-time-token>
```

Fallback custom scheme:

```
locationapp://setup?api=https%3A%2F%2Fdonda.gdw2.com&token=<one-time-token>
```

The token is single-use and expires (default 24h). Generate one on the Donda server:

```bash
npm run make-setup-link -- --person Greg --email gdwar@gmail.com --donda-url https://donda.gdw2.com
```

Deliver the printed link out of band (no public asset links are served). When tapping the
`https://` link, choose **Donda Location** in the Android chooser.

## Build

### Locally (devbox)
```bash
devbox install            # JDK 17 + Gradle
devbox run setup-android  # downloads the Android SDK into ./.android-sdk (~1 GB)
# put your Firebase config at app/google-services.json (gitignored)
devbox run build-debug    # -> app/build/outputs/apk/debug/app-debug.apk
devbox run build          # signed release (set KEYSTORE_PATH/KEYSTORE_PASSWORD/... env)
# or open the project in Android Studio
```

### Release (CI)
Pushing a tag like `v0.1.0` runs `.github/workflows/release.yml`, which builds and signs
the release APK and attaches it to a GitHub Release. Required repository secrets:

| Secret | Purpose |
| --- | --- |
| `GOOGLE_SERVICES_JSON` | base64 of `app/google-services.json` |
| `KEYSTORE_BASE64` | base64 of the release keystore (`.jks`) |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

Create the base64 values with:
```bash
base64 -w0 app/google-services.json   # -> GOOGLE_SERVICES_JSON
base64 -w0 release.jks                # -> KEYSTORE_BASE64
```

The Firebase Android app must be registered with the **release** keystore's SHA-1 and
SHA-256 fingerprints (obtain with `keytool -list -v -keystore release.jks`).

> The repo is private by default. For the simplest Obtainium setup, either make it public
> or give Obtainium a GitHub token with access to this repo.

## Install via Obtainium

1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. Add app → `https://github.com/gdw2/location-app-android`.
3. Obtainium tracks GitHub Releases and installs/updates the APK automatically.

After installing, open the setup link on the device to enroll.

## Permissions

| Permission | Why |
| --- | --- |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Get the device location |
| `ACCESS_BACKGROUND_LOCATION` | Required to fetch location when woken from the background |
| `POST_NOTIFICATIONS` | Foreground-service notification during a fetch |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION` | Location foreground service |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Keep FCM delivery reliable |

## Testing

- Firebase Console → Messaging → send a **data** message `{"type":"locate"}` with
  Android priority high to the device token, or trigger via the Donda skill.
- Confirm the point appears in Dawarich.
- Doze: `adb shell dumpsys deviceidle force-idle`, then send the push.
- Note: a **force-stopped** app cannot receive FCM until it is opened again.

## Troubleshooting

- **Nothing happens on push**: verify notifications permission, background location
  permission, and that the app has not been force-stopped. Check `adb logcat -s
  LocationFetchService`.
- **`Send location now` says "No location fix"**: grant location permission and ensure
  device location is on.
- **Dawarich 401**: re-enroll (the API key may have rotated).
- **OEMs (Xiaomi/Huawei/Oppo/Vivo/Samsung)**: enable autostart and disable battery
  restrictions for the app.
