# RunSense Native Android

RunSense Android is a **native Kotlin + Jetpack Compose application**. The app does not load the former web app through WebView; the bundled HTML has been removed.

## Implemented native modules

- Native Compose navigation and dark RunSense UI.
- Local activity logging for run and walk, including duration, distance, average HR, optional external load, and quality-session marker.
- Local strength logging with muscle-group categories, duration, level, and completed / skipped status.
- Local daily wellness logging for resting HR, sleep, and optional HRV.
- SharedPreferences-backed persistence for activity, strength, wellness, and athlete profile.
- Kotlin training engine for activity load, 7-day acute load, 21-day chronic-load average, ACWR, 48-hour strength exposure, cadence stream normalization, and readiness / Coach guidance.
- Synthetic JVM tests for load calculation, cadence normalization, strength exposure, and quality-session lock.

## Not yet migrated / validated

- Automatic Intervals.icu and Huawei Health / Huawei Core synchronization.
- GPS recording and background workout tracking.
- Original RunSense stream segmentation, cadence-by-5-second windows, pace/HR zone analysis, and advanced session analytics.
- Full adaptive training plan generation, forecasts, and the complete original Coach decision tree.
- Data import/export and migration of existing web-app records.

The native build is an incremental port. A feature is not considered complete until its Kotlin implementation and regression/device validation are in place.

## Build

```bash
gradle assembleDebug
gradle testDebugUnitTest
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.
