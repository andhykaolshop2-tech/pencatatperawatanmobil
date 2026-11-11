#!/usr/bin/env python3
"""End-to-end test of the installed app on a connected device or emulator (through adb).

It drives the real UI like a user would and checks what is on screen:
first start, fill-ups, a service entry, editing and deleting, a reminder with the
notification permission, statistics, changing units, CSV export, "delete all data",
CSV import, rotation and - with --apk - the reminder notifications.

    python tools/device_test.py                     # test the app that is installed
    python tools/device_test.py --apk app/build/outputs/apk/release/app-release.apk
                                                    # install that APK first (data is wiped)

WARNING: the test clears the data of the app on the device. Use a test device or emulator.
The device should be unlocked; the test switches the app language to English (en-US).
Only the Python standard library is needed.
"""

from __future__ import annotations

import argparse
import re
import sys
import time

import adbui as a

PKG = a.PACKAGE
failures: list[str] = []


def check(condition: bool, what: str) -> None:
    print(("  ok   " if condition else "  FAIL ") + what)
    if not condition:
        failures.append(what)


def tab(name: str) -> None:
    """Taps a bottom tab (the last node with that text; cards can carry the same title)."""
    nodes = [n for n in a.dump() if n.text == name]
    a.tap_xy(*nodes[-1].center)
    time.sleep(0.6)


def fill(label: str, value: str) -> None:
    a.tap(text=label, pause=0.5)
    a.type_text(value)


def clear_field_and_type(label: str, value: str) -> None:
    a.tap(text=label, pause=0.5)
    a.key("KEYCODE_MOVE_END", 0.2)
    a.shell("input keyevent " + " ".join(["KEYCODE_DEL"] * 12))
    a.type_text(value)


def add_fill_up(amount: str, cost: str, odometer: str, full: bool = True) -> None:
    a.tap(desc="Add entry")
    items = [n for n in a.dump() if n.text == "Fill-up"]
    a.tap_xy(*items[-1].center)
    time.sleep(0.6)
    fill("Fuel amount", amount)
    fill("Total cost", cost)
    fill("Odometer", odometer)
    a.hide_keyboard()
    if not full:
        a.tap(text="Full tank")
    a.tap(text="Save")
    time.sleep(1.0)


def app_notifications() -> list[str]:
    dump = a.shell("dumpsys notification --noredact")
    titles = []
    for block in dump.split("NotificationRecord(")[1:]:
        if f"pkg={PKG}" in block[:400]:
            title = re.search(r"android.title=String \((.*?)\)", block)
            titles.append(title.group(1) if title else "?")
    return titles


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--apk", help="install this APK first (uninstalls the app, so its data is wiped)")
    args = parser.parse_args()

    if "device" not in a.adb("get-state"):
        sys.exit("No device: connect a phone with USB debugging or start an emulator.")

    if args.apk:
        a.adb("uninstall", PKG)
        result = a.adb("install", args.apk)
        check("Success" in result, "install " + args.apk)
    a.shell(f"pm clear {PKG}")
    a.shell(f"cmd locale set-app-locales {PKG} --locales en-US")
    a.shell("settings put system accelerometer_rotation 0")
    a.shell("settings put system user_rotation 0")
    a.shell("rm -f /sdcard/Download/odolog-*.csv")
    a.clear_logcat()
    a.launch()
    time.sleep(1.5)

    print("1. first start")
    texts = a.texts()
    check("Welcome to Odolog" in texts, "setup screen is shown")
    check("Miles (mi)" in texts and "mpg (US)" in texts and "USD ($)" in texts, "US defaults for an en-US phone")
    a.tap(text="Vehicle name")
    edit = [n for n in a.dump() if n.cls.endswith("EditText")][0]
    a.tap_xy(*edit.center)
    a.type_text("Test car")
    a.tap(text="OK")
    a.tap(text="Get started")
    time.sleep(1.0)
    check(a.find(text="Start your log book") is not None, "empty overview after setup")

    print("2. fill-ups and consumption")
    a.tap(text="Fill-up")
    time.sleep(0.6)
    a.tap(text="Save")
    check(a.find(text="Enter an amount greater than zero") is not None, "empty form is rejected")
    fill("Fuel amount", "10.5")
    fill("Total cost", "36.75")
    fill("Odometer", "26,300")
    check(a.find(text="$3.50/gal") is not None, "price per gallon is shown")
    a.tap(desc="Save")                      # the save action in the top bar
    time.sleep(1.0)
    add_fill_up("9.8", "33.32", "26625")
    add_fill_up("4", "14.00", "26800", full=False)
    add_fill_up("7.9", "27.65", "27010")
    texts = a.texts()
    check("32.7 mpg" in texts, "consumption over two full-tank intervals is 32.7 mpg")
    check("$0.157/mi" in texts, "total cost per mile is $0.157")
    check("710 mi" in texts and "27,010 mi" in texts, "distance and odometer")

    print("3. service entry, log, edit, delete")
    a.tap(desc="Add entry")
    a.tap(text="Service or expense")
    time.sleep(0.6)
    a.tap(contains="Category:")
    a.tap(text="Tires")
    fill("Title (optional)", "All-season tires")
    fill("Total cost", "486.40")
    a.hide_keyboard()
    a.tap(text="Save")
    time.sleep(1.0)
    tab("Log")
    texts = a.texts()
    check("All-season tires" in texts and "$486.40" in texts, "expense is in the log")
    check(any("33.2 mpg" in t for t in texts), "per-fill-up consumption is shown in the log")
    a.tap(text="Service & costs")
    time.sleep(0.5)
    check(a.find(contains="gal") is None, "filter hides the fill-ups")
    a.tap(text="All")
    a.tap(text="All-season tires")
    time.sleep(0.6)
    check(a.find(text="486.40") is not None, "edit form shows the amount with two decimals")
    clear_field_and_type("Total cost", "500")
    a.hide_keyboard()
    a.tap(text="Save")
    time.sleep(1.0)
    check(a.find(text="$500.00") is not None, "edited amount is saved")
    a.tap(text="All-season tires")
    time.sleep(0.6)
    a.tap(desc="Delete")
    a.tap(text="Delete")
    time.sleep(1.0)
    check(a.find(text="All-season tires") is None, "entry is deleted")

    print("4. reminder and notification permission")
    tab("Reminders")
    a.tap(desc="Add reminder")
    time.sleep(0.6)
    a.tap(text="Save")
    check(a.find(text="Enter a title") is not None, "reminder without title is rejected")
    fill("What is due?", "Annual service")
    a.hide_keyboard()
    a.tap(text="Inspection")
    a.tap(contains="Due date:")
    time.sleep(0.8)
    check(a.find(text="Select date") is not None, "date picker opens")
    a.tap(text="OK")
    a.tap(contains="Repeat:")
    a.tap(text="Repeats every 6 months")
    a.tap(contains="Remind me:")
    a.tap(text="30 days before")
    a.tap(text="Save")
    time.sleep(2.0)
    allow = a.find(text="Allow")
    if allow is not None:                   # Android 13+: the system asks once
        a.tap_xy(*allow.center)
        time.sleep(1.0)
    texts = a.texts()
    check("Annual service" in texts and "Repeats every 6 months" in texts, "reminder is listed")
    first_date = next((t for t in texts if t.startswith("Inspection ·")), "")
    a.tap(text="Done")
    time.sleep(1.2)
    texts = a.texts()
    second_date = next((t for t in texts if t.startswith("Inspection ·")), "")
    check(any(t.startswith("Next due date:") for t in texts) and second_date != first_date,
          "Done moves a repeating reminder to its next date")

    print("5. statistics and units")
    tab("Statistics")
    texts = a.texts()
    check("Total cost per mi" in texts and "Fuel cost per mi" in texts, "statistics cards")
    a.scroll_down()
    check(a.find(text="Fuel consumption trend") is not None, "trend chart is shown")
    a.tap(desc="Settings")
    time.sleep(0.6)
    a.tap(text="Distance")
    a.tap(text="Kilometers (km)")
    check(a.find(text="L/100 km") is not None, "consumption unit follows the distance unit")
    a.back()
    tab("Overview")
    texts = a.texts()
    check(any(t.endswith("L/100 km") for t in texts) and any(t.endswith(" km") for t in texts), "overview shows metric values")
    a.tap(desc="Settings")
    time.sleep(0.6)
    a.tap(text="Distance")
    a.tap(text="Miles (mi)")
    a.tap(text="Fuel consumption")
    a.tap(text="mpg (US)")

    print("6. export, delete all, import")
    a.tap(text="Export CSV file")
    time.sleep(2.5)
    a.tap(text="SAVE")
    time.sleep(1.5)
    check(a.find(text="Export finished (entries: 4, reminders: 1)") is not None, "export reports 4 entries and 1 reminder")
    exported = a.shell("ls /sdcard/Download/").split()
    name = next((f for f in exported if f.startswith("odolog-") and f.endswith(".csv")), "")
    check(bool(name), "exported file exists in Downloads")
    content = a.adb("exec-out", "cat", "/sdcard/Download/" + name)
    check("fuel,20" in content and ",mi," in content and "reminder,20" in content, "file has fuel rows in miles and the reminder")
    a.scroll_down()
    a.tap(text="Delete all data")
    a.tap(text="Delete")
    time.sleep(1.0)
    a.back()
    tab("Overview")
    check(a.find(text="Start your log book") is not None, "everything is deleted")
    a.tap(desc="Settings")
    time.sleep(0.6)
    a.tap(text="Import CSV file")
    time.sleep(2.5)
    a.tap(text=name)
    time.sleep(2.0)
    check(a.find(contains="entries: 4, reminders: 1") is not None, "import dialog counts the rows")
    a.tap(text="Import")
    time.sleep(1.5)
    a.back()
    tab("Overview")
    texts = a.texts()
    check("32.7 mpg" in texts and "27,010 mi" in texts, "imported data gives the same statistics")
    a.tap(desc="Settings")
    time.sleep(0.6)
    a.tap(text="Import CSV file")
    time.sleep(2.5)
    a.tap(text=name)
    time.sleep(2.0)
    a.tap(text="Import")
    time.sleep(1.5)
    check(a.find(text="Import finished (entries: 0, reminders: 0)") is not None, "importing twice adds no duplicates")
    a.back()

    print("7. rotation keeps what was typed")
    tab("Overview")
    a.tap(desc="Add entry")
    items = [n for n in a.dump() if n.text == "Fill-up"]
    a.tap_xy(*items[-1].center)
    time.sleep(0.6)
    fill("Fuel amount", "11.25")
    a.hide_keyboard()
    a.shell("settings put system user_rotation 1")
    time.sleep(2.5)
    check(a.find(text="11.25") is not None, "text survives the rotation")
    a.shell("settings put system user_rotation 0")
    time.sleep(2.5)
    a.back()
    if a.find(text="New fill-up") is not None:
        a.back()

    if args.apk:
        print("8. reminder notifications (triggered by installing the APK again)")
        today = a.shell("date +%Y-%m-%d").strip()
        csv = ("record,date,category,title,repeat_months,notify_days_before\r\n"
               f"reminder,{today},inspection,Due today test,0,7\r\n"
               f"reminder,{today},service,Second test,12,7\r\n")
        # printf turns the \r\n escapes into real line ends on the device
        a.shell("printf '" + csv.replace("\r\n", "\\r\\n") + "' > /sdcard/Download/odolog-reminders.csv")
        a.tap(desc="Settings")
        time.sleep(0.6)
        a.tap(text="Import CSV file")
        time.sleep(2.5)
        a.tap(text="odolog-reminders.csv")
        time.sleep(2.0)
        a.tap(text="Import")
        time.sleep(1.5)
        a.back()
        a.key("KEYCODE_HOME")
        a.adb("install", "-r", args.apk)    # "app updated" runs the reminder check
        time.sleep(6.0)
        titles = app_notifications()
        check("Due today test" in titles and "Second test" in titles, "two notifications are posted: %s" % titles)
        a.shell("cmd statusbar expand-notifications")
        time.sleep(1.5)
        node = a.find(text="Due today test") or a.find(contains="Due today test")
        if node is not None:
            a.tap_xy(*node.center)
            time.sleep(2.5)
            if a.find(text="Clear all") is not None:        # the group only expanded
                node = a.find(text="Due today test")
                a.tap_xy(*node.center)
                time.sleep(2.5)
        texts = a.texts()
        check("Due today test" in texts and "Due today" in texts, "tapping the notification opens the reminders tab")
        a.key("KEYCODE_HOME")
        before = app_notifications()
        a.adb("install", "-r", args.apk)
        time.sleep(6.0)
        after = app_notifications()
        check(set(after) <= set(before), "a second check does not notify again (before %s, after %s)" % (before, after))

    crash = a.crashed()
    check(not crash, "no crash in the log")
    if crash:
        print(crash[:3000])
    print()
    print("RESULT:", "ALL CHECKS PASSED" if not failures else "%d CHECK(S) FAILED: %s" % (len(failures), failures))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
