# Aura Music converter server

The Android app is offline-first. The optional converter backend is only needed for the **Authorized media import** screen.

It accepts a normal public YouTube URL, uses `yt-dlp` to obtain an audio source that the server is permitted to access, and uses **FFmpeg** to produce an MP3 stream. The Android app then opens Android's save-file picker so the user chooses the exact destination.

## Ubuntu / Debian

```bash
sudo apt update
sudo apt install ffmpeg python3 python3-venv
cd backend
./run_server.sh
```

The API listens on:

```text
http://0.0.0.0:8000
```

For Android Studio emulator use:

```text
http://10.0.2.2:8000
```

For a real phone connected to the same LAN, use the development computer's LAN address, e.g.:

```text
http://192.168.1.20:8000
```

Set that value in **Aura Music → Downloads → Converter server** or **Settings → Converter server**.

## Health check

Open `/health`. A working server should report an `ffmpeg` path and `ok: true`.

## Endpoints

- `GET /health`
- `POST /info` → title, artist and duration metadata
- `POST /convert` → streamed `audio/mpeg`

## Docker

```bash
docker build -t aura-music-converter .
docker run --rm -p 8000:8000 aura-music-converter
```

Do not expose this development sample directly to the public internet without authentication, rate limits, logging, cleanup, and an appropriate access policy.

## Platform / copyright note

Use the converter only for media you own or are authorized to download, and comply with the source platform's terms and applicable law. This project does not bypass DRM, private videos, or authentication barriers.
