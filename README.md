# Aura Music 🎵

**Your Music. Your World.** An offline-first, dark premium neon music player for Android,
with a built-in YouTube → MP3 downloader powered by yt-dlp + FFmpeg.

- Version: **2.0.0** (versionCode 3)
- Android **8.0+** (minSdk 26, targetSdk 37)
- Kotlin · Jetpack Compose · Material 3 · Media3/ExoPlayer

---

## 📲 Get the APK

After building (see below), the APKs are here:

```
app/build/outputs/apk/debug/
```

| File | Install on |
|---|---|
| `app-arm64-v8a-debug.apk` | **Almost all phones (use this one)** |
| `app-armeabi-v7a-debug.apk` | Old 32-bit phones |
| `app-x86_64-debug.apk` | Android Studio emulator |
| `app-x86-debug.apk` | Old 32-bit emulators |

**Install on a phone**

- Copy `app-arm64-v8a-debug.apk` to the phone, tap it, and allow *Install unknown apps*.
- Or, with USB debugging enabled:
  ```bash
  adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
  ```

If Android says *"App not installed"*, try the `armeabi-v7a` APK instead.

> **Why four APKs?** The downloader bundles Python, yt-dlp and FFmpeg as native code for each
> CPU type. Splitting keeps each APK around 70–80 MB instead of one ~300 MB file.

---

## ✨ Features

| Feature | Where to find it |
|---|---|
| 🎵 Local music player: gapless, background playback, notification + lock-screen controls | Home, Library, Now Playing |
| 📁 Storage / folder browser | Library → Folders |
| 📋 Playlists: create, rename, reorder, delete, plus smart playlists (Liked, Most played, Recently played, Recently added) | Library → Playlists |
| 🎚️ Equalizer with device presets + your own saved presets | Now Playing → EQ icon, or Settings |
| 🔊 Bass boost, virtualizer, loudness | Equalizer |
| 🔍 Search songs, artists, albums and playlists, with recent searches | Search tab |
| ❤️ Favorites (Liked Songs) | ♥ on Now Playing, or ⋮ menu on any song |
| 🎤 Synced lyrics: find online (LRCLIB), import `.lrc`, or paste/edit; tap a line to jump | Now Playing → Lyrics bar |
| ⏱️ Sleep timer: 15/30/45/60/90 min, custom, end of song, gentle fade-out | Now Playing → timer icon |
| 📥 YouTube / link → **MP3 or M4A on the phone**, 128–320 kbps, with title, artist and cover art | Downloads tab, or **Share** a link from the YouTube app |
| 🖼️ Album artwork (Android 8 → 16), also in the notification | Everywhere |
| 📊 Recently played, most played, recently added | Home |
| 💾 Backup & restore (playlists, likes, history, EQ, settings) to a JSON file | Settings → Backup & Restore |
| 🌙 Dark neon theme, light theme, or follow system | Settings → Appearance |
| 🚗 Car mode with big buttons + Android Auto browsing and voice search | Home → Car mode, or Settings |
| ▶️ Queue, play next, shuffle, repeat, playback speed, skip silence | Now Playing |
| 📂 "Open with Aura Music" for audio files | Any file manager |

**Song menu (⋮):** play next · add to queue · add to playlist · like · go to album/artist · share · song info.

---

## 📥 Downloading music

1. Open the **Downloads** tab, or tap **Share → Aura Music** in the YouTube app.
2. Paste the link (📋 button) and tap **Get audio**.
3. Check the preview, then pick **Format** (MP3/M4A), **Quality** and **Save to** folder.
4. Tap **Download**. Progress shows in the app and in the notification.

Files are saved to **`Music/AuraMusic/`** by default (changeable) and appear in your library
right away. Past downloads are listed under the **History** tab.

**Tabs:** **YouTube** (youtube.com, youtu.be, Shorts, YouTube Music) · **URL** (other sites
supported by yt-dlp) · **History**.

### If a download fails

- **"YouTube changed something" / "bot check"**: tap **Update engine & retry**, or go to
  **Settings → Update download engine**. yt-dlp also updates itself every 3 days.
- **"Too many requests"**: YouTube is rate-limiting your network for a while. Wait a few
  minutes and retry.
- **Playlists** aren't supported; open a single video and share/copy its link.
- **Private or age-restricted** videos can't be downloaded (no sign-in).

### Optional: convert on a PC instead

Switch **Settings → Download engine → Companion server** and run the server in `backend/`:

```bash
sudo apt install ffmpeg python3 python3-venv
cd backend && ./run_server.sh        # listens on http://0.0.0.0:8000
```

In the app, set **Settings → Server URL** to `http://10.0.2.2:8000` (emulator) or
`http://<your-PC-LAN-IP>:8000` (phone on the same Wi-Fi), then tap **Test server connection**.
See `backend/README.md` for Docker and API details.

---

## 🛠️ Build from source

**Requirements:** Android Studio (recent), JDK 17+ (Android Studio's bundled JBR works),
Android SDK Platform 37, internet access for the first Gradle sync.

**Android Studio:** *File → Open* → select the `AuraMusic` folder → wait for Gradle sync →
press **Run ▶**. Studio installs the correct APK for your device automatically.

**Command line:**

```bash
export JAVA_HOME=/path/to/android-studio/jbr   # if java isn't on your PATH
./gradlew assembleDebug        # debug APKs
./gradlew assembleRelease      # release APKs (signed with the debug key; see below)
./gradlew lintDebug            # static checks
```

Outputs go to `app/build/outputs/apk/debug/` (or `release/`).

> **Before publishing:** release builds are signed with the debug key so they install out of
> the box. Create your own keystore and replace `signingConfig` in `app/build.gradle.kts`.

---

## 🔐 Permissions

| Permission | Why |
|---|---|
| Music and audio (`READ_MEDIA_AUDIO`, or storage on Android ≤ 12) | Find songs on the device |
| Notifications | Playback controls and download progress |
| Internet | Downloads, online lyrics, engine updates |
| Storage write (Android 8–9 only) | Save downloaded songs |
| Foreground service | Keep music and downloads running in the background |

If you denied audio access, tap **Allow access** on Home, or go to
**Settings → App permissions**.

---

## 🗂️ Project structure

```
app/src/main/java/com/auramusic/app/
├── AuraApp.kt, MainActivity.kt   App start, root UI, navigation, intents
├── data/                         Library scan, preferences, artwork, lyrics
├── player/                       PlayerManager, equalizer/effects, sleep timer, media tree
├── service/PlaybackService.kt    Media3 MediaLibraryService (notification, Android Auto)
├── download/                     yt-dlp/FFmpeg engine, server engine, queue, saving
└── ui/                           Theme, shared components, screens
backend/                          Optional Python (FastAPI + yt-dlp + FFmpeg) converter
```

---

## ⚖️ Legal

Download only content you own or are authorized to download. Aura Music does not bypass DRM
and cannot access private or sign-in-only media. You are responsible for complying with the
source platform's terms and copyright law in your country.

---

## 🎨 Design reference

`design-reference.png` is the UI reference image used for this build.
