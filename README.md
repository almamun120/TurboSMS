# Turbo SMS

## নতুন/ঠিক করা
- **ওয়েব প্যানেলে PIN লগইন** — আগে LAN-এর যে কেউ আপনার ফোন দিয়ে SMS পাঠাতে পারত। অ্যাপে ৬ অঙ্কের PIN দেখায় (কপি / নতুন PIN)। ৫ ভুলে ১ মিনিট ব্লক।
- **Excel নম্বর বাগ**: সেল সংখ্যা হলে `1.7E9` / `…​.0` হয়ে যেত, ফাঁকা সেলে কলাম সরে যেত। নিজস্ব `.xlsx` পার্সার (POI-ooxml Android-এ ক্র্যাশ করে), `.xls` HSSF-এ। CSV/TSV অটো-ডিটেক্ট।
- নম্বর পরিষ্কার (স্পেস/ড্যাশ/+৮৮০) ও যাচাই; অবৈধ নম্বর ব্যর্থ হিসেবে গণনা।
- ক্র্যাশ ফিক্স: `startForegroundService`-এর পর `startForeground` না ডাকলে সার্ভার ফেইল হলে অ্যাপ ক্র্যাশ করত।
- `stop()`-এর বদলে `shutdown()` (রিসিভার/ফাইল cleanup), wakelock শুধু সার্ভার চালু থাকলে।
- বহু-পার্ট SMS-এর সেন্ট/ফেইল গণনা সব পার্ট মিলিয়ে; PendingIntent request-code সংঘর্ষ (ভুল স্ট্যাটাস) ঠিক; ডাবল-স্টার্ট প্রতিরোধ।
- বুটে অটো-স্টার্ট আগে কখনো কাজ করত না (`wasRunning` কেউ লিখত না) — ঠিক।
- অপ্রয়োজনীয় `READ_SMS`/`RECEIVE_SMS` পারমিশন বাদ; নোটিফিকেশন পারমিশন ঐচ্ছিক।
- Kotlin প্লাগিন ভার্সন অমিল (1.9.0 vs 2.0.21) ঠিক; `LocalBroadcastManager` ডিপেন্ডেন্সি স্পষ্ট।
- **নতুন UI**: গ্র্যাডিয়েন্ট হেডার, স্ট্যাটাস পিল, কার্ড-ভিত্তিক সার্ভার/PIN/স্ট্যাটস/ডিভাইস, লাইট+ডার্ক; ওয়েব লগইন পেজও আধুনিক।

## GitHub Actions (অটো বিল্ড)

`.github/workflows/android.yml` যা করে:

| কখন | কী হয় |
|---|---|
| `main`/`master`-এ push, Pull Request, বা Actions → *Run workflow* | **Debug APK** + **Signed Release APK** দুটোই বিল্ড হয়, Artifacts-এ পাওয়া যায় |
| `v1.2.0` ধরনের ট্যাগ push | উপরের দুটো APK সহ **GitHub Release** তৈরি হয় |

### নিজের কী দিয়ে সাইন করতে (ঐচ্ছিক, কিন্তু আপডেটের জন্য জরুরি)
```bash
keytool -genkeypair -v -keystore release.jks -alias myalias -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks     # আউটপুটটা KEYSTORE_BASE64 সিক্রেটে বসান
```
Repo → Settings → Secrets and variables → Actions → এই ৪টা যোগ করুন:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

সিক্রেট না দিলে workflow প্রতি রানে একটা অস্থায়ী কী বানিয়ে সাইন করে (APK ইনস্টল হবে, তবে আলাদা কী-র APK-এর ওপর আপডেট হবে না)।

ভার্সন বদলাতে `gradle.properties`-এর `APP_VERSION_NAME` এডিট করুন; `versionCode` = CI run number।

