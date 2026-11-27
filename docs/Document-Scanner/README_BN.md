# Document Scanner — প্রকল্প নথি

এই ফোল্ডারটি Document Scanner Android অ্যাপের আলাদা ও কেন্দ্রীয় প্রকল্প রেকর্ড। Source code, build configuration এবং workflow repository-র মূল স্থানে থাকবে; এই ফোল্ডারে পরিকল্পনা, ফিচার তালিকা, build/test ইতিহাস ও সীমাবদ্ধতা রাখা হবে।

## পরিচিতি
- App: Document Scanner
- Repository: https://github.com/alamin999bd/ScanPro
- Working branch: document-scanner-premium
- Application ID: com.scanpro.pdfdocscanner
- Minimum Android: 8.0 (API 26)
- Compile SDK: 35
- সর্বশেষ পরিচিত version: 0.6.0 (versionCode 8; build পরিবর্তনে যাচাই করতে হবে)

## নথি
- [Feature Matrix](FEATURE_MATRIX_BN.md)
- [Build ও Test Log](BUILD_TEST_LOG_BN.md)
- [Known Issues ও Roadmap](ROADMAP_BN.md)

## বাধ্যতামূলক Build Failure নিয়ম
1. কোনো Build ব্যর্থ হলে পরবর্তী feature/ধাপে যাওয়া যাবে না।
2. প্রথমে ব্যর্থ Build-এর সম্পূর্ণ job log দেখে root cause শনাক্ত করতে হবে।
3. সংশ্লিষ্ট source/configuration error ঠিক করে GitHub-এ commit দিতে হবে।
4. নতুন Build চালিয়ে **সেই সংশোধিত commit-এর Build সফল** হয়েছে কি না যাচাই করতে হবে।
5. Build আবার ব্যর্থ হলে একইভাবে error ঠিক করে পুনরায় Build করতে হবে; সফল না হওয়া পর্যন্ত এই ধাপেই থাকতে হবে।
6. পুরোনো সফল Build দিয়ে নতুন ব্যর্থ commit-কে সফল বলা যাবে না।
7. কেবল সর্বশেষ branch HEAD-এর Build সফল হওয়ার পর পরবর্তী feature/ধাপে যাওয়া যাবে।
8. Build success শুধু compilation/package তৈরির প্রমাণ; feature functionality ও বাস্তব ডিভাইস পরীক্ষা আলাদাভাবে করতে হবে।

## গুরুত্বপূর্ণ নিয়ম
1. বিদ্যমান source audit না করে duplicate feature যোগ করা যাবে না।
2. মূল PDF অপরিবর্তিত রেখে edit করা হলে নতুন copy তৈরি করতে হবে।
3. CI build success-কে feature QA বা বাস্তব ফোনে পরীক্ষার প্রমাণ বলা যাবে না।
4. চূড়ান্ত APK এবং source ZIP প্রস্তুত না হওয়া পর্যন্ত পরীক্ষামূলক APK ব্যবহারকারীর কাছে পাঠানো হবে না।
5. Samsung Galaxy M12 (Android 13) বাস্তব ডিভাইস পরীক্ষা আলাদা করে নথিভুক্ত করতে হবে।


## Source ZIP ও reproducible build
Final workflow-এ `Document-Scanner-Source.zip` tracked source tree-এর exact checked-out commit থেকে তৈরি হয় এবং `unzip -t` দিয়ে integrity যাচাই হয়। OCR-এর `ben.traineddata`, `eng.traineddata` এবং Noto Sans font assets CI workflow build-এর সময় download করে app assets-এ বসায়; এগুলো generated build inputs, Git source archive-এ tracked binary হিসেবে রাখা হয় না। Source ZIP-এ workflow ও build instructions থাকবে, যাতে একই provisioning/build process পুনরায় চালানো যায়।


Final packaging CI trigger: see the latest successful GitHub Actions run recorded in `DOCUMENT_SCANNER_STATUS_BN.md`.
