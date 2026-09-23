# Calculator Vault for Android

A native Java Android app with a working calculator and a PIN-protected local vault for photos, videos, and phone numbers, plus a private browser. Android 8.0 or newer. No ads, accounts, analytics, or third-party SDKs. The browser is the only part that uses the network.

## Use

1. Install `app/build/outputs/apk/debug/app-debug.apk` on your Android phone.
2. Create and confirm a 6–12 digit PIN on first launch.
3. Enter that PIN in the calculator and tap **=** to open the vault.
4. Use the tabs along the bottom: **Photos**, **Videos**, **Contacts** and **Browser**.
5. On a media tab, tap **Add photos** or **Add videos** and select media. Enter the PIN again when you return from the system picker to complete importing; the import opens on the tab you started from.
6. Tap an item to view it, export a copy, select it, or permanently delete the vault copy. Export also requires unlocking after the system picker returns.
7. Long press any item to select several, then **Export** them into a folder you choose or **Delete** them together.
8. Open the **Browser** tab for a private browsing session that is erased whenever the vault locks.
9. Tap **Lock**, switch apps, or leave the activity to return to the calculator.

## Calculator

The **fx** key adds brackets, `^`, `√`, `π` and sin/cos/tan/log/ln. Angles are degrees, `−2^2` is `−4`, and a value written next to another multiplies, so `2(3+4)` and `3π` work. The line under the expression previews the result while typing; it stays blank for a bare number so PIN entry shows nothing.

The history button keeps the last 20 finished calculations in app preferences, and tapping one puts its result back on screen. Expressions that are only digits are never recorded, so PIN attempts are not stored. Clear the history from the history sheet or from vault settings.

## Vault

The unlocked vault is a set of tabs along the bottom: **Photos**, **Videos**, **Contacts** and **Browser**. Photos and videos are listed separately, each with its own count, empty state and **Add** button, and the picker on each tab offers only that kind of file. Sort order and grid/list are shared by both media tabs. Leaving selection mode, locking, or backgrounding the app returns to **Photos**.

Photos are listed with a thumbnail, decrypted in memory for display only and dropped when the vault locks; videos keep an icon. **Grid** shows three per row, **list** adds size and date. Items are numbered per type from the newest, and that name stays with the item when the sort order changes (newest, oldest, largest or smallest first). Vault settings also holds **Change PIN**, which asks for the current PIN and rewrites the salted hash without touching the encrypted files.

An import copies first and removes second. The encrypted copy is written before anything is asked of the gallery, so a failed import never costs the original. Once the copy is on disk, the app asks Android to delete the picked items from the gallery. On Android 11 and newer that goes through `MediaStore.createDeleteRequest`, which is the only way an app without the storage permission may delete media it did not create. Whether you see a prompt is Android's call, not the app's: when the picker hands write access over with the file — which stock DocumentsUI does — the delete goes straight through, and otherwise one system prompt covers the whole batch. Declining leaves everything in place. A prompt runs in another app, so the vault locks while it is up and the PIN is asked for again afterwards. Vault settings carries the switch: **Originals leave your gallery** is on by default, and tapping it hands imports back to plain copying.

Removal reaches the gallery on this phone and nothing else. Cloud backups, your gallery's trash or recently-deleted album, and copies you have already shared are untouched — clear those yourself. On Android 10 and older there is no system delete prompt, so the original usually stays and the app says so rather than pretending otherwise.

## Contacts

**Add** saves a name, number and optional note straight into the vault. **Import** opens Android's contact picker; that picker runs in another app, so the vault locks and the PIN is asked for again before the picked number is filled into the editor. Nothing is written to the phone's own contact list, and importing does not remove the original — delete that yourself if you want the number gone from the phone.

The list is sorted by name, with unnamed entries shown by number. The green button on a row calls; tapping the row opens call, copy, edit and delete. The whole list is one encrypted file, rewritten on every change, and the decrypted copy lives in memory only until the vault locks.

Calling opens the phone dialer with the number filled in, which needs no permission. Tapping the calling row switches to **direct calling**: Android asks for `CALL_PHONE` once, the vault locks while it asks, and calls then start without the dialer. Turning it off, or revoking the permission in Android settings, returns to the dialer.

A call from the vault is an ordinary call. It appears in the call log, in the dialer's recents, and on the carrier's bill; the app does not hide it there. Starting a call leaves the app, so the vault locks.

## Browser

The **Browser** tab is a single private browsing session. Type an address or words to search for; anything that is not a hostname is searched on Google, and every other scheme (`javascript:`, `file:`, `data:`, `intent:`) is searched for rather than loaded. Back, forward, reload, home and **Clear browsing now** sit under the address bar, and the system back gesture steps back through the page history before it locks the vault.

Only `https` is loaded. A typed `http://` address and an `http` link are retried over `https`; an `http` redirect is refused instead, so a site that bounces between the two cannot loop. Mixed content is blocked, downloads are refused rather than written outside the vault, and camera, microphone and location requests from a page are denied without asking.

Cookies last as long as the unlocked session so a sign-in works, but third-party cookies are blocked, nothing is cached, and no history or form data is kept. Locking the vault — including simply leaving the app — erases cookies, site storage and the cache, and destroys the browser itself. The session does not survive it. The address bar shows the site rather than the whole address, so a long link cannot push the real host out of view; tap it to see and edit the full address.

Switching to another tab stops the page's scripts and timers, and returning resumes them. The page itself stays loaded until the vault locks.

## Storage and privacy

- Media contents use AES-256-GCM with a key held by Android Keystore. Independent 1 MiB authenticated chunks allow large videos without loading the whole video into memory. Chunk position, random file identifier, MIME type, and a terminal record are authenticated.
- Contacts use the same key and the same authenticated format, in one file whose name does not end in `.vault`, so it is never listed as media. A save writes a temporary file and renames it, so a failed write leaves the stored contacts untouched. Separators typed into a name, number or note are replaced before writing, so no entry can split or forge another.
- Vault files live in app-private, no-backup storage with random filenames. MIME type, ciphertext size, and modification time are not concealed. The number of saved contacts can be estimated from the size of the contacts file.
- PIN verification uses PBKDF2-HMAC-SHA256, a random 32-byte salt, and 120,000 iterations. Five failed attempts trigger a 30-second cooldown. The PIN gates app access; it is not the encryption key.
- Screenshots and screen recording are blocked using Android's secure-window flag. Android backup is disabled.
- Two permissions are declared. `INTERNET` is used only by the browser tab; nothing else in the app opens a connection, and the calculator, vault and contacts work with the device offline. `CALL_PHONE` is optional and only requested if you turn on direct calling; the app works fully without it. Telephony is declared as a non-required feature so the app still installs on tablets. The file and contact pickers need no permission because they run in another app and hand back only the item you picked.
- Browsing is private on this device, not anonymous on the network. The sites you visit, your network, and your provider see the traffic as they would from any browser. The app adds no proxy, VPN, or tracker blocking, and the vault hides nothing at that level.
- The browser loads `https` only. Cookies last for the unlocked session and third-party cookies are refused; the cache, site storage, form data and page history are erased and the browser destroyed on every lock, which includes leaving the app. Downloads are refused so no plaintext file is written outside the vault. If the process is killed without pausing, by a crash or a force stop, WebView files may survive until the next lock clears them.
- Pages render inside the same secure window as the rest of the app, so the screenshot and screen-recording block covers browsing too. JavaScript is on, as a usable browser needs it; camera, microphone and location requests from a page are denied without asking.
- Copying a number puts it on the system clipboard, where other apps can read it until something replaces it. On Android 13 and newer the clip is flagged sensitive so the system does not preview it.
- Playback temporarily decrypts the selected item into the app-private cache. These files are removed on lock and next launch. This is not a guarantee against a rooted/compromised device or forensic recovery.
- List and grid thumbnails decrypt images in memory only, never to disk. The decoded thumbnails are held in a 6 MB cache that is emptied on lock; the intermediate plaintext buffer is zeroed after decoding, but copies the garbage collector still holds are outside the app's control.
- Calculation history is stored unencrypted in app preferences, like any calculator's history. Only expressions containing an operator are saved.
- No PIN recovery. Uninstalling, clearing app data, or losing the device can permanently lose the vault, contacts included. Export needed files first and keep important numbers elsewhere. Exports are ordinary unencrypted files at the location you choose.
- An interrupted export may leave a partial file at the chosen destination; delete it before retrying. Unfinished picker operations are not restored after process death.
- This is an initial local prototype, not an independently audited security product. Media format support depends on the device. Images are sampled for display; EXIF rotation and animated image playback are not implemented.

## Build

Open this folder in Android Studio with JDK 17 and Android SDK 35, or run Gradle 8.14.3:

```text
./gradlew :app:assembleDebug :app:lintDebug
```

The debug APK is for testing. Store distribution requires your own release signing key and release build configuration.

## Checks

Run `powershell -ExecutionPolicy Bypass -File tests/check.ps1` with `JAVA_HOME` set to JDK 17. The standalone tests cover 46 arithmetic checks (precedence, brackets, powers, percentages, degree trigonometry, logarithms, implicit multiplication and rejected input), 40 encryption roundtrip/tamper checks, including multi-chunk files, incorrect keys, modified metadata, missing terminal records, and appended data, 30 contact checks covering the record roundtrip, separators injected into a field, damaged records being skipped, and which characters reach a `tel:` URI, and 31 address-bar checks covering https upgrades, hostname versus search detection, other schemes being searched for rather than loaded, and the host shown for an address carrying embedded credentials. They test the formats on the JVM, not Android Keystore on a device.

On-device checks should cover PIN setup, changing the PIN, wrong-PIN cooldown, multiple photo/video imports, thumbnails in both layouts, multi-select export and delete, playback, single export, background locking, and process restart. For contacts: adding, editing and deleting an entry, importing through the contact picker and re-entering the PIN, calling through the dialer, granting and then revoking `CALL_PHONE`, and confirming entries survive a lock and unlock. For the tabs and browser: switching between all four tabs, importing from each media tab and landing back on it, loading a page and stepping back through history with the system back gesture, confirming a signed-in page is signed out again after a lock, and confirming **Clear browsing now** starts a fresh session. A physical phone or emulator is needed for these checks.

Implementation references: [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [system document picker](https://developer.android.com/training/data-storage/shared/documents-files), [contact picker](https://developer.android.com/training/contacts-provider/retrieve-names).
