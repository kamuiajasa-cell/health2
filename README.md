# RunSense Native Android

RunSense Android is being rebuilt as a **native Kotlin + Jetpack Compose application**.

## Current migration state

- Native Android Activity and Compose UI; no WebView runtime.
- Native bottom navigation: Beranda, Latihan, Progress, Coach, and Setelan.
- Dark RunSense visual foundation and responsive native cards.
- The former bundled `runsense.html` has been removed from the Android app.
- This is the first native UI migration milestone. Training calculations, persistent workout storage, adaptive Coach, charts, GPS recording, and Huawei Core / watch delivery still need to be ported and validated as native Android modules; they are not represented as completed here.

## Build

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.
