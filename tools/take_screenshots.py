#!/usr/bin/env python3
"""Takes the Play Store screenshots from the real app on a connected device or emulator.

For every listing language it resets the app, switches the app language, imports the
example log books from docs/demo-data and captures the screens. The pictures are real
screenshots of the running app (1080x2400 on the reference emulator); nothing is mocked.

    python tools/make_demo_data.py          # fresh example data, dated up to today
    python tools/take_screenshots.py        # -> play-store/screenshots/raw/<language>/
    python tools/frame_screenshots.py       # -> play-store/screenshots/<language>/  (1080x1920)

WARNING: the data of the app on the device is wiped. Use a test device or emulator with
Android 13 or newer (the app language is switched with `cmd locale`).
The texts the script taps on are read from the app's own string resources, so it keeps
working when the wording changes. Only the Python standard library is needed.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys
import time
import xml.etree.ElementTree as ET

import adbui as a

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
DEMO = ROOT / "docs" / "demo-data"
OUT = ROOT / "play-store" / "screenshots" / "raw"
PKG = a.PACKAGE

VARIANTS = [
    # listing language, app locale, string folder, fuel car demo, electric car demo, example values for the form
    dict(lang="en-US", locale="en-US", values="values", demo="demo-en-US.csv", electric="demo-ev-en-US.csv",
         amount="11.2", cost="38.64"),
    dict(lang="en-GB", locale="en-GB", values="values-en-rGB", demo="demo-en-GB.csv", electric="demo-ev-en-GB.csv",
         amount="38.5", cost="54.86"),
    dict(lang="de-DE", locale="de-DE", values="values-de", demo="demo-de-DE.csv", electric="demo-ev-de-DE.csv",
         amount="41,5", cost="72,21"),
]


def load_strings(folder: str) -> dict[str, str]:
    """name -> text for one language (falls back to English for untranslated names)."""
    strings: dict[str, str] = {}
    for values in ("values", folder):
        for file in sorted((RES / values).glob("*.xml")):
            for item in ET.parse(file).getroot().iter("string"):
                text = "".join(item.itertext())
                text = text.replace("\\n", "\n").replace("\\'", "'").replace('\\"', '"')
                strings[item.get("name")] = text
    return strings


def demo_mode(on: bool) -> None:
    """A clean status bar: fixed clock, full battery, no notification icons."""
    if on:
        a.shell("settings put global sysui_demo_allowed 1")
        broadcast = "am broadcast -a com.android.systemui.demo -e command "
        a.shell(broadcast + "enter")
        a.shell(broadcast + "clock -e hhmm 0930")
        a.shell(broadcast + "battery -e level 100 -e plugged false")
        # "fully true" = validated connection (otherwise the Wi-Fi icon gets an exclamation mark).
        # The mobile icon is hidden: without a data type the demo mode would label it "3G".
        a.shell(broadcast + "network -e wifi show -e level 4 -e fully true")
        a.shell(broadcast + "network -e mobile hide")
        a.shell(broadcast + "notifications -e visible false")
    else:
        a.shell("am broadcast -a com.android.systemui.demo -e command exit")


def tap_last(text: str) -> None:
    """Taps the last node with this text (the bottom tab, not a card with the same title)."""
    nodes = [n for n in a.dump() if n.text == text]
    if not nodes:
        raise RuntimeError(f"not on screen: {text!r}; visible: {a.texts()[:30]}")
    a.tap_xy(*nodes[-1].center)
    time.sleep(0.8)


def tap_file(name: str) -> None:
    """Taps a file in the system file picker; scrolls down when the folder has many files."""
    a.wait_for(contains=".csv", timeout=20)
    for _ in range(8):
        node = a.find(text=name)
        if node is not None:
            a.tap_xy(*node.center)
            return
        a.scroll_down()
    raise RuntimeError(f"not found in the file picker: {name}; visible: {a.texts()[:40]}")


def import_file(s: dict[str, str], name: str) -> None:
    a.tap(desc=s["settings_title"])
    time.sleep(0.8)
    a.tap(text=s["settings_import"])
    time.sleep(2.5)
    tap_file(name)
    time.sleep(2.0)
    replace = a.find(text=s["import_mode_replace"])
    if replace is not None:
        a.tap_xy(*replace.center)
        time.sleep(0.4)
    a.tap(text=s["import_action"])
    time.sleep(2.0)
    a.back()
    time.sleep(0.8)


def capture(folder: pathlib.Path, name: str) -> None:
    time.sleep(0.8)
    path = a.screenshot(folder / (name + ".png"))
    print("   ", path.relative_to(ROOT))


def run_variant(v: dict) -> None:
    s = load_strings(v["values"])
    folder = OUT / v["lang"]
    folder.mkdir(parents=True, exist_ok=True)
    for old in folder.glob("*.png"):
        old.unlink()
    print(f"[{v['lang']}]")

    a.shell(f"am force-stop {PKG}")
    a.shell(f"pm clear {PKG}")
    a.shell(f"cmd locale set-app-locales {PKG} --locales {v['locale']}")
    a.shell(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    a.launch()
    time.sleep(1.5)
    a.tap(text=s["setup_start"])
    time.sleep(1.0)

    # ---- car with a fuel engine
    import_file(s, v["demo"])
    tap_last(s["tab_home"])
    capture(folder, "01-overview")

    tap_last(s["tab_stats"])
    a.swipe(540, 1750, 540, 560, 600)
    capture(folder, "02-statistics")

    tap_last(s["tab_log"])
    capture(folder, "03-log")

    tap_last(s["tab_reminders"])
    capture(folder, "04-reminders")

    # ---- the form for a new fill-up, with values typed in
    tap_last(s["tab_home"])
    odometer_row = [n for n in a.dump() if n.text.endswith(" km") or n.text.endswith(" mi")]
    last = int(re.sub(r"\D", "", odometer_row[-1].text)) if odometer_row else 50000
    a.tap(desc=s["action_add_entry"])
    items = [n for n in a.dump() if n.text == s["add_fuel"]]
    a.tap_xy(*items[-1].center)
    time.sleep(0.8)
    for label, value in ((s["field_volume"], v["amount"]), (s["field_total_cost"], v["cost"]),
                         (s["field_odometer"], str(last + 342))):
        a.tap(text=label, pause=0.5)
        a.type_text(value)
    a.hide_keyboard()
    capture(folder, "06-new-fill-up")
    a.back()
    time.sleep(0.8)

    # ---- privacy summary
    a.tap(desc=s["settings_title"])
    time.sleep(0.8)
    a.scroll_down()
    a.tap(text=s["settings_privacy"])
    capture(folder, "07-privacy")
    a.tap(text=s["action_ok"])
    a.back()
    time.sleep(0.8)

    # ---- electric car (replaces the data; the vehicle in the file switches the drive type)
    import_file(s, v["electric"])
    tap_last(s["tab_home"])
    capture(folder, "05-electric-car")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--only", help="one listing language, for example de-DE")
    args = parser.parse_args()

    if "device" not in a.adb("get-state"):
        sys.exit("No device: connect a phone with USB debugging or start an emulator.")
    if PKG not in a.shell("pm list packages " + PKG):
        sys.exit("The app is not installed on the device.")
    missing = [f for v in VARIANTS for f in (v["demo"], v["electric"]) if not (DEMO / f).exists()]
    if missing:
        sys.exit("Run tools/make_demo_data.py first; missing: " + ", ".join(missing))

    for v in VARIANTS:
        for name in (v["demo"], v["electric"]):
            a.adb("push", str(DEMO / name), "/sdcard/Download/")
    a.shell("cmd uimode night no")
    a.shell("settings put system font_scale 1.0")
    a.shell("settings put system accelerometer_rotation 0")
    a.shell("settings put system user_rotation 0")
    demo_mode(True)
    try:
        for v in VARIANTS:
            if args.only and v["lang"] != args.only:
                continue
            run_variant(v)
    finally:
        demo_mode(False)
    crash = a.crashed()
    if crash:
        print(crash[:2000])
        return 1
    print("done")
    return 0


if __name__ == "__main__":
    sys.exit(main())
