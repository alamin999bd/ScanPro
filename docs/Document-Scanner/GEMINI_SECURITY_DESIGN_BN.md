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

## Gemini PDF assistant integration (implemented in source)
- The app has the Android INTERNET permission for explicitly user-triggered Gemini requests.
- `GeminiApiClient` calls the Gemini Developer API `generateContent` endpoint over HTTPS and sends the key in the `x-goog-api-key` header. The key is not written to logs or source code.
- The selected PDF's selectable/OCR text is extracted locally, capped at 24,000 characters, then sent to Gemini only after the user opens the assistant and starts an action.
- The viewer offers Summary, Bengali translation, Smart Notes, Explain, and Ask PDF actions. The six-digit PIN is required before the saved key is read.
- Image-only PDFs need OCR text before these text-based AI actions can work. The AI feature does not upload the original PDF file, only extracted text.
- The model configured in the client is `gemini-3.5-flash-lite`. Model availability, quotas, pricing and terms are controlled by Google and may change. A free tier is not a guarantee of unlimited or permanently free use.
- The About screen now distinguishes offline core tools from online Gemini features and warns against sending sensitive documents.

## Verification still required
- CI Build #280 passed unit tests, OfficeConverter emulator tests, APK builds, output/signature checks and source ZIP packaging. This is not a real Gemini API call.
- Test with the user's own API key entered privately in the app Settings; never paste the key into chat or GitHub.
- Check the model/key access, network behavior, Bengali output, quota/error messages, and large-document truncation.
- Verify Settings and AI assistant on the user's Samsung Galaxy M12. Physical-device QA is still pending.
- Persistent production signing is not configured; the successful CI release APK uses the temporary debug-key fallback. Do not treat it as a final updateable release.
- The six-digit PIN and Android Keystore protect the key at rest, but cannot guarantee protection on a rooted/compromised phone or against runtime instrumentation.
