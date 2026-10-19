# Aura Music — Android Studio Project

Aura Music is an offline-first, dark premium neon music player with local library scanning, playlists, favorites, search, album/folder views, lyrics import, sleep timer, equalizer controls, backup/restore, car mode, and an optional online media conversion backend.

## Features (all working)

| Feature | Where |
|---|---|
| 🎵 Local music player (Media3/ExoPlayer, gapless, background + lock-screen controls) | Home, Library, Now Playing |
| 📁 Storage / folder browser | Library → Folders |
| 📋 Playlists (create, rename, reorder, delete) + smart playlists | Library → Playlists |
| 🎚️ Equalizer with device presets + your own presets | Now Playing → EQ, Settings |
| 🔊 Bass boost, virtualizer, loudness | Equalizer |
| 🔍 Search (songs, artists, albums, playlists) + recent searches | Search tab |
| ❤️ Favorites (Liked Songs) | ♥ on any song |
| 🎤 Synced lyrics: find online (LRCLIB), import `.lrc`, or paste/edit; tap a line to seek | Now Playing → Lyrics |
| ⏱️ Sleep timer (presets, custom, end of song, fade-out) | Now Playing → timer |
| 📥 YouTube → MP3/M4A download **on the phone** (yt-dlp + FFmpeg), 128–320 kbps, cover art + tags | Downloads tab, or *Share* a link from YouTube |
| 🖼️ Album artwork (works on Android 8 → 16) | everywhere, notification too |
| 📊 Recently played / most played / recently added | Home |
| 💾 Backup & restore (playlists, likes, history, EQ, settings) | Settings |
| 🌙 Dark neon theme + light theme | Settings → Appearance |
| 🚗 Car mode + full Android Auto browsing & voice search | Settings / Home → Car mode |

## Build

Open the folder in Android Studio (JDK 17+, SDK 37) and press **Run**, or:

```bash
./gradlew assembleDebug     # APKs in app/build/outputs/apk/debug/ (one per CPU type)
```

The app uses per-ABI APK splits because the download engine bundles Python, yt-dlp and
FFmpeg (~70 MB per APK). Android Studio installs the right one automatically; for a phone
install `app-arm64-v8a-debug.apk`.

## Downloads

* Paste a link (or share it from the YouTube app) → **Get audio** → pick format/quality → **Download**.
* Files are saved to `Music/AuraMusic/` (changeable) and appear in the library instantly.
* yt-dlp auto-updates every 3 days; if YouTube changes something, use
  **Settings → Update download engine** (the app also offers this button on errors).
* Optional: switch **Settings → Download engine** to *Companion server* to convert on a PC
  with the Python server in `backend/`.

## Legal / platform note

The converter sample is for media you own or are authorized to download. It is not designed to bypass DRM or access protected/private media. You are responsible for complying with platform terms and copyright law.

## Design reference

`design-reference.png` is the UI reference image supplied for this build.
