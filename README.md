# Hozin Cloth Sim

Simulasi kain jatuh ke bola emas — seperti video referensi MORPHIK 3D
(kain melapis bola, angka FACE naik 1 → 65k).

- **Native murni**: Kotlin + OpenGL ES 3.0 + C++ (NDK). Tanpa WebView,
  tanpa HTML/JS/CSS, full offline, tanpa iklan, tanpa permission.
- **Simulasi**: verlet cloth (position-based dynamics) di C++ — gravitasi,
  tabrakan bola + lantai. Tanpa self-collision (sengaja: terlalu berat
  untuk real-time di HP).
- **Face slider**: 1, 4, 16, 64, 256, 1024, 4096, 65536 face (= grid 1²–256²).
  Iterasi constraint adaptif per tingkat; 65536 face dilabeli "Ultra — berat".
- **UI**: Play/Pause, Reset, slider Face, slider Angin, warna kain + bola,
  orbit kamera (drag), pinch zoom, FPS asli di layar.
- **Target**: arm64-v8a saja, minSdk 26.

## Build

APK hanya dibangun di GitHub Actions (workflow `build-apk.yml` bikin tag +
rilis sendiri saat push ke `main`). Keystore debug persisten dipulihkan dari
secret `DEBUG_KEYSTORE_BASE64` (file `debug.keystore` di root, di-.gitignore,
jangan pernah di-push).
