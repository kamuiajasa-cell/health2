# RunSense Native Android

RunSense Android is a **native Kotlin + Jetpack Compose application**. It does not load the former web app through WebView; the bundled HTML has been removed.

## Implemented

- Native Compose navigation and RunSense dark visual system: Beranda, Latihan, Progress, Coach, Jam, and Setelan.
- Native local activity, strength, wellness, and athlete-profile entry with persistent local storage.
- The original RunSense Kotlin domain engine is ported into `:domain`: capacity/stage estimation, LTHR/LT pace, readiness, autonomic checks, session classification, daily Coach design, workout steps, stream analysis, and review logic.
- Coach UI consumes `Snapshot.processData()`, including stage, threshold estimates, 5K estimate, and today's recommendation.
- The `:data` module contains the RunSense-compatible data model, JSON codecs, Intervals.icu ingestion, and repository.
- Native Intervals.icu API-key entry and sync adapter; imported activities and wellness are merged into the local history used by Coach and Progress.
- Native Huawei watch pairing / connection / sync screen. After a watch sync, workouts and wellness are imported from the Huawei database into the same local history.
- Huawei transport source is linked from the RunNowNative repository as a Git submodule under `vendor/RunNowNative`; CI checks out submodules.
- Local synthetic tests cover the initial load, cadence normalization, strength exposure, and quality-session guardrails; the original domain golden tests and fixtures are included.

## Still to integrate / validate

- Full original strength progression and exact Coach state inputs (pain check-in, full autonomic baselines, weekly plan, race settings).
- Detailed native stream segmentation, 5-second cadence windows, pace/HR zone analysis, and complete Progress charts.
- GPS/background run recording and native workout delivery to the watch.
- Verify Huawei pairing, full-history sync, and Intervals.icu sync on the user's actual phone and Watch Fit 2. CI compilation is not a substitute for device validation.
- Migrate existing browser-local records; new syncs can restore data from connected Intervals.icu / Huawei sources, but local browser storage is not copied automatically.

The migration is incremental. A feature is only marked complete after its Kotlin path and regression/device checks are in place.

## Build

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.
