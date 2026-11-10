#!/usr/bin/env python3
"""Generate the Play Store graphics (icon, feature graphic) with the GeminiGen API.

How it works (no webhook needed, so it runs fine on a local PC):
  1. POST the prompt to the API -> the API answers with a job "uuid".
  2. Poll  GET /uapi/v1/history/{uuid}  until the job is finished.
  3. Download the result into play-store/graphics/raw/ (every result is kept).
  4. Derive the final files (512 px icon, 1024x500 feature graphic, launcher
     icon layers) from the selected raw image with Pillow.

Rules this script follows:
  * A job whose raw image already exists is NOT generated again (no credits spent).
  * A job that was submitted but not downloaded yet is resumed, not re-submitted.
  * The API key is read from the environment or from .env and is never printed.

Usage:
  python tools/generate_images.py                 # everything that is missing
  python tools/generate_images.py --only icon     # just one job
  python tools/generate_images.py --new icon      # one more variant (old ones are kept)
  python tools/generate_images.py --select icon icon-01-1a2b3c4d.png
  python tools/generate_images.py --process-only  # only re-create the derived files
  python tools/generate_images.py --account       # show plan + remaining credits

Only the Python standard library is needed for the API part; Pillow is needed
for step 4 (pip install -r tools/requirements.txt).
"""

from __future__ import annotations

import argparse
import datetime
import json
import os
import pathlib
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = pathlib.Path(__file__).resolve().parent.parent
JOBS_FILE = ROOT / "tools" / "image_jobs.json"
RAW_DIR = ROOT / "play-store" / "graphics" / "raw"
STATE_FILE = RAW_DIR / "jobs-state.json"

DEFAULT_API_BASE = "https://api.geminigen.ai"
USER_AGENT = "play-asset-script/1.0"

# Tried in this order. The first entry is what we want (GPT Image 2, low, 4K);
# the others are only used when that model is not available for the account.
MODEL_CHAIN = [
    {"name": "gpt-image-2", "path": "/uapi/v1/imagen/gpt-image-2",
     "fields": {"mode": "low", "resolution": "4K"}},
    {"name": "nano-banana-pro", "path": "/uapi/v1/generate_image",
     "fields": {"model": "nano-banana-pro", "resolution": "4K", "output_format": "png"}},
    {"name": "nano-banana-2", "path": "/uapi/v1/generate_image",
     "fields": {"model": "nano-banana-2", "resolution": "4K", "output_format": "png"}},
]
# Error codes that mean "this model is not usable for this account" -> try the next one.
FALLBACK_ERRORS = {"PREMIUM_PLAN_REQUIRED", "FORBIDDEN", "MODEL_NOT_FOUND", "MODEL_NOT_SUPPORTED"}

STATUS_PROCESSING, STATUS_DONE, STATUS_FAILED = 1, 2, 3


class ApiError(Exception):
    def __init__(self, status: int, code: str, message: str):
        super().__init__(f"HTTP {status} {code}: {message}")
        self.status, self.code, self.message = status, code, message


# --------------------------------------------------------------------------- env

def load_env() -> None:
    """Load KEY=VALUE lines from .env (repo root) without overriding real env vars."""
    env_file = ROOT / ".env"
    if not env_file.exists():
        return
    for raw in env_file.read_text(encoding="utf-8-sig").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key, value = key.strip(), value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        os.environ.setdefault(key, value)


def api_base() -> str:
    return os.environ.get("GEMINIGEN_API_BASE", DEFAULT_API_BASE).rstrip("/")


def api_key() -> str:
    key = os.environ.get("GEMINIGEN_API_KEY", "").strip()
    if not key:
        sys.exit("GEMINIGEN_API_KEY is missing. Put it in .env (see .env.example).")
    return key


# -------------------------------------------------------------------------- http

def _multipart(fields: dict) -> tuple[bytes, str]:
    boundary = "----assetscript" + uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        parts.append(
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="{name}"\r\n\r\n'
            f"{value}\r\n"
        )
    parts.append(f"--{boundary}--\r\n")
    return "".join(parts).encode("utf-8"), f"multipart/form-data; boundary={boundary}"


def api_call(method: str, path: str, fields: dict | None = None, timeout: int = 120) -> dict:
    headers = {"x-api-key": api_key(), "Accept": "application/json", "User-Agent": USER_AGENT}
    data = None
    if fields is not None:
        data, content_type = _multipart(fields)
        headers["Content-Type"] = content_type
    request = urllib.request.Request(api_base() + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read().decode("utf-8", "replace")
            status = response.status
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", "replace")
        status = error.code
    try:
        payload = json.loads(body) if body.strip() else {}
    except json.JSONDecodeError:
        payload = {"raw": body[:500]}
    if status >= 400:
        detail = payload.get("detail") if isinstance(payload, dict) else None
        if isinstance(detail, dict):
            code = str(detail.get("error_code", "ERROR"))
            message = str(detail.get("error_message") or detail.get("message") or "")
        else:
            code, message = "ERROR", str(detail if detail is not None else payload)[:500]
        raise ApiError(status, code, message)
    return payload if isinstance(payload, dict) else {"result": payload}


def download(url: str, dest_without_ext: pathlib.Path) -> pathlib.Path:
    """Download a result file. The API key is NOT sent to the file host."""
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=300) as response:
        content_type = (response.headers.get("Content-Type") or "").split(";")[0].strip().lower()
        data = response.read()
    ext = {"image/png": ".png", "image/jpeg": ".jpg", "image/webp": ".webp"}.get(content_type)
    if ext is None:
        if data[:8] == b"\x89PNG\r\n\x1a\n":
            ext = ".png"
        elif data[:3] == b"\xff\xd8\xff":
            ext = ".jpg"
        elif data[:4] == b"RIFF" and data[8:12] == b"WEBP":
            ext = ".webp"
        else:
            ext = pathlib.PurePosixPath(urllib.parse.urlparse(url).path).suffix or ".bin"
    dest = dest_without_ext.with_name(dest_without_ext.name + ext)
    dest.write_bytes(data)
    return dest


# ------------------------------------------------------------------------- state

def load_state() -> dict:
    if STATE_FILE.exists():
        return json.loads(STATE_FILE.read_text(encoding="utf-8"))
    return {}


def save_state(state: dict) -> None:
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    STATE_FILE.write_text(json.dumps(state, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def now() -> str:
    return datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def selected_raw(job_state: dict) -> pathlib.Path | None:
    name = job_state.get("selected")
    if name and (RAW_DIR / name).exists():
        return RAW_DIR / name
    return None


def outputs_exist(job: dict) -> bool:
    """True when every derived file of the job is already on disk."""
    outputs = job.get("outputs", [])
    return bool(outputs) and all((ROOT / out["path"]).exists() for out in outputs)


# ---------------------------------------------------------------------- generate

def submit(job: dict) -> dict:
    """Submit a job to the first model of MODEL_CHAIN that accepts it."""
    last_error: ApiError | None = None
    for model in MODEL_CHAIN:
        fields = {"prompt": job["prompt"], "aspect_ratio": job.get("aspect_ratio", "1:1")}
        fields.update(model["fields"])
        fields.update(job.get("extra_fields", {}).get(model["name"], {}))
        try:
            reply = api_call("POST", model["path"], fields)
        except ApiError as error:
            last_error = error
            if error.code in FALLBACK_ERRORS or error.status in (403, 404):
                print(f"    {model['name']}: not available ({error.code}) -> trying the next model")
                continue
            raise
        job_uuid = reply.get("uuid")
        if not job_uuid:
            raise SystemExit(f"Unexpected API reply (no uuid): {json.dumps(reply)[:400]}")
        credits = reply.get("estimated_credit", reply.get("used_credit"))
        print(f"    submitted to {model['name']}: uuid={job_uuid} estimated credits={credits}")
        return {
            "uuid": job_uuid,
            "model": model["name"],
            "request": {k: v for k, v in fields.items() if k != "prompt"},
            "prompt": job["prompt"],
            "submitted_at": now(),
            "estimated_credit": credits,
            "status": "processing",
        }
    raise SystemExit(f"No image model accepted the request. Last error: {last_error}")


def result_url(history: dict) -> str | None:
    for image in history.get("generated_image") or []:
        for key in ("file_download_url", "image_url"):
            if image.get(key):
                return image[key]
    value = history.get("generate_result")
    return value if isinstance(value, str) and value.startswith("http") else None


def wait_and_download(job_name: str, attempt: dict, index: int, timeout_s: int) -> pathlib.Path | None:
    deadline = time.time() + timeout_s
    last_line = ""
    while True:
        history = api_call("GET", f"/uapi/v1/history/{attempt['uuid']}")
        status = history.get("status")
        line = f"    status={status} {history.get('status_desc')} {history.get('status_percentage')}%"
        if line != last_line:
            print(line)
            last_line = line
        if status == STATUS_DONE:
            url = result_url(history)
            if not url:
                # Sometimes the media list is filled a moment after the status flips.
                if time.time() < deadline:
                    time.sleep(4)
                    continue
                attempt.update(status="failed", error="completed without a result URL")
                return None
            stem = RAW_DIR / f"{job_name}-{index:02d}-{attempt['uuid'][:8]}"
            path = download(url, stem)
            attempt.update(status="completed", file=path.name, finished_at=now(),
                           used_credit=history.get("used_credit"))
            print(f"    saved {path.relative_to(ROOT)} ({path.stat().st_size // 1024} KB)")
            return path
        if status == STATUS_FAILED:
            attempt.update(status="failed", finished_at=now(),
                           error=f"{history.get('error_code')}: {history.get('error_message')}")
            print(f"    FAILED: {attempt['error']}")
            return None
        if time.time() > deadline:
            print("    still processing - run the script again later to resume this job")
            return None
        time.sleep(6)


def run_job(job_name: str, job: dict, state: dict, force_new: bool, timeout_s: int) -> None:
    job_state = state.setdefault(job_name, {"attempts": []})
    attempts = job_state["attempts"]
    print(f"[{job_name}]")

    pending = [a for a in attempts if a.get("status") == "processing"]
    if pending:
        attempt = pending[-1]
        print(f"    resuming job {attempt['uuid']} (not submitting again)")
    elif selected_raw(job_state) is not None and not force_new:
        print(f"    raw image already exists ({job_state['selected']}) -> not generating again")
        return
    elif outputs_exist(job) and not force_new:
        print("    final files already exist -> not generating again (use --new to force)")
        return
    else:
        attempt = submit(job)
        attempts.append(attempt)
        save_state(state)

    path = wait_and_download(job_name, attempt, attempts.index(attempt) + 1, timeout_s)
    if path is not None:
        job_state["selected"] = path.name
    save_state(state)


# ----------------------------------------------------------------------- process

def process_job(job_name: str, job: dict, state: dict) -> bool:
    raw = selected_raw(state.get(job_name, {}))
    if raw is None:
        print(f"[{job_name}] no raw image yet -> nothing to process")
        return False
    try:
        from PIL import Image
    except ImportError:
        sys.exit("Pillow is missing. Install it with:  pip install -r tools/requirements.txt")
    lanczos = getattr(Image, "Resampling", Image).LANCZOS

    source = Image.open(raw)
    source.load()
    source = source.convert("RGBA")
    print(f"[{job_name}] processing {raw.name} ({source.width}x{source.height})")

    corner = _corner_color(source)
    color_xml = job.get("background_color_xml")
    if color_xml:
        target = ROOT / color_xml["path"]
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- Written by tools/generate_images.py: background colour of the generated icon. -->\n"
            "<resources>\n"
            f'    <color name="{color_xml["name"]}">#{corner[0]:02X}{corner[1]:02X}{corner[2]:02X}</color>\n'
            "</resources>\n",
            encoding="utf-8",
        )
        print(f"    wrote {color_xml['path']} (#{corner[0]:02X}{corner[1]:02X}{corner[2]:02X})")

    for out in job.get("outputs", []):
        image = source
        scale = out.get("content_scale")
        if scale:
            image = _shrink_onto_background(image, scale, corner, lanczos)
        crop = out.get("center_crop")
        if crop:
            cw, ch = round(image.width * crop), round(image.height * crop)
            left, top = (image.width - cw) // 2, (image.height - ch) // 2
            image = image.crop((left, top, left + cw, top + ch))
        tw, th = out["size"]
        ratio = tw / th
        if image.width / image.height > ratio:
            nw = round(image.height * ratio)
            left = round((image.width - nw) * out.get("h_anchor", 0.5))
            image = image.crop((left, 0, left + nw, image.height))
        else:
            nh = round(image.width / ratio)
            top = round((image.height - nh) * out.get("v_anchor", 0.5))
            image = image.crop((0, top, image.width, top + nh))
        image = image.resize((tw, th), lanczos)
        if not out.get("alpha", False):
            flat = Image.new("RGB", image.size, corner)
            flat.paste(image, mask=image.split()[3])
            image = flat
        target = ROOT / out["path"]
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.suffix.lower() in (".jpg", ".jpeg"):
            image.convert("RGB").save(target, quality=92, optimize=True)
        else:
            image.save(target, optimize=True)
        print(f"    wrote {out['path']} ({tw}x{th}, {target.stat().st_size // 1024} KB)")
    return True


def _corner_color(image) -> tuple[int, int, int]:
    """Average colour of small patches in the four corners (the flat background)."""
    from PIL import ImageStat
    w, h = image.size
    patch = max(2, min(w, h) // 50)
    total = [0.0, 0.0, 0.0]
    corners = ((0, 0), (w - patch, 0), (0, h - patch), (w - patch, h - patch))
    for x0, y0 in corners:
        mean = ImageStat.Stat(image.crop((x0, y0, x0 + patch, y0 + patch)).convert("RGB")).mean
        for i in range(3):
            total[i] += mean[i]
    return (round(total[0] / len(corners)), round(total[1] / len(corners)), round(total[2] / len(corners)))


def _shrink_onto_background(image, scale: float, color: tuple[int, int, int], resample):
    """Scale the picture down and centre it on a canvas of the flat background colour.

    Used for the adaptive launcher icon: launchers only show the middle of the 108 dp
    foreground (a 72 dp mask, 66 dp safe zone), so the emblem needs extra margin there.
    The edge of the scaled picture is feathered so that no seam is visible.
    """
    from PIL import Image, ImageDraw, ImageFilter
    w, h = image.size
    sw, sh = round(w * scale), round(h * scale)
    small = image.resize((sw, sh), resample)
    feather = max(2, min(sw, sh) // 40)
    mask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mask).rectangle((feather, feather, sw - feather - 1, sh - feather - 1), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(feather / 2))
    canvas = Image.new("RGBA", (w, h), color + (255,))
    canvas.paste(small, ((w - sw) // 2, (h - sh) // 2), mask)
    return canvas


# -------------------------------------------------------------------------- main

def show_account() -> None:
    info = api_call("GET", "/uapi/v1/account")
    credit = info.get("user_credit") or {}
    print(f"plan_id={info.get('plan_id')} active={info.get('is_active')} "
          f"available_credit={credit.get('available_credit')} locked_credit={credit.get('locked_credit')}")
    for benefit in info.get("user_benefits") or []:
        product = benefit.get("product") or {}
        print(f"  benefit: {product.get('name')} active={benefit.get('is_active')} expires={benefit.get('expire_at')}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--only", help="comma separated job names (default: all jobs)")
    parser.add_argument("--new", action="append", default=[], metavar="JOB",
                        help="generate one more variant of JOB even if a raw image exists")
    parser.add_argument("--select", nargs=2, metavar=("JOB", "FILE"),
                        help="use an earlier raw image (file name inside play-store/graphics/raw)")
    parser.add_argument("--process-only", action="store_true", help="skip the API, only derive the final files")
    parser.add_argument("--account", action="store_true", help="show plan and credits, then exit")
    parser.add_argument("--timeout", type=int, default=900, help="seconds to wait per job (default 900)")
    args = parser.parse_args()

    load_env()
    if args.account:
        show_account()
        return 0

    jobs = json.loads(JOBS_FILE.read_text(encoding="utf-8"))
    state = load_state()

    if args.select:
        job_name, file_name = args.select
        if job_name not in jobs or not (RAW_DIR / file_name).exists():
            sys.exit(f"Unknown job or file: {job_name} / {file_name}")
        state.setdefault(job_name, {"attempts": []})["selected"] = file_name
        save_state(state)

    names = [n.strip() for n in args.only.split(",")] if args.only else list(jobs)
    names = list(dict.fromkeys(names + args.new))
    unknown = [n for n in names if n not in jobs]
    if unknown:
        sys.exit(f"Unknown job(s): {', '.join(unknown)}. Known: {', '.join(jobs)}")

    if not args.process_only:
        for name in names:
            run_job(name, jobs[name], state, force_new=name in args.new, timeout_s=args.timeout)

    for name in names:
        process_job(name, jobs[name], state)
    return 0


if __name__ == "__main__":
    sys.exit(main())
