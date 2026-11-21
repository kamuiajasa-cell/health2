# RunSense Native Android

RunSense Android is a **native Kotlin + Jetpack Compose application**. It does not load the former web app through WebView; the bundled HTML has been removed.

## Implemented

- Native Compose navigation and RunSense dark visual system.
- Native local activity, strength, wellness, and athlete-profile entry.
- Persistent local records in SharedPreferences.
- The original RunSense Kotlin domain engine has been ported into the `:domain` module from the native RunNowNative codebase. This includes capacity/stage estimation, LTHR/LT pace, readiness, autonomic checks, session classification, daily Coach design, workout steps, stream analysis, and review logic.
- The Coach screen now consumes the original Kotlin `Snapshot.processData()` output, including stage, threshold estimates, 5K estimate, and today's recommendation.
- A separate native data module (`:data`) is included for RunSense-compatible JSON, Intervals.icu ingestion, watch-data codecs, repository, and climate handling.
- Synthetic tests cover the initial native activity-load, cadence-normalization, strength-exposure, and quality-session guardrails.

## Still to integrate / validate

- Wire the `:data` repository to Android storage, HTTP, location, and Huawei Watch source implementations.
- Import existing Intervals.icu and Huawei history into the native records without losing IDs, streams, or wellness history.
- Full strength progression/menu engine and the exact original Coach state inputs (pain check-in, autonomic baselines, weekly plan, race settings).
- GPS/background recording, native workout delivery to Huawei, and device-level validation.
- Full native stream segmentation and detailed session UI.

The migration is incremental. Features are only marked complete after the Kotlin path and regression/device checks are in place.

## Build

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.
