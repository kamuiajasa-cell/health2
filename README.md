# RunSense Android

Aplikasi Android Kotlin yang membungkus aplikasi RunSense asli sebagai aset lokal WebView agar antarmuka, navigasi, perhitungan coach, dan alur data tetap konsisten dengan versi web.

## Build APK
GitHub Actions otomatis membangun APK debug pada setiap push ke `main`. Hasil tersedia sebagai artifact **RunSense-debug-apk**.

## Catatan
- UI dan logika aplikasi bersumber dari `kamuiajasa-cell/runsense`.
- HTML dibundel ke APK; aplikasi tidak perlu membuka situs untuk menampilkan antarmuka.
- Izin lokasi diminta ketika fitur yang menggunakan geolokasi dipakai.
