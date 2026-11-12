#!/usr/bin/env python3
"""Collects everything the Play Console needs into one folder: siap-upload/

    ./gradlew bundleRelease assembleRelease     # signed release (needs keystore.properties)
    python tools/frame_screenshots.py           # framed screenshots
    python tools/collect_release.py             # -> siap-upload/

The folder is rebuilt from scratch on every run and is git-ignored (it contains the bundle).
The keystore and its password are NOT copied: they stay in signing/ and keystore.properties.
Only the Python standard library is needed.
"""

from __future__ import annotations

import datetime
import hashlib
import os
import pathlib
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "siap-upload"
AAB = ROOT / "app" / "build" / "outputs" / "bundle" / "release" / "app-release.aab"
APK = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
LISTING = ROOT / "play-store" / "listing"
GRAPHICS = ROOT / "play-store" / "graphics"
SHOTS = ROOT / "play-store" / "screenshots"
POLICY = ROOT / "docs" / "privacy-policy.html"
CHEAT_SHEET = ROOT / "docs" / "CONTEKAN-PLAY-CONSOLE.md"
BLOGGER_FROM, BLOGGER_TO = "<!-- BLOGGER: COPY FROM HERE -->", "<!-- BLOGGER: COPY UNTIL HERE -->"


def fail(message: str) -> None:
    sys.exit("collect_release: " + message)


def read_version() -> tuple[str, str, str]:
    gradle = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    code = re.search(r"versionCode\s*=\s*(\d+)", gradle)
    app_id = re.search(r'applicationId\s*=\s*"([^"]+)"', gradle)
    if not (name and code and app_id):
        fail("cannot read applicationId / versionCode / versionName from app/build.gradle.kts")
    return app_id.group(1), code.group(1), name.group(1)


def policy_url() -> str:
    config = (ROOT / "app" / "src" / "main" / "res" / "values" / "config.xml").read_text(encoding="utf-8")
    return re.search(r'name="privacy_policy_url"[^>]*>([^<]+)<', config).group(1).strip()


def newest_source_time() -> float:
    files = [ROOT / "app" / "build.gradle.kts", ROOT / "gradle" / "libs.versions.toml"]
    files += [p for p in (ROOT / "app" / "src" / "main").rglob("*") if p.is_file()]
    return max(p.stat().st_mtime for p in files)


def find_keytool() -> str | None:
    name = "keytool.exe" if os.name == "nt" else "keytool"
    homes = [os.environ.get("JAVA_HOME"), r"C:\Program Files\Android\Android Studio\jbr",
             "/Applications/Android Studio.app/Contents/jbr/Contents/Home", "/opt/android-studio/jbr"]
    for home in homes:
        if home and (pathlib.Path(home) / "bin" / name).exists():
            return str(pathlib.Path(home) / "bin" / name)
    return shutil.which("keytool")


def certificate(bundle: pathlib.Path) -> dict[str, str]:
    """Owner and fingerprints of the signing certificate (public information, no password needed).

    Read from the bundle, which is signed like a jar; the release APK only has an APK v2
    signature (minSdk 26), which keytool cannot read.
    """
    keytool = find_keytool()
    if keytool is None or not bundle.exists():
        return {}
    result = subprocess.run([keytool, "-printcert", "-jarfile", str(bundle)], capture_output=True, text=True)
    info: dict[str, str] = {}
    for line in result.stdout.splitlines():
        key, _, value = line.strip().partition(":")
        if key in ("Owner", "SHA1", "SHA256") and key not in info:
            info[key] = value.strip()
    return info


def _varint(data: bytes, pos: int) -> tuple[int, int]:
    result = shift = 0
    while True:
        byte = data[pos]
        pos += 1
        result |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return result, pos
        shift += 7


def _fields(data: bytes):
    """(field number, wire type, value) for every field of one protobuf message."""
    pos = 0
    while pos < len(data):
        key, pos = _varint(data, pos)
        number, wire = key >> 3, key & 7
        if wire == 0:
            value, pos = _varint(data, pos)
        elif wire == 2:
            length, pos = _varint(data, pos)
            value, pos = data[pos:pos + length], pos + length
        elif wire in (1, 5):
            size = 8 if wire == 1 else 4
            value, pos = data[pos:pos + size], pos + size
        else:
            raise ValueError("unexpected protobuf wire type in the bundle manifest")
        yield number, wire, value


def _elements(node: bytes):
    """(tag name, {attribute name: value}) for every element of an aapt2 XmlNode message."""
    for number, wire, value in _fields(node):
        if number != 1 or wire != 2:                       # XmlNode.element
            continue
        name, attributes, children = "", {}, []
        for field, field_wire, content in _fields(value):
            if field_wire != 2:
                continue
            if field == 3:                                 # XmlElement.name
                name = content.decode("utf-8")
            elif field == 4:                               # XmlElement.attribute
                parts = {n: v for n, w, v in _fields(content) if w == 2}
                attributes[parts.get(2, b"").decode("utf-8")] = parts.get(3, b"").decode("utf-8")
            elif field == 5:                               # XmlElement.child
                children.append(content)
        yield name, attributes
        for child in children:
            yield from _elements(child)


def bundle_manifest(aab: pathlib.Path) -> list[tuple[str, dict[str, str]]]:
    """All elements of the bundle's manifest (it is stored as a protobuf, not as XML text)."""
    return list(_elements(zipfile.ZipFile(aab).read("base/manifest/AndroidManifest.xml")))


def bundle_permissions(manifest: list[tuple[str, dict[str, str]]]) -> list[str]:
    """The permissions the bundle requests: its <uses-permission> entries."""
    return sorted(attributes.get("name", "") for name, attributes in manifest if name.startswith("uses-permission"))


def bundle_version(manifest: list[tuple[str, dict[str, str]]]) -> tuple[str, str, str]:
    """(package, versionCode, versionName) as they are written inside the bundle."""
    for name, attributes in manifest:
        if name == "manifest":
            return attributes.get("package", ""), attributes.get("versionCode", ""), attributes.get("versionName", "")
    return "", "", ""


def copy(source: pathlib.Path, target: pathlib.Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)


def main() -> int:
    app_id, code, name = read_version()
    if not AAB.exists():
        fail("no release bundle yet: run  ./gradlew bundleRelease assembleRelease  first")
    if "META-INF/MANIFEST.MF" not in zipfile.ZipFile(AAB).namelist():
        fail("the bundle is not signed: create keystore.properties (see keystore.properties.example)")
    if AAB.stat().st_mtime < newest_source_time():
        fail("the bundle is older than the app sources: build again, then run this script again")
    manifest = bundle_manifest(AAB)
    if bundle_version(manifest) != (app_id, code, name):
        fail(f"the bundle is {bundle_version(manifest)} but app/build.gradle.kts says {(app_id, code, name)}: "
             "build again, then run this script again")
    permissions = bundle_permissions(manifest)
    if not permissions or "android.permission.INTERNET" in permissions:
        fail("unexpected permissions in the bundle: " + ", ".join(permissions)
             + " - the listing and the privacy policy promise an app without internet access")
    languages = sorted(p.name for p in LISTING.iterdir() if p.is_dir())
    missing = [lang for lang in languages if len(list((SHOTS / lang).glob("*.png"))) < 2]
    if missing:
        fail("framed screenshots are missing for: " + ", ".join(missing) + " (tools/frame_screenshots.py)")
    policy = POLICY.read_text(encoding="utf-8")
    if BLOGGER_FROM not in policy or BLOGGER_TO not in policy:
        fail("docs/privacy-policy.html has no BLOGGER markers")
    if re.search(r"\[\[[A-Z_]+\]\]", policy):
        fail("docs/privacy-policy.html still contains [[PLACEHOLDERS]]")

    if OUT.exists():
        shutil.rmtree(OUT)
    base = f"odolog-{name}-vc{code}"
    copy(AAB, OUT / "1-aab" / (base + ".aab"))
    if APK.exists():
        copy(APK, OUT / "1-aab" / (base + "-untuk-dicoba-di-hp.apk"))
    for lang in languages:
        for file in sorted((LISTING / lang).glob("*.txt")):
            copy(file, OUT / "2-listing" / lang / file.name)
        for file in sorted((SHOTS / lang).glob("*.png")):
            copy(file, OUT / "3-grafik" / "screenshots" / lang / file.name)
    copy(GRAPHICS / "icon-512.png", OUT / "3-grafik" / "icon-512.png")
    copy(GRAPHICS / "feature-graphic-1024x500.png", OUT / "3-grafik" / "feature-graphic-1024x500.png")
    fragment = policy.split(BLOGGER_FROM, 1)[1].split(BLOGGER_TO, 1)[0].strip() + "\n"
    target = OUT / "4-kebijakan-privasi" / "tempel-di-blogger.html"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(fragment, encoding="utf-8", newline="\n")
    copy(POLICY, OUT / "4-kebijakan-privasi" / "pratinjau-di-browser.html")
    copy(CHEAT_SHEET, OUT / "5-contekan" / CHEAT_SHEET.name)

    cert = certificate(AAB)
    sha256 = hashlib.sha256(AAB.read_bytes()).hexdigest()
    shots = {lang: len(list((SHOTS / lang).glob("*.png"))) for lang in languages}
    url = policy_url()
    lines = [
        f"# Odolog {name} - paket siap upload ke Play Console",
        "",
        f"Dibuat {datetime.datetime.now():%Y-%m-%d %H:%M} oleh `tools/collect_release.py`. "
        "Folder ini tidak ikut ke GitHub (ada di `.gitignore`) dan dibuat ulang setiap script dijalankan.",
        "",
        "## Isi folder",
        "",
        "| Folder / file | Untuk apa |",
        "|---|---|",
        f"| `1-aab/{base}.aab` | **File yang diupload ke Play Console.** Sudah ditandatangani dengan upload key. |",
        f"| `1-aab/{base}-untuk-dicoba-di-hp.apk` | Hanya untuk dicoba di HP sendiri (`adb install`). Jangan diupload. |",
        "| `2-listing/<bahasa>/` | Teks listing: `title.txt`, `short_description.txt`, `full_description.txt`, `release_notes.txt` |",
        "| `3-grafik/icon-512.png` | App icon 512x512 |",
        "| `3-grafik/feature-graphic-1024x500.png` | Feature graphic 1024x500 |",
        "| `3-grafik/screenshots/<bahasa>/` | Phone screenshots 1080x1920 ("
        + ", ".join(f"{lang}: {count}" for lang, count in shots.items()) + ") |",
        "| `4-kebijakan-privasi/tempel-di-blogger.html` | Isi halaman kebijakan privasi untuk ditempel di Blogger (tampilan HTML) |",
        "| `4-kebijakan-privasi/pratinjau-di-browser.html` | Halaman yang sama, untuk dilihat dulu di browser |",
        "| `5-contekan/CONTEKAN-PLAY-CONSOLE.md` | Jawaban siap salin untuk semua formulir Play Console |",
        "",
        "## Urutan kerja",
        "",
        "1. **Kebijakan privasi** - pastikan halaman ini tayang (alamat ini tertanam di dalam app):",
        f"   <{url}>",
        "   Kalau belum ada: Blogger -> Halaman -> Halaman baru, judul persis `Odolog Privacy Policy`,",
        "   ganti ke *Tampilan HTML*, tempel seluruh isi `4-kebijakan-privasi/tempel-di-blogger.html`,",
        "   lalu Publikasikan. Kalau isinya berubah: edit halaman yang sama dan tempel ulang.",
        "   Kalau alamatnya berbeda, ubah `privacy_policy_url` di `app/src/main/res/values/config.xml`,",
        "   naikkan `versionCode`, build ulang, dan jalankan script ini lagi.",
        "2. **Play Console** - buat app, isi formulir memakai `5-contekan/CONTEKAN-PLAY-CONSOLE.md`.",
        "3. **Listing** - salin teks dari `2-listing/` dan unggah grafik dari `3-grafik/` per bahasa.",
        f"4. **Rilis** - unggah `1-aab/{base}.aab`, catatan rilis dari `release_notes.txt`.",
        "",
        "## Data bundle",
        "",
        "| Hal | Nilai |",
        "|---|---|",
        f"| applicationId | `{app_id}` |",
        f"| versionCode / versionName | {code} / {name} |",
        f"| Ukuran AAB | {AAB.stat().st_size:,} byte |",
        f"| SHA-256 file AAB | `{sha256}` |",
        "| Izin yang diminta | " + ", ".join(f"`{p}`" for p in permissions) + " |",
    ]
    if cert:
        lines += [
            f"| Sertifikat upload key | {cert.get('Owner', '')} |",
            f"| SHA-256 sertifikat | `{cert.get('SHA256', '')}` |",
            f"| SHA-1 sertifikat | `{cert.get('SHA1', '')}` |",
        ]
    lines += [
        "",
        "Upload key (file `signing/odolog-upload.jks`) dan sandinya (`keystore.properties`) **tidak** ada di",
        "folder ini. Cadangkan keduanya di tempat aman di luar PC: tanpa keduanya update app berikutnya",
        "tidak bisa ditandatangani dengan kunci yang sama.",
        "",
    ]
    (OUT / "BACA-DULU.md").write_text("\n".join(lines), encoding="utf-8", newline="\n")

    for path in sorted(OUT.rglob("*")):
        if path.is_file():
            print(f"  {path.relative_to(ROOT)}  ({path.stat().st_size:,} bytes)")
    print(f"done: {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
