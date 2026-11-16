# Hozin Cloth Sim

Simulasi kain jatuh ke bola emas — seperti video referensi MORPHIK 3D
(kain melapis bola, angka FACE naik 1 → 65k).

- **Native murni**: Kotlin + OpenGL ES 3.0 + C++ (NDK). Tanpa WebView,
  tanpa HTML/JS/CSS, full offline, tanpa iklan, tanpa permission.
- **Simulasi**: verlet cloth (position-based dynamics) di C++ — gravitasi,
  tabrakan bola + lantai. Tanpa self-collision (sengaja: terlalu berat
  untuk real-time di HP).
- **Face slider**: 1, 4, 9, 16, 36, 64, 100, 256, 400, 900, 1024, 1600, 2500,
  4096, 10000, 16384, 65536, 262144 face (= grid 1²–512²; 18 tingkat).
  Iterasi constraint + gesekan adaptif per tingkat; 65536 dilabeli
  "Ultra — berat", 262144 "Ekstrem — sangat berat".
- **Background gua bawah tanah** (default, tombol ⛏ Gua / 🌑 Gelap):
  dinding batu melengkung, langit-langit + 8 stalaktit + 6 stalagmit,
  lantai tanah + petak batu, cahaya hangat obor + kabut tipis.
  Kamera dibatasi di dalam gua (jarak ≤ 10).
- **Tabrakan bola-kain**: partikel + centroid + titik tengah sisi +
  bidang segitiga, dengan offset 0.01 — bola tidak lagi menembus kain
  di face rendah, dan tidak ada lagi garis coklat (z-fighting) di face
  tinggi. Depth buffer 24-bit eksplisit.
- **UI**: Play/Pause, Reset, slider Face, slider Angin, warna kain + bola,
  orbit kamera (drag), pinch zoom, FPS asli di layar. Tombol warna
  dibagi rata (tidak ada lagi yang terjepit).
- **Target**: arm64-v8a saja, minSdk 26.

## v1.1.0 — perbaikan dari tes POCO v1.0 (60 FPS di Face 1 & 4096)
- Face 1: bola menembus kain (puncak 0.98 unit saat jatuh) -> tabrakan
  segitiga; terukur 0.0000 sesudah perbaikan di semua level.
- Face 4096: garis coklat radial (z-fighting: kain menempel persis di
  permukaan bola) -> offset tabrakan 0.01 + depth 24-bit.
- Emas tampil oranye -> preset (1.00, 0.84, 0.30) + ambient bola 0.55.
- Tombol HITAM terjepit (teks vertikal) -> tombol warna weight=1.
- Grid besar: N=128 regang 3.4x, N=256 melorot total (2 iterasi) ->
  iterasi 4 + gesekan 0.8 di grid besar; N=512 (262144 face) tetap di
  bola, ~133 ms/step di VM (pesimistis; POCO jauh lebih cepat).

## Build

APK hanya dibangun di GitHub Actions (workflow `build-apk.yml` bikin tag +
rilis sendiri saat push ke `main`). Keystore debug persisten dipulihkan dari
secret `DEBUG_KEYSTORE_BASE64` (file `debug.keystore` di root, di-.gitignore,
jangan pernah di-push).
