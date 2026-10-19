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

### Why is the APK ~76 MB?

A plain music player would be well under 10 MB. Aura Music is bigger because it carries a
**complete YouTube-to-MP3 engine inside the app**, so no PC or server is needed.
Measured breakdown of `app-arm64-v8a-debug.apk` (75.7 MB):

| Part | Size in APK | What it is |
|---|---|---|
| **FFmpeg** (`libffmpeg.zip.so`) | **33.8 MB** | Audio converter: makes the MP3/M4A and embeds title, artist and cover art |
| **Python 3.12** (`libpython.zip.so`) | **13.4 MB** | Small Python interpreter needed to run yt-dlp |
| **yt-dlp** (`res/raw/ytdlp`) | 3.0 MB | Open-source tool that reads YouTube and finds the audio stream |
| **QuickJS** (`libqjs.so`) | 0.4 MB | Tiny JavaScript engine that solves YouTube's player "puzzles" |
| **App code** (`classes*.dex`) | 23.2 MB | Aura Music + Jetpack Compose, Media3, icons, coroutines. Debug builds are not shrunk |
| Resources, icons, other | ~2 MB | Layout resources, launcher icon, metadata |

**≈ 51 MB (two thirds) is the download engine.**

**Why four APKs?** FFmpeg, Python and QuickJS are native programs compiled separately for
each phone CPU type (ARM 64-bit, ARM 32-bit, x86, x86-64). One "universal" APK would contain
all four copies (~280 MB), so the build makes one APK per CPU type and each phone only gets
what it needs.

**After installing**, the app unpacks Python and FFmpeg into its private storage on first
launch, so storage use in *Settings → Apps → Aura Music* is noticeably larger than the
APK itself (the packed engine plus its unpacked copy). That's normal for apps with an
on-device FFmpeg, such as Seal.

**Could it be smaller?**

- **Release build with code shrinking (R8):** set `isMinifyEnabled = true` in
  `app/build.gradle.kts` (needs keep rules for the yt-dlp library); app code would shrink from
  ~23 MB to a few MB, giving roughly 55 MB.
- **Server-only mode:** removing the on-device engine and always using the companion server
  in `backend/` would bring the APK to ~10–25 MB, but then downloads need a running PC.

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

### How YouTube downloading works (is there a server?)

**No. By default there is no server.** Everything runs on the phone. The phone talks
directly to YouTube, downloads the audio, converts it with FFmpeg, and saves the MP3. Nothing
passes through the developer's or anyone else's computer.

```
 Phone (Aura Music)                                     Internet
┌──────────────────────────────────────────┐
│ 1. You paste / share a link              │
│        ↓                                 │
│ 2-3. yt-dlp (Python) ── asks for info ──────────→  YouTube servers
│        ↓            ←── audio stream ────────────  (youtube.com / googlevideo.com)
│ 4. FFmpeg → MP3 320 kbps + tags + cover  │
│        ↓                                 │
│ 5-6. Saved to Music/AuraMusic → Library  │
└──────────────────────────────────────────┘
```

The engine is the open-source library
[youtubedl-android](https://github.com/JunkFood02/youtubedl-android), the same one the
*Seal* app uses. It bundles Python, yt-dlp, FFmpeg and QuickJS (see the size table above).

**Step by step**

0. **First launch (once):** the app unpacks Python and FFmpeg from the APK into private
   storage (a few seconds; the Download page shows *Preparing the download engine…*).
1. **Link cleanup:** `youtu.be/…`, Shorts, YouTube Music, mobile links and shared text such
   as `Song title https://youtu.be/…` are turned into one standard
   `https://www.youtube.com/watch?v=<id>` link. Playlist links are rejected.
2. **Get audio (preview):** yt-dlp runs on the phone, contacts YouTube directly, and reads
   the title, channel, length and thumbnail. This is the preview card.
3. **Download:** yt-dlp asks YouTube for the list of streams and picks the **best
   audio-only stream** (usually Opus/WebM, ~130–160 kbps) into a temporary cache folder.
   The progress percentage comes from yt-dlp.
4. **Convert:** FFmpeg encodes **MP3 at your chosen bitrate (320 kbps default)** or M4A,
   writes **title / artist / year** tags, and embeds the video thumbnail, **cropped
   square**, as album cover. If cover embedding fails, it retries once without it.
5. **Save:** the file is copied to **`Music/AuraMusic/`** through Android's official
   MediaStore API, and temporary files are deleted.
6. **Library update:** Aura notices the new file in MediaStore and refreshes the library
   automatically; the song is also added to **History**.

During all of this a **foreground service** shows a progress notification, so Android
doesn't stop the download when you switch apps. You can cancel anytime.

**Other internet connections the app makes**

| To | When | Why |
|---|---|---|
| YouTube | Only when you fetch/download a link | Read info + download audio |
| GitHub (yt-dlp releases) | Every 3 days, or *Update download engine* | YouTube changes often; a fresh yt-dlp keeps downloads working |
| LRCLIB (lrclib.net) | Only when you tap *Find lyrics online* | Lyrics |

No analytics, no accounts, no data sent to the developer.

**Optional companion server (off by default).** With *Settings → Download engine →
Companion server*, the phone sends the link to **your own PC** running `backend/server.py`,
which does the yt-dlp + FFmpeg work and streams the MP3 back:

```
Phone ──link──→ your PC (backend: yt-dlp + FFmpeg) ──→ YouTube
Phone ←──MP3──  your PC
```

**Built-in protections against YouTube changes**

- **SABR streaming:** YouTube's normal web player often offers no direct audio link
  (*"Requested format is not available"*). The app asks through several YouTube player
  types (`default`, `visionos`, `android_vr`, then `tv_simply`, `mweb`) that still provide
  direct links, and automatically retries with the second set if the first fails.
- **Safe updates:** yt-dlp updates are locked so they never replace the file while a
  download is using it.
- **Clear errors:** technical yt-dlp errors are turned into plain messages, with an
  **Update engine & retry** button when that's the likely fix.

**Limits**

- Only what a logged-out visitor can watch: no private, members-only or age-restricted videos.
- No DRM bypass (Premium-only tracks, movies, etc. won't work).
- YouTube's source audio is ~130–160 kbps. A 320 kbps MP3 plays everywhere but can't add
  quality that wasn't there. **M4A** stays closer to the original with a smaller file.
- Many downloads in a short time from one network can trigger YouTube's bot check or
  *"Too many requests"*. Wait a few minutes.

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
