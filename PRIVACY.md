# Privacy Policy for Calculator Vault: Hide Photo

Last updated: 29 September 2026

Calculator Vault ("the app") is an Android app that works as a calculator, keeps a PIN-protected vault for photos, videos, phone numbers, and notes on your device, and includes a private browser. This policy explains how the app handles your information.

## Data the developer collects

**None.** The app does not collect, transmit, sell, or share any personal data.

- The app sends nothing to the developer. Its only network use is the browser tab, which connects to the sites you choose to visit and to nothing else.
- There are no accounts, sign-ins, ads, analytics, crash reporting, or third-party SDKs.
- The developer cannot see your PIN, your photos, your videos, your contacts, your notes, the pages you visit, or how you use the app.

## Data the app stores on your device

Everything the app stores stays in the app's private storage on your phone:

- **Photos and videos you import.** They are copied into the vault and encrypted with AES-256-GCM. The encryption key is held by the Android Keystore on your device.
- **Contacts you save in the vault.** The name, number, and note are encrypted with the same key, in the same format, in a single file inside the vault. They are not written to your phone's contact list and are not visible to any other app.
- **Notes you write in the vault.** The title and the text are encrypted with the same key, in the same format, in a single file inside the vault. They are not written to your phone's own notes app and are not visible to any other app.
- **Your PIN.** The PIN itself is not stored. Only a PBKDF2-HMAC-SHA256 hash and a random salt are saved, which are used to check the PIN you enter.
- **Temporary files.** When you view or play an item, the app decrypts it into its private cache. These files are deleted when the vault locks and on next launch.
- **Nothing from the browser.** No browsing history, bookmarks, passwords, or downloads are saved. Cookies and site storage last only while the vault is unlocked and are erased, together with the browser cache, every time it locks.

Importing makes a copy first. Once the encrypted copy is written, the app asks Android to remove the original photo or video from your gallery, so the item is not in both places. Android decides whether to confirm that deletion with you: sometimes it shows its own dialog and removes nothing unless you agree, and sometimes, when the file picker already handed the app access to the file you chose, it removes it without asking again. Either way the app only ever names the items you picked for that import. You can switch this off in vault settings, in which case originals stay where they are until you delete them yourself. Imported contacts are always left alone.

Removing an original takes it out of your gallery on this phone. It does not reach copies held elsewhere — a cloud backup such as Google Photos, your gallery app's own trash or recently-deleted album, a memory card copied to a computer, or anything you have already shared. Clear those yourself if you need the item gone from them too.

## Permissions

- **No permission is needed to use the vault.** To add media the app uses Android's system file picker, and to import a contact it uses Android's system contact picker. Both give the app access only to the single item you pick, and neither requires the storage or contacts permission. Removing an original from the gallery also needs no storage permission: on Android 11 and newer the app asks Android to delete the items it just imported, and Android puts that request to you in its own dialog.
- **CALL_PHONE is optional.** By default, calling a vault contact opens your phone's dialer with the number filled in, which needs no permission. If you turn on direct calling in vault settings, Android asks for the phone permission once so the call can start without leaving the app. Declining keeps the dialer behaviour, and you can turn it off again at any time.
- **INTERNET is used only by the browser tab.** The calculator, the vault, your contacts, and your notes work with the device offline. No part of the app contacts the developer or any analytics, advertising, or crash-reporting service.

## Calls

A call placed from the vault is an ordinary phone call. It appears in your phone's call log, in your dialer's recent calls, and on your carrier's records and bill. The app does not and cannot hide it there, and it never places a call on its own.

## Browsing

The browser tab is private on your device, not anonymous on the network. The websites you open, your network operator, and your internet provider see your traffic exactly as they would from any other browser. The app adds no VPN, proxy, or tracker blocking, and makes no claim to hide who you are from the sites you visit.

On your device, nothing is kept. Pages load over `https` only, downloads are refused, and requests from a page for your camera, microphone, or location are denied without asking you. Cookies work while the vault is unlocked so that you can sign in to a site, but third-party cookies are blocked. Cookies, site storage, and the browser cache are erased, and the browser itself is destroyed, every time the vault locks — which happens whenever you leave the app. Screenshots and screen recording are blocked while browsing, as they are everywhere else in the app.

## Data sharing and deletion

The app shares no data with anyone. You can delete any item, contact, or note from within the vault, and uninstalling the app or clearing its data removes the vault and all its contents permanently.

There is no PIN recovery and no backup. If you forget your PIN, uninstall the app, or clear its data, the vault cannot be recovered. Export important files and keep a copy of important numbers and notes elsewhere.

## Children

The app is not directed at children and collects no data from anyone.

## Security

Media, contacts, and notes are encrypted on the device, screenshots and screen recording are blocked inside the app, and Android backup is disabled. No security measure is absolute; a rooted, compromised, or forensically examined device may put local data at risk.

## Changes

If this policy changes, the updated version will be posted at this address with a new date.

## Contact

Questions about this policy: fensikotadiya@gmail.com
