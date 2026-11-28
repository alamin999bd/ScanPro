# Document Scanner — APK Build & Phone Test Guide

এই গাইডটি বর্তমান GitHub project `alamin999bd/ScanPro` এবং `document-scanner-premium` branch-এর জন্য।

## বর্তমান অবস্থা

সর্বশেষ CI Build #264 সফল: Unit Tests, OfficeConverter Emulator Test (3/3), Debug/Release APK build, signature verification এবং source ZIP integrity check পাস করেছে।

**তবে এখনো Final APK নয়:** Build #264-এর Release APK temporary debug key দিয়ে সই হয়েছে। একই ফোনে পরবর্তী আপডেট নির্ভরযোগ্যভাবে ইনস্টল করার জন্য একবার স্থায়ী release keystore তৈরি ও GitHub Actions secrets-এ সংরক্ষণ করতে হবে। Samsung Galaxy M12-এ physical test-ও এখনো হয়নি।

## স্থায়ী Release Signing — একবারের কাজ

1. একটি স্থায়ী Android release keystore তৈরি করতে হবে (PC-তে Android/Java `keytool` ব্যবহার করে)। উদাহরণ:
   ```bash
   keytool -genkeypair -v -keystore document-scanner-release.jks -alias document-scanner -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Keystore file এবং তার password নিরাপদে offline backup রাখুন। Keystore কখনো GitHub repository-তে commit করবেন না।
3. Keystore ফাইলের Base64 text তৈরি করুন:
   - Linux/macOS: `base64 -w 0 document-scanner-release.jks` (macOS-এ `base64 document-scanner-release.jks | tr -d '\\n'`)
   - Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("document-scanner-release.jks"))`
4. GitHub repository → **Settings → Secrets and variables → Actions → New repository secret**-এ এই চারটি secret যোগ করুন:
   - `SCANPRO_KEYSTORE_BASE64` = Base64 text
   - `SCANPRO_KEYSTORE_PASSWORD` = keystore password
   - `SCANPRO_KEY_ALIAS` = `document-scanner` (বা key তৈরির সময় দেওয়া alias)
   - `SCANPRO_KEY_PASSWORD` = key password
5. Secrets কখনো chat, source code, commit, issue বা screenshot-এ পাঠাবেন না। Keystore ও password হারালে একই signature দিয়ে update দেওয়া সম্ভব হবে না।

> তোমার ফোনে file picker সমস্যা থাকায় keystore তৈরি/encode করার জন্য PC বা বিশ্বস্ত সহায়তাকারীর প্রয়োজন হতে পারে। আমি এখনো তোমাকে কোনো secret দিতে বা শেয়ার করতে বলছি না।

## Signed build চালানো

Secrets যোগ হওয়ার পর GitHub repository-র **Actions** tab → **Build Document Scanner APK** → **Run workflow**:
- Branch: `document-scanner-premium`
- `use_persistent_signing`: **true**

Workflow সব secret আছে কি না যাচাই করবে, release APK-তে স্থায়ী key ব্যবহার করবে এবং debug certificate থেকে আলাদা কি না পরীক্ষা করবে। কোনো secret অনুপস্থিত থাকলে final signing stage ব্যর্থ হবে; debug key-কে final release হিসেবে দেখাবে না।

## Final APK পাওয়া ও Samsung Galaxy M12 পরীক্ষা

আমি আগে CI ফল, artifact, package/version এবং signature যাচাই করব। সব ঠিক হলে তোমাকে final APK-এর link দেব এবং তখনই ফোনে install/test করতে বলব।

ফোনে পরীক্ষার সময়:
1. Install/open এবং app launch.
2. Camera permission, camera preview, focus/flash ও multi-page scan.
3. Gallery import, crop/rotate, auto-detect ও perspective correction.
4. PDF create, view, save, share ও reopen.
5. Bangla + English OCR/searchable PDF.
6. DOCX/XLSX text-to-PDF import.
7. PDF merge/extract/compress/lock/unlock/watermark/sign.
8. Rename, move, folders, delete confirmation, theme/settings.

যে ধাপে সমস্যা হবে, শুধু সেই অংশের screenshot বা Problem report পাঠাবে। তারপর সেই নির্দিষ্ট সমস্যাটি ঠিক করে আবার CI চালানো হবে।

## সতর্কতা

- Build/Emulator test পাস মানেই Samsung M12-এ সব feature বাস্তবে পাস—এমন নয়।
- Debug APK এবং debug-key fallback release APK চূড়ান্ত updateable release নয়।
- Release `applicationId` `com.scanpro.pdfdocscanner` অপরিবর্তিত রাখতে হবে।
