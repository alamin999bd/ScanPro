# ScanPro – PDF & Document Scanner: প্রজেক্ট ডকুমেন্টেশন

সম্পূর্ণ Offline, Native Android (Kotlin + Jetpack Compose)। কোনো API Key, Server, Login, Subscription, Paid SDK বা INTERNET permission নেই।

## ১. পরিচয়

| বিষয় | মান |
|---|---|
| App Name | ScanPro – PDF & Document Scanner |
| Package / namespace | `com.scanpro.pdfdocscanner` |
| applicationId (release) | `com.scanpro.pdfdocscanner` |
| applicationId (debug) | `com.scanpro.pdfdocscanner.debug` (আলাদা অ্যাপ হিসেবে ইনস্টল হয়) |
| Version Name | `0.4.1` |
| Version Code | `5` |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |

## ২. টুল ও লাইব্রেরি (সব ভার্সন `gradle/libs.versions.toml`-এ)

| টুল | ভার্সন |
|---|---|
| Kotlin | 2.0.21 |
| Gradle (wrapper) | 8.9 |
| Android Gradle Plugin | 8.7.3 |
| KSP | 2.0.21-1.0.28 |
| JDK | 17 বা তার বেশি |

| Dependency | ভার্সন | কাজ |
|---|---|---|
| Compose BOM (Material3, UI, icons-extended) | 2024.10.01 | UI |
| Navigation Compose | 2.8.4 | নেভিগেশন |
| Lifecycle (runtime/viewmodel/compose) | 2.8.7 | ViewModel, state |
| Activity Compose | 1.9.3 | picker, permission |
| Room (runtime, ktx, compiler) | 2.6.1 | ডকুমেন্ট/ফোল্ডার ডেটাবেস |
| DataStore Preferences | 1.1.1 | সেটিংস |
| CameraX (core, camera2, lifecycle, view) | 1.4.0 | scan ক্যামেরা |
| pdfbox-android (`com.tom-roush`) | 2.0.27.0 | PDF তৈরি/মার্জ/পেজ বের করা |
| Coil Compose | 2.7.0 | ছবির thumbnail |
| kotlinx-coroutines-android | 1.9.0 | async |

PDF preview/thumbnail/PDF→ছবি: Android-এর নিজস্ব `PdfRenderer`। ছবির filter/crop/rotate: নিজস্ব Kotlin কোড (`ImageProcessor`), OpenCV বা ML ছাড়া।

## ৩. Permission

- শুধু `CAMERA` (ঐচ্ছিক, `required=false`)।
- Gallery = Android Photo Picker; Import = Document Picker; Save to device = system "Save as" → storage permission লাগে না।
- Share/Open = `FileProvider` (`${applicationId}.fileprovider`)।
- `allowBackup=false`, cloud backup বন্ধ।

## ৪. Project Structure

```
ScanPro/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradle/libs.versions.toml            ← সব ভার্সন এখানে
├── gradle/wrapper/                      ← Gradle 8.9 wrapper
├── gradlew, gradlew.bat
├── .github/workflows/build-apk.yml      ← GitHub-এ APK build
├── PROJECT_DOCS.md, BUILD_APK_GUIDE_BN.md
└── app/
    ├── build.gradle.kts, proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/ (themes, adaptive icon, xml/file_paths, data_extraction_rules)
        └── java/com/scanpro/pdfdocscanner/
            ├── ScanProApp, MainActivity, AppContainer   ← entry + manual DI
            ├── core/    PdfEngine, ImageProcessor, FileStore
            ├── data/    DocumentRepository, SettingsRepository, db/ (Room)
            └── ui/
                ├── ScanProRoot (NavHost), navigation/Routes
                ├── theme/ (Color, Type, Theme: light+dark)
                ├── components/ (TopBar, EmptyState, dialogs, DocumentRow, DocActions)
                ├── home/  scan/  editor/  imagetopdf/  pdftools/
                ├── importdocs/  documents/  folders/  viewer/  settings/
```

Architecture: `Compose UI → ViewModel → Repository → Room / FileStore / PdfEngine / ImageProcessor`। `AppContainer` সব singleton রাখে; ViewModel তৈরি হয় `containerViewModel(container) { … }` দিয়ে।
ফাইল: ডকুমেন্ট `filesDir/documents/`, scratch `cacheDir/temp/`।

## ৫. কোডে লেখা ফিচার (⚠ এখনো কোনোটি ডিভাইসে চালিয়ে পরীক্ষা হয়নি)

Home Dashboard (৮ tile, Recent, Search আইকন) · Camera scan (flash, tap-to-focus, একাধিক পেজ, পেজ ক্রম বদল/মুছা) · Gallery থেকে পেজ যোগ · Page Editor (crop handle + Free/ID/A4/Square, rotate, brightness/contrast) · Filters (Enhance, B&W Document, Grayscale, Vivid) · Image→PDF (একাধিক ছবি) · ID Scan (গাইড ফ্রেম, ID-ratio crop, A4-তে সামনে-পেছন) · PDF Tools (Merge, Extract pages, PDF→JPEG, Compress, Lock/Unlock with password, Text watermark, Sign PDF with a drawn signature; Watermark-এ বাংলাসহ যেকোনো লেখা) · Page Editor-এ "Auto" বাটন (নথির সীমানা খোঁজে) ও "Straighten page" (চার কোণ খুঁজে perspective সোজা করে) · In-app PDF/ছবি viewer (zoom, পেজ কাউন্টার) · Save to device · Import (PDF, ছবি, Word .docx ও Excel .xlsx — শেষ দুটি শুধু লেখা-ভিত্তিক PDF হয়ে ঢোকে) · My Documents (search, sort, view/open-with/share/save/rename/move/delete) · Folders (create/rename/delete) · Settings (Light/Dark/System, page size, quality, storage, temp clear, permission অবস্থা, About)।

## ৬. অসম্পূর্ণ / সীমাবদ্ধ (আগের ধাপে তৈরি হয়নি, তাই দাবি করা হয় না)

- Auto ও Straighten দুটোই রঙের উজ্জ্বলতার পার্থক্য (Otsu threshold) দিয়ে পেজ খোঁজে; কোণ হাতে টেনে ঠিক করার সুবিধা নেই (শুধু Straighten চালু/বন্ধ)। পেজ ও টেবিলের রং কাছাকাছি হলে, বা কোণ ঢাকা থাকলে ব্যর্থ হয় ও বার্তা দেখায়।
- OCR (বাংলা+ইংরেজি, Tesseract, অফলাইন): Documents তালিকার মেনুতে "Read text (OCR)"; পড়া লেখা সংরক্ষিত হয় ও Search-এ ফাইলের নামের পাশাপাশি লেখার ভেতরও খোঁজে। মান মাঝারি: ছাপা পরিষ্কার লেখায় ভালো, হাতের লেখা/ঝাপসা ছবিতে ভুল বেশি; প্রতি পেজে কয়েক সেকেন্ড লাগে; পুরনো ফাইল নিজে নিজে পড়া হয় না (প্রতিটির জন্য একবার চালাতে হয়)। ভাষা-ডেটা (`ben/eng.traineddata`, tessdata_fast) Git-এ নেই: GitHub Actions workflow Build-এর সময় নামিয়ে `app/src/main/assets/tessdata/`-এ রাখে। Android Studio-তে নিজে build করলে ফাইল দুটি ওই ফোল্ডারে হাতে রাখতে হবে (না রাখলে OCR "language data missing" বার্তা দেখায়, বাকি অ্যাপ ঠিক চলে)। ডেটাবেস সংস্করণ ২ (নতুন `document_text` টেবিল, আগের ডেটা অক্ষত)।
- (পুরনো নোট, এখন প্রযোজ্য নয়) আগে OCR বাদ ছিল কারণ: বাংলা OCR-এর জন্য ভাষা-ডেটা ফাইল (traineddata) অ্যাপে বান্ডল করতে হয়, যা এখানে ডাউনলোড করা যায়নি; ML Kit-এর ইংরেজি OCR বান্ডল করলে লাইব্রেরি নিজে INTERNET permission বা টেলিমেট্রি আনে কি না এখানে যাচাই করা যায়নি, তাই আপনার "কোনো INTERNET permission নয়" শর্ত ভাঙার ঝুঁকিতে যোগ করা হয়নি।
- Word/Excel import শুধু লেখা বের করে PDF বানায়: ছবি, রং, ফন্ট, চার্ট, সঠিক লেআউট থাকে না; Excel-এর তারিখ সংখ্যা হিসেবে দেখায়; পুরনো .doc/.xls সমর্থিত নয়। মূল ফাইল রাখা হয় না, শুধু PDF থাকে।
- Signature হলো আঁকা ছবি বসানো (শুধু নিচের বাম/মাঝ/ডান, পেজ নম্বর বেছে); সার্টিফায়েড ডিজিটাল সিগনেচার নয়, ঘোরানো পেজে কাজ করে না।
- Compress পেজকে ছবি বানিয়ে ছোট করে, তাই কপির লেখা select/search করা যায় না; ফল ছোট না হলে সংরক্ষণ হয় না।
- Watermark-এ ইংরেজি লেখা আসল টেক্সট থাকে; বাংলা বা অন্য লিপি ফোনের নিজস্ব ফন্টে আঁকা ছবি হিসেবে বসে (ফোনে সেই ফন্ট না থাকলে বাক্স দেখাতে পারে)।
- Lock করা PDF খুললে Viewer পাসওয়ার্ড চায় (ফাইল locked-ই থাকে; দেখার সময় একটি অস্থায়ী unlocked কপি ক্যাশে থাকে, বন্ধ করলে মুছে যায়)। পাসওয়ার্ড ভুলে গেলে উদ্ধার সম্ভব নয়। এমন ফাইলের তালিকায় "password-protected" লেখা থাকে।
- R8 shrinking বন্ধ (ডিভাইসে পরীক্ষার আগে চালু করা ঝুঁকিপূর্ণ)।

## ৭. যাচাইয়ের অবস্থা (সৎ তালিকা)

| যাচাই | ফল |
|---|---|
| Kotlin ফাইলের সিনট্যাক্স (kotlinc পার্স; নতুন Compress/Lock/Unlock/Watermark/Auto-detect কোডসহ) | ✅ কোনো সিনট্যাক্স error নেই |
| ইম্পোর্ট, ব্র্যাকেট, XML, কল-সাইট মিল (স্ক্রিপ্ট) | ✅ সমস্যা নেই |
| স্বাধীন রিভিউ (API/টাইপ/স্কোপ) | ✅ নিশ্চিত কম্পাইল error পাওয়া যায়নি; ৩টি ঝুঁকি ঠিক করা হয়েছে (`catch (_)`, OOM, FD leak, ক্যামেরা bind) |
| টাইপ-চেক / Gradle build | ❌ করা যায়নি (Android SDK ও Maven/Google সার্ভার ব্লকড) |
| ডিভাইস/এমুলেটরে চালানো, বাটন ও ফিচার পরীক্ষা | ❌ হয়নি |

নতুন ফিচারগুলোও (Auto-detect, Straighten, Compress, Lock, Unlock, Watermark, Sign) লেখা হয়েছে কিন্তু **কম্পাইল ও ডিভাইসে পরীক্ষা হয়নি**।

**APK তৈরি হয়নি।** কারণ: এই পরিবেশে dl.google.com, maven.google.com, repo.maven.apache.org, services.gradle.org সব হোস্ট নেটওয়ার্ক নীতিতে ব্লকড (HTTP 403); Android SDK ইনস্টল নেই।

## ৮. Build নির্দেশনা

বিস্তারিত বাংলা গাইড: `BUILD_APK_GUIDE_BN.md`। সংক্ষেপে:

- **ফোনে:** GitHub Private repo + `.github/workflows/build-apk.yml` + `ScanPro-source.zip` আপলোড → Actions-এ APK।
- **PC-তে:** Android Studio-তে প্রজেক্ট খুলে Sync → Build APK। অথবা `./gradlew assembleDebug assembleRelease`।
- আউটপুট: `app/build/outputs/apk/debug/app-debug.apk`, `app/build/outputs/apk/release/app-release.apk`।
- release যেকোনো আলাদা কী ছাড়া debug-কী দিয়ে সই হয় (ফোনে ইনস্টল চলে, Play Store-এ নয়)। আসল কী: `keytool -genkey -v -keystore scanpro.jks -alias scanpro -keyalg RSA -keysize 2048 -validity 10000`, তারপর env `SCANPRO_KEYSTORE_PATH`, `SCANPRO_KEYSTORE_PASSWORD`, `SCANPRO_KEY_ALIAS`, `SCANPRO_KEY_PASSWORD` সেট করে `assembleRelease`। কী ফাইল ও পাসওয়ার্ড নিরাপদে ব্যাকআপ রাখুন; হারালে আপডেট দেওয়া যাবে না।

## ৯. APK-র অবস্থান

এখনো তৈরি হয়নি। তৈরি হলে এখানে লিখুন: `<পথ>`, সাইজ `<MB>`, Version `0.3.0 (3)`।

## ১০. ভবিষ্যতে আপডেট করার নিয়ম

1. **প্যাকেজ নাম বদলাবেন না** (`com.scanpro.pdfdocscanner`) — বদলালে আলাদা অ্যাপ হয়ে যায়।
2. প্রতিটি নতুন রিলিজে `app/build.gradle.kts`-এ `versionCode` +১ এবং `versionName` বদলান।
3. নতুন লাইব্রেরি: আগে `gradle/libs.versions.toml`-এ ভার্সন, তারপর `app/build.gradle.kts`-এ `implementation(libs.…)`। INTERNET permission বা ক্লাউড SDK যোগ করবেন না।
4. নতুন স্ক্রিন: `ui/<feature>/` ফোল্ডারে Screen + ViewModel, `Routes.kt`-এ route, `ScanProRoot.kt`-এ `composable(...)`। ডেটা সবসময় `DocumentRepository` দিয়ে।
5. **Room ডেটাবেস বদলালে** (`data/db/Entities.kt`): `AppDatabase`-এর `version` বাড়ান এবং `Migration` লিখুন, নইলে ব্যবহারকারীর ডকুমেন্ট তালিকা মুছে/ক্র্যাশ করতে পারে। স্কিমা `app/schemas/`-এ জমা হয়, এটি সংরক্ষণ করুন।
6. ব্যবহারকারীর ফাইল মোছার আগে সবসময় confirm dialog রাখুন।
7. প্রতিটি পরিবর্তনের পর: Sync → Build → ফোনে চালিয়ে scan, import, PDF তৈরি, viewer, rename/move/delete, dark mode পরীক্ষা করুন।
8. পরবর্তী প্রস্তাবিত কাজ: প্রথম আসল build-এর error ঠিক করা → ডিভাইস টেস্ট → auto edge detection (OpenCV) → OCR → PDF compress/lock → আসল signing key → Play Store।

## Problem report (ক্র্যাশের তথ্য)
অ্যাপ হঠাৎ বন্ধ হলে তার বিবরণ ফোনেই `last_crash.txt` ফাইলে লেখা হয় (কোথাও পাঠানো হয় না)। Settings → "Problem report" থেকে দেখা, Copy ও Clear করা যায়। বাগ জানানোর সময় এটি কপি করে ডেভেলপারকে দিন।
