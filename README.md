# Contact Ringtone

One ringtone for every saved contact. Unknown numbers keep the phone's SIM 1 / SIM 2 ringtones.

| Caller | Calling | Plays |
|---|---|---|
| Saved contact | SIM 1 or SIM 2 | Contacts ringtone (set in this app) |
| Unknown number | SIM 1 | SIM 1 ringtone (phone settings) |
| Unknown number | SIM 2 | SIM 2 ringtone (phone settings) |

## How it works

The app writes your chosen tone onto each contact (the same field as "Set ringtone" on a contact),
so Android plays it by itself. Nothing runs during calls, so there's no battery cost.
New contacts are picked up by an Android scheduled job that wakes ~30 seconds after your contacts
change, plus a check every 12 hours.

Contacts you gave a ringtone by hand are left alone unless you turn on
"Also replace ringtones I set by hand".

## Install

Every push to `main` builds a signed APK and publishes it under **Releases** as
`ContactRingtone.apk`. Download it on the phone and allow "Install unknown apps" for the
browser/GitHub app when asked. Later builds install over the old one.

## Setup on the phone (Vivo)

1. Open the app → **Choose ringtone** → **Apply to all contacts** → allow Contacts access.
2. **Open sound settings** → set the SIM 1 and SIM 2 ringtones (these play for unknown numbers).
3. For auto-sync of new contacts: Settings → Apps → Contact Ringtone → turn on **Autostart**
   and set battery to **No restrictions**. Without this Vivo may delay the background job;
   you can always reopen the app and tap Apply.

## Notes

- The signing key lives only in the `SIGNING_KEY_B64` Actions secret, never in the repo.
  Keep the same key so every build installs as an update.
- Custom MP3s: put the file in the phone's `Ringtones` folder so it appears in the picker.
