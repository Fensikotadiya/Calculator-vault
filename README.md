# Calculator Vault for Android

A native Java Android app with an everyday calculator and a PIN-protected local photo/video vault. Android 8.0 or newer. No internet permission, ads, or account required.

## Use

1. Install `app/build/outputs/apk/debug/app-debug.apk` on your Android phone.
2. Create and confirm a 6–12 digit PIN on first launch.
3. Enter that PIN in the calculator and tap **=** to open the vault.
4. Tap **Add photos or videos** and select media. Enter the PIN again when you return from the system picker to complete importing.
5. Tap an item to view it, export a copy, or permanently delete the vault copy. Export also requires unlocking after the system picker returns.
6. Tap **Lock**, switch apps, or leave the activity to return to the calculator.

Imports copy media. They do not delete or hide originals in your gallery, cloud backups, or trash. Check that the imported file opens before manually removing originals.

## Storage and privacy

- Media contents use AES-256-GCM with a key held by Android Keystore. Independent 1 MiB authenticated chunks allow large videos without loading the whole video into memory. Chunk position, random file identifier, MIME type, and a terminal record are authenticated.
- Vault files live in app-private, no-backup storage with random filenames. MIME type, ciphertext size, and modification time are not concealed.
- PIN verification uses PBKDF2-HMAC-SHA256, a random 32-byte salt, and 120,000 iterations. Five failed attempts trigger a 30-second cooldown. The PIN gates app access; it is not the encryption key.
- Screenshots and screen recording are blocked using Android's secure-window flag. Android backup is disabled.
- Playback temporarily decrypts the selected item into the app-private cache. These files are removed on lock and next launch. This is not a guarantee against a rooted/compromised device or forensic recovery.
- No PIN recovery. Uninstalling, clearing app data, or losing the device can permanently lose the vault. Export needed files first. Exports are ordinary unencrypted files at the location you choose.
- An interrupted export may leave a partial file at the chosen destination; delete it before retrying. Unfinished picker operations are not restored after process death.
- This is an initial local prototype, not an independently audited security product. Media format support depends on the device. Images are sampled for display; EXIF rotation and animated image playback are not implemented.

## Build

Open this folder in Android Studio with JDK 17 and Android SDK 35, or run Gradle 8.14.3:

```text
./gradlew :app:assembleDebug :app:lintDebug
```

The debug APK is for testing. Store distribution requires your own release signing key and release build configuration.

## Checks

Run `powershell -ExecutionPolicy Bypass -File tests/check.ps1` with `JAVA_HOME` set to JDK 17. The standalone tests cover 12 arithmetic checks and 40 encryption roundtrip/tamper checks, including multi-chunk files, incorrect keys, modified metadata, missing terminal records, and appended data. They test the file format on the JVM, not Android Keystore on a device.

On-device checks should cover PIN setup, wrong-PIN cooldown, multiple photo/video imports, playback, export, deleting a vault copy, background locking, and process restart. A physical phone or emulator is needed for these checks.

Implementation references: [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [system document picker](https://developer.android.com/training/data-storage/shared/documents-files).
