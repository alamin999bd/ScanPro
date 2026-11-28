# Gemini API Key Security — Document Scanner

## Implemented foundation
- A device-local `GeminiCredentialVault` stores no API key in source code, BuildConfig, resources, or APK constants.
- API key bytes are encrypted with AES-256-GCM using a non-exportable Android Keystore key.
- A six-digit PIN is required by the vault before saving, reading, changing the PIN, or deleting the key.
- PIN verification uses a random 128-bit salt and PBKDF2-HMAC-SHA256 (310,000 iterations).
- Settings contains a PIN setup flow, masked API-key entry, update, PIN change, and key removal controls.
- The API key is never rendered back into the UI.

## Important security boundaries
- Sharing the APK alone does not share this phone's app-private preferences or Android Keystore key.
- This protects the saved key at rest on the device. It is not a guarantee against a rooted/compromised phone, runtime instrumentation, a modified app, or someone who knows the PIN.
- The six-digit PIN is an app access gate, not a substitute for protecting the Google account or rotating a leaked key.
- If the app is uninstalled or its data is cleared, the device-bound key and encrypted credential may be lost. The API key must then be added again.
- Do not put a real API key in GitHub, source code, screenshots, crash reports, or support messages.

## Not yet complete
- Gemini network client and AI features are not wired yet.
- The app still has no INTERNET permission at this stage; add it only together with the reviewed online client.
- Add automated tests for PIN validation, encrypted save/read, wrong PIN, change PIN, and key deletion.
- Verify the Settings UI and vault on an Android emulator and then on the user's Samsung Galaxy M12.
- Do not label the APK as a final release until CI, QA, and signing requirements are satisfied.
