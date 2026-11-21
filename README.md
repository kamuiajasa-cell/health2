# RunSense Native Android

RunSense Android is a **native Kotlin + Jetpack Compose application**. It does not load the former web app through WebView; the bundled HTML has been removed.

## Implemented

- Native Compose navigation and RunSense dark visual system: Beranda, Latihan, Progress, Coach, Jam, and Setelan.
- Native local activity, strength, wellness, and athlete-profile entry with persistent local storage.
- The original RunSense Kotlin domain engine is ported into `:domain`: capacity/stage estimation, LTHR/LT pace, readiness, autonomic checks, session classification, daily Coach design, workout steps, stream analysis, and review logic.
- Coach UI consumes `Snapshot.processData()`, including stage, threshold estimates, 5K estimate, and today's recommendation.
- The native data module (`:data`) includes RunSense-compatible JSON codecs, Intervals.icu ingestion, Huawei watch import, stream summaries, and repository persistence.
- Native Intervals.icu API-key entry and sync adapter. Imported activities and wellness are merged into the local history used by Coach and Progress.
- Native Huawei pairing / connection / sync screen. Synced Huawei workouts and wellness are imported into the same local history; watch stream summaries are passed into the original Kotlin domain engine.
- Progress includes native session review and on-demand Intervals.icu stream retrieval for activities with an Intervals ID.
- Huawei transport source is linked from the RunNowNative repository as a Git submodule under `vendor/RunNowNative`; CI checks out submodules.
- CI runs the original domain golden tests, data/JSON/ingestion golden tests, and app unit tests before packaging the APK.

## Still to integrate / validate

- Full original strength progression and exact Coach state inputs (pain check-in, full autonomic baselines, weekly plan, race settings).
- Detailed 5-second cadence windows and complete pace/HR zone charts in the native UI.
- GPS/background run recording and native workout delivery to the watch.
- Verify Huawei pairing, full-history sync, Intervals.icu sync, and stream review on the actual phone and Watch Fit 2. CI compilation is not a substitute for device validation.
- Migrate existing browser-local records; new syncs can restore data from connected Intervals.icu / Huawei sources, but local browser storage is not copied automatically.

The Huawei protocol module is AGPL-3.0-or-later and derived in part from Gadgetbridge. Its license and attribution notices are retained in `vendor/RunNowNative/huawei/LICENSE` and `vendor/RunNowNative/huawei/NOTICE`.

The migration is incremental. A feature is only marked complete after its Kotlin path and regression/device checks are in place.

## Build

```bash
gradle :domain:test :data:test :app:testDebugUnitTest
gradle :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.
