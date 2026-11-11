"""Small helper to drive an Android device or emulator through adb (no extra packages).

It reads the screen with `uiautomator dump` (works with Jetpack Compose, because Compose
publishes its texts and content descriptions to the accessibility tree), finds elements by
their text and taps them. Used by tools/device_test.py and tools/take_screenshots.py.
"""

from __future__ import annotations

import os
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.personalizeprolabs.odolog"


def find_adb() -> str:
    for root in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"),
                 os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk")):
        if root:
            candidate = pathlib.Path(root) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
            if candidate.exists():
                return str(candidate)
    return "adb"


ADB = find_adb()


def adb(*args: str, timeout: int = 120, check: bool = False) -> str:
    result = subprocess.run([ADB, *args], capture_output=True, timeout=timeout)
    output = result.stdout.decode("utf-8", "replace") + result.stderr.decode("utf-8", "replace")
    if check and result.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {output.strip()}")
    return output


def shell(command: str, timeout: int = 120) -> str:
    return adb("shell", command, timeout=timeout)


class Node:
    def __init__(self, element: ET.Element):
        self.text = element.get("text", "")
        self.desc = element.get("content-desc", "")
        self.cls = element.get("class", "")
        self.clickable = element.get("clickable") == "true"
        self.checked = element.get("checked") == "true"
        self.selected = element.get("selected") == "true"
        self.package = element.get("package", "")
        match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", element.get("bounds", ""))
        self.bounds = tuple(int(g) for g in match.groups()) if match else (0, 0, 0, 0)

    @property
    def center(self) -> tuple[int, int]:
        left, top, right, bottom = self.bounds
        return (left + right) // 2, (top + bottom) // 2

    def __repr__(self) -> str:
        return f"<{self.cls.split('.')[-1]} text={self.text!r} desc={self.desc!r} {self.bounds}>"


def dump(retries: int = 6) -> list[Node]:
    """All nodes currently on screen, in document order."""
    for _ in range(retries):
        shell("uiautomator dump /sdcard/window_dump.xml")
        raw = subprocess.run([ADB, "exec-out", "cat", "/sdcard/window_dump.xml"], capture_output=True, timeout=60).stdout
        try:
            root = ET.fromstring(raw.decode("utf-8", "replace"))
            return [Node(element) for element in root.iter("node")]
        except ET.ParseError:
            time.sleep(1.0)
    raise RuntimeError("could not read the screen (uiautomator dump failed)")


def texts() -> list[str]:
    """Visible texts and content descriptions, for logging and assertions."""
    return [n.text or n.desc for n in dump() if n.text or n.desc]


def find(text: str | None = None, desc: str | None = None, contains: str | None = None,
         nodes: list[Node] | None = None) -> Node | None:
    for node in nodes if nodes is not None else dump():
        if text is not None and node.text == text:
            return node
        if desc is not None and node.desc == desc:
            return node
        if contains is not None and (contains in node.text or contains in node.desc):
            return node
    return None


def wait_for(text: str | None = None, desc: str | None = None, contains: str | None = None,
             timeout: float = 20.0) -> Node:
    deadline = time.time() + timeout
    while True:
        node = find(text=text, desc=desc, contains=contains)
        if node is not None:
            return node
        if time.time() > deadline:
            raise RuntimeError(f"not on screen: {text or desc or contains!r}; visible: {texts()[:40]}")
        time.sleep(0.7)


def tap_xy(x: int, y: int, pause: float = 0.8) -> None:
    shell(f"input tap {x} {y}")
    time.sleep(pause)


def tap(text: str | None = None, desc: str | None = None, contains: str | None = None,
        timeout: float = 20.0, pause: float = 0.8) -> Node:
    node = wait_for(text=text, desc=desc, contains=contains, timeout=timeout)
    tap_xy(*node.center, pause=pause)
    return node


def type_text(value: str, pause: float = 0.4) -> None:
    """Types into the focused field. Only simple ASCII is safe with `input text`."""
    escaped = value.replace(" ", "%s")
    for char in "\\'\"&|;<>()$`":
        escaped = escaped.replace(char, "\\" + char)
    shell(f"input text {escaped}")
    time.sleep(pause)


def key(name: str, pause: float = 0.5) -> None:
    shell(f"input keyevent {name}")
    time.sleep(pause)


def back(pause: float = 0.8) -> None:
    key("KEYCODE_BACK", pause)


def keyboard_shown() -> bool:
    return "mInputShown=true" in shell("dumpsys input_method")


def hide_keyboard(pause: float = 0.6) -> None:
    """Closes the on-screen keyboard if it is open (BACK would otherwise leave the screen)."""
    if keyboard_shown():
        back(pause)


def swipe(x1: int, y1: int, x2: int, y2: int, duration_ms: int = 300, pause: float = 0.8) -> None:
    shell(f"input swipe {x1} {y1} {x2} {y2} {duration_ms}")
    time.sleep(pause)


def scroll_down(pause: float = 0.8) -> None:
    swipe(540, 1700, 540, 700, 350, pause)


def scroll_up(pause: float = 0.8) -> None:
    swipe(540, 700, 540, 1700, 350, pause)


def screenshot(path: str | os.PathLike) -> pathlib.Path:
    target = pathlib.Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    data = subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout
    if not data.startswith(b"\x89PNG"):
        raise RuntimeError("screencap did not return a PNG image")
    target.write_bytes(data)
    return target


def launch(package: str = PACKAGE, pause: float = 2.5) -> None:
    shell(f"monkey -p {package} -c android.intent.category.LAUNCHER 1")
    time.sleep(pause)


def force_stop(package: str = PACKAGE) -> None:
    shell(f"am force-stop {package}")


def clear_data(package: str = PACKAGE) -> None:
    shell(f"pm clear {package}")


def crashed(package: str = PACKAGE) -> str:
    """Returns the crash part of logcat for the app, or an empty string."""
    log = adb("logcat", "-d", "-b", "crash", timeout=60)
    return log if package in log else ""


def clear_logcat() -> None:
    adb("logcat", "-c")
