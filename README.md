# SalahMute

Automatically switches your Android phone to **Vibrate / Silent / Do Not Disturb** during prayer times, custom schedules, or saved silent places — then **restores your previous sound mode** automatically when the window ends.

## Download
Get the latest signed Android APK (~3.3 MB, Android 8.0+) from the **[GitHub Releases page](https://github.com/umar1O1/SalahMute/releases/latest)**.

## Features
- **Automatic Daily Prayer Times**: Works completely **offline** (MWL, ISNA, Egyptian, Umm al-Qura, Karachi, Diyanet Turkey, and Shia Jafari / Ithna Ashari; Sunni Hanafi, Sunni Shafi/Maliki/Hanbali, and Shia Jafari schools) with optional **monthly online timetable sync** cached locally on your phone.
- **Flexible Daily Schedules**: Add multiple custom or prayer-based slots per day, repeat across selected weekdays, copy slots or entire days, archive/restore with swipe gestures, and bulk-manage via multi-select.
- **Silent Places (Optional)**: Silence automatically when arriving at a saved Masjid, office, or place using an interactive OpenStreetMap picker (**Detailed** & **Satellite** views, search, live radius circle) or **Pick with Google Maps** (Share directly to SalahMute or paste any Google Maps link). Runs natively via Android's `LocationManager.addProximityAlert` with zero Google Play Services.
- **Profiles**: Create and switch profiles (Normal, Ramadan, Travel…) with one tap.
- **Smart Restore & Live Countdown**: If you manually change your phone's sound mode while muted, SalahMute respects your choice. Active silence shows a live countdown notification with a 1-tap **End now** button.
- **Local Backup & Restore**: Automatic and on-demand local backups to `Documents/SalahMute` (or any folder), plus automatic detection on reinstall.
- **Share & Import**: Export or import setups as `.salahmute` files, compact text codes, or QR codes. Personal prayer coordinates are never shared, and silent places are opt-in.
- **Customizable Appearance**: 6 themes (Light, Pure Black, Deep Navy, Cool Light, Sepia, Forest), custom accent colors, configurable card action layout, and opt-in tap-card-to-edit.
- **13 Languages (100% Translated + RTL Support)**: English, اردو, العربية, Türkçe, Bahasa Indonesia, Bahasa Melayu, বাংলা, हिन्दी, فارسی, Français, Deutsch, Español, Русский.
- **Privacy-First & Lightweight**: ~3.3 MB minified release APK, no ads, no tracking, no accounts, HTTPS-only (`usesCleartextTraffic="false"`). Internet is used only when browsing map tiles, searching places, resolving shared Google Maps links, or optionally syncing monthly prayer timetables.

## Permissions
| Permission | Why |
|---|---|
| Do Not Disturb access | Required by Android to switch Silent / DND modes and restore sound |
| Alarms & reminders | Required to switch on the exact minute |
| Notifications | Shows active countdown with a 1-tap Unmute button and optional background shield |
| Battery optimization *(recommended)* | Keeps schedules reliable on aggressive OEM battery savers |
| Location *(optional)* | Only requested if you use Silent Places or "My location" |
| Background location *(optional)* | Only needed if you want Silent Places to work while the app is closed |
| Camera *(optional)* | Only used when scanning a shared QR code |

## Architecture
- `Engine` computes active mute intervals from the active profile and merges them with enabled `GeoZone`s (`GeoController`).
- `Scheduler.reconcile()` applies the strictest active ringer mode for *now* and arms a single exact `AlarmManager.setAlarmClock` for the next transition, backed by a lightweight `JobScheduler` boundary/watchdog safety net.
- All untrusted inputs (QR/share codes, backup files, shared Google Maps URLs) are size-bounded, host-allowlisted (HTTPS-only), and sanitized before use.

## Build & Test
Requires JDK 17+ and Android SDK (compileSdk 36, targetSdk 35, minSdk 26).

```bash
./gradlew testDebugUnitTest      # Run unit tests
./gradlew assembleDebug          # Build app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # Build minified release APK (3.3 MB)
```

For a signed release build, create `keystore.properties` (git-ignored) with `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`, then run `./gradlew assembleRelease`.

## Share File Format
```json
{
  "format": "salahmute",
  "version": 1,
  "method": "KARACHI",
  "hanafi": true,
  "activeProfile": 0,
  "profiles": [
    {
      "name": "Normal",
      "slots": [
        { "label": "Zuhr", "startMin": 780, "endMin": 810, "mode": "VIBRATE", "days": [1, 2, 3, 4, 5] }
      ]
    }
  ]
}
```
Days use ISO numbering (`1` = Monday … `7` = Sunday). Share codes are `SALAHMUTE1:` + gzip + base64url of the same JSON.

## Credits
Built with AI co-authoring and engineering assistance from:
- **Claude** (Anthropic)
- **Gemini / Antigravity** (Google DeepMind)

## License
MIT — see [LICENSE](LICENSE).
