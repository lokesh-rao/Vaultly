# Vaultly

AI-powered, privacy-first personal vault for passwords and private notes.

## Theme

Monochrome only:

- **Light** — white surfaces, black text, grey accents
- **Dark** — near-black surfaces, white text, grey accents
- **System** — follows the device setting (persisted in Settings → Appearance)

## Voice-assisted notes

On the Notes tab:

1. Tap the **mic** FAB to dictate a new note (requests microphone permission).
2. Or open a note and tap the mic / **Voice assist** to append speech-to-text.
3. Transcript stays in the app; Vaultly does not upload note audio.

## Privacy

- Vault data is encrypted at rest (PBKDF2 + AES-GCM).
- Master password is never stored as plaintext.
- Offline-first for core vault features.
- Firebase (when configured) is used for Crashlytics + App Distribution only — not as a password backend.

## Build

```bash
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## CI/CD

- `.github/workflows/ci.yml` — unit tests + debug APK on PRs/`main`
- `.github/workflows/distribute.yml` — signed release + Firebase App Distribution

Required GitHub secrets for distribution:

- `SIGNING_KEYSTORE_BASE64`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`
- `SIGNING_STORE_PASSWORD`
- `FIREBASE_SERVICE_ACCOUNT`
- `FIREBASE_APP_ID`
