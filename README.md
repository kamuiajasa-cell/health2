# Health2

Health2 adalah aplikasi Android pendamping Huawei Watch dengan dashboard lokal.

## Arsitektur

- `index.html`: dashboard Health2; dimuat dari assets APK, tidak bergantung pada hosting web.
- `app/`: host Android Kotlin, WebViewAssetLoader, jembatan JavaScript, dan layar pairing Bluetooth.
- `vendor/RunNowNative`: submodule sumber native JoyoFit/Gadgetbridge yang dipin pada commit `5c44ad08508934160a5a70b123a266eb03214e93`; Gradle memakai modul `huawei/` dari sana.

Repo `RunNowNative` tetap menjadi proyek sumber terpisah. Health2 tidak mengubahnya. Untuk memperbarui core, ubah pin submodule secara sengaja setelah audit kompatibilitas dan lisensi.

## Build APK

GitHub Actions workflow **Build Health2 APK** menjalankan:

```sh
git submodule update --init --recursive
gradle :app:assembleDebug --no-daemon
```

Artifact debug bernama `Health2-debug-apk`.

## Sinkronisasi dan batasan

Aplikasi meminta data historis dari jam melalui core Bluetooth Huawei. Dashboard tidak merekam lari memakai GPS ponsel dan tidak menyalakan sensor detak jantung live terus-menerus. Data jam dikirim ke halaman melalui `window.nativeImport`; browser biasa tanpa host Android tidak dapat membaca Bluetooth.

Build berhasil hanya membuktikan kompilasi. Pairing Watch Fit 2, izin Bluetooth Android, sinkronisasi wellness/workout, dan pengulangan sinkronisasi perlu diuji pada jam/ponsel fisik sebelum APK dianggap siap dipakai sehari-hari.

## Lisensi pihak ketiga

Modul Huawei/JoyoFit membawa lisensi AGPL-3.0-or-later dan pemberitahuan asal-usul di `vendor/RunNowNative/huawei/LICENSE` dan `NOTICE`. Perubahan atau distribusi aplikasi gabungan harus mematuhi ketentuan lisensi tersebut. Health2 tidak berafiliasi dengan atau didukung oleh Huawei.
