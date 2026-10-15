# Health Connect Test Data Writer

A small Android sample app for developers who need to test reading and aggregation of step records with Google Health Connect.

## What it does

- Requests Health Connect read/write permissions for step records.
- Lets you enter a bounded test count (1–100,000).
- Writes one synthetic step record with Health Connect's `MANUAL_ENTRY` recording method.
- Reads the current day's aggregate for basic integration testing.
- Uses the device's local timezone when calculating daily aggregation boundaries.
- Does not include analytics or advertising SDKs in the app module and does not request the Android `INTERNET` permission.
- Disables Android backup for the app.

**Important:** This app writes synthetic test data, not evidence of physical activity. Other apps may ignore manually entered records or apply their own validation. Do not use test records to claim rewards, discounts, prizes, or other benefits that require genuine activity. Use a dedicated test device and remove test records after testing.

## Requirements

- Android 14 or newer (API 34+)
- JDK 17
- Android SDK platform 35
- Google Health Connect available on the device

## Build and test

The repository includes a Windows Gradle wrapper script. From a Windows command prompt with JDK 17 and Android SDK configured:

```bat
gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is normally written to `app/build/outputs/apk/debug/app-debug.apk`.

On systems without the wrapper script for that operating system, install Gradle 8.9 and run:

```sh
gradle testDebugUnitTest assembleDebug
```

## Permissions and privacy

The app requests Health Connect step read/write permissions only. It stores no credentials and has no network permission. App backup is disabled. Use a test device or account, and remove test records when no longer needed.

## Dependency notes

The app uses the stable Health Connect client 1.1.0. See the official [Health Connect documentation](https://developer.android.com/health-and-fitness/health-connect) for platform compatibility and permission guidance.
