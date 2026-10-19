from __future__ import annotations

import re
import shutil
import tempfile
import uuid
from pathlib import Path
from urllib.parse import quote, urlparse

import yt_dlp
from yt_dlp.version import __version__ as YTDLP_VERSION
from fastapi import FastAPI, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, HttpUrl

APP = FastAPI(title="Aura Music Converter", version="2.0.0")
DOWNLOAD_ROOT = Path(tempfile.gettempdir()) / "aura_music_downloads"
DOWNLOAD_ROOT.mkdir(parents=True, exist_ok=True)

ALLOWED_HOSTS = {"youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "music.youtube.com"}
MAX_DURATION_SECONDS = 6 * 60 * 60


class UrlRequest(BaseModel):
    url: HttpUrl
    format: str = "mp3"
    quality: str = "320"


def quality_arg(q: str) -> str:
    digits = "".join(ch for ch in (q or "") if ch.isdigit())
    return digits if digits in {"128", "192", "256", "320"} else "320"


def validate_youtube(url: str) -> None:
    parsed = urlparse(url)
    host = (parsed.hostname or "").lower().rstrip(".")
    if parsed.scheme not in {"http", "https"} or host not in ALLOWED_HOSTS:
        raise HTTPException(400, "Only normal public YouTube URLs are accepted by this server.")


def safe_name(value: str) -> str:
    value = re.sub(r"[\\/:*?\"<>|\x00-\x1f]+", "_", value).strip(" .")
    return (value or "aura-download")[:120]


def content_disposition(filename: str) -> str:
    ascii_name = filename.encode("ascii", "ignore").decode().replace('"', "") or "aura-download"
    return f"attachment; filename=\"{ascii_name}\"; filename*=UTF-8''{quote(filename)}"


def ydl_info(url: str):
    validate_youtube(url)
    opts = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "skip_download": True,
        "extract_flat": False,
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        data = ydl.extract_info(url, download=False)
    duration = int(data.get("duration") or 0)
    if duration > MAX_DURATION_SECONDS:
        raise HTTPException(413, "This media item is longer than the server's 6-hour limit.")
    return data


def ytdlp_error(exc: Exception) -> str:
    text = str(exc).strip().replace("\n", " ")
    if "Sign in to confirm" in text:
        return "YouTube requires an authenticated session for this item. Use a public video you are authorized to download."
    if "Video unavailable" in text:
        return "The video is unavailable, private, removed, or blocked in this region."
    if "Unsupported URL" in text:
        return "The URL is not a supported YouTube video URL."
    return text[:500] or "The media source could not be read."


@APP.get("/health")
def health():
    ffmpeg = shutil.which("ffmpeg")
    return {
        "ok": bool(ffmpeg),
        "ffmpeg": ffmpeg or "missing",
        "yt_dlp": YTDLP_VERSION,
    }


@APP.post("/info")
def info(req: UrlRequest):
    try:
        data = ydl_info(str(req.url))
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(502, f"Unable to read source: {ytdlp_error(exc)}")

    return {
        "id": data.get("id"),
        "title": data.get("title") or "Unknown title",
        "artist": data.get("artist") or data.get("uploader") or data.get("channel") or "Unknown artist",
        "duration": int(data.get("duration") or 0),
        "thumbnail": data.get("thumbnail"),
        "webpage_url": data.get("webpage_url") or str(req.url),
    }


@APP.post("/convert")
def convert(req: UrlRequest):
    validate_youtube(str(req.url))
    fmt = req.format.lower()
    if fmt not in {"mp3", "m4a"}:
        raise HTTPException(400, "Supported formats: mp3, m4a.")
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise HTTPException(500, "FFmpeg is not installed or is not available in PATH.")

    job_id = uuid.uuid4().hex
    job_dir = DOWNLOAD_ROOT / job_id
    job_dir.mkdir(parents=True, exist_ok=True)

    # Use the immutable YouTube ID as the temporary filename so titles containing
    # filesystem separators never create unexpected directories.
    outtmpl = str(job_dir / "%(id)s.%(ext)s")
    ydl_opts = {
        "format": "bestaudio[ext=m4a]/bestaudio/best" if fmt == "m4a" else "bestaudio/best",
        "outtmpl": outtmpl,
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "retries": 3,
        "fragment_retries": 3,
        "writethumbnail": True,
        "postprocessors": [
            {
                "key": "FFmpegExtractAudio",
                "preferredcodec": fmt,
                "preferredquality": quality_arg(req.quality),
            },
            {"key": "FFmpegThumbnailsConvertor", "format": "jpg", "when": "before_dl"},
            {"key": "FFmpegMetadata", "add_metadata": True},
            {"key": "EmbedThumbnail", "already_have_thumbnail": False},
        ],
        "ffmpeg_location": ffmpeg,
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            data = ydl.extract_info(str(req.url), download=True)
            source_path = Path(ydl.prepare_filename(data))
            mp3_path = source_path.with_suffix("." + fmt)

        if not mp3_path.exists():
            matches = list(job_dir.glob("*." + fmt))
            if not matches:
                raise RuntimeError(f"FFmpeg finished but no {fmt.upper()} file was produced.")
            mp3_path = matches[0]

        filename = safe_name(data.get("title") or "aura-download") + "." + fmt

        def stream_file():
            try:
                with mp3_path.open("rb") as handle:
                    while chunk := handle.read(1024 * 1024):
                        yield chunk
            finally:
                shutil.rmtree(job_dir, ignore_errors=True)

        return StreamingResponse(
            stream_file(),
            media_type="audio/mp4" if fmt == "m4a" else "audio/mpeg",
            headers={
                "Content-Length": str(mp3_path.stat().st_size),
                "Content-Disposition": content_disposition(filename),
                "Cache-Control": "no-store",
            },
        )
    except HTTPException:
        shutil.rmtree(job_dir, ignore_errors=True)
        raise
    except Exception as exc:
        shutil.rmtree(job_dir, ignore_errors=True)
        raise HTTPException(502, f"Conversion failed: {ytdlp_error(exc)}")
