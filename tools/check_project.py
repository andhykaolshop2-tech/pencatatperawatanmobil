#!/usr/bin/env python3
"""Static checks for the Odolog project that need no Android SDK and no Gradle.

It catches the kind of mistakes that otherwise only show up as a build error (or a
crash) much later:

  * resource files are well-formed XML
  * every R.string / R.plurals / R.drawable / ... used in Kotlin exists
  * every @string/ @xml/ @mipmap/ ... used in XML exists
  * German has exactly the same strings as English, with the same placeholders
  * regional English files only override strings that exist
  * strings contain no unescaped apostrophe / quote and no stray percent sign
  * vector drawables have syntactically valid path data
  * Kotlin files have balanced brackets, and names that are imported in one file are
    not used without import in another one (heuristic, reported as a warning)
  * Play Store listing texts respect the length limits

Usage:  python tools/check_project.py        (exit code 1 when an ERROR was found)
Only the Python standard library is used.
"""

from __future__ import annotations

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
SRC_DIRS = [ROOT / "app" / "src" / "main" / "java", ROOT / "app" / "src" / "test" / "java"]
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
LISTING = ROOT / "play-store" / "listing"

errors: list[str] = []
warnings: list[str] = []


def error(message: str) -> None:
    errors.append(message)


def warn(message: str) -> None:
    warnings.append(message)


def rel(path: pathlib.Path) -> str:
    return str(path.relative_to(ROOT)).replace("\\", "/")


# ----------------------------------------------------------------- resources

def parse_xml(path: pathlib.Path):
    try:
        return ET.parse(path).getroot()
    except ET.ParseError as exc:
        error(f"{rel(path)}: not well-formed XML ({exc})")
        return None


def collect_resources() -> dict[str, set[str]]:
    """Names of all resources, by type (string, plurals, drawable, mipmap, xml, ...)."""
    found: dict[str, set[str]] = {}
    for folder in sorted(RES.iterdir()):
        if not folder.is_dir():
            continue
        kind = folder.name.split("-")[0]
        for file in sorted(folder.iterdir()):
            if kind == "values":
                root = parse_xml(file)
                if root is None:
                    continue
                for item in root:
                    name = item.get("name")
                    if name:
                        tag = "style" if item.tag == "style" else item.tag
                        found.setdefault(tag, set()).add(name.replace(".", "_"))
            else:
                if file.suffix == ".xml":
                    parse_xml(file)
                found.setdefault(kind, set()).add(file.stem)
    return found


PLACEHOLDER = re.compile(r"%(\d+)\$[sd]")


def string_problems(text: str) -> list[str]:
    problems = []
    if re.search(r"(?<!\\)'", text):
        problems.append("unescaped apostrophe (write ’ or \\')")
    if re.search(r'(?<!\\)"', text):
        problems.append('unescaped double quote (write “ ” or \\")')
    without_placeholders = PLACEHOLDER.sub("", text)
    if "%" in without_placeholders:
        problems.append("stray percent sign (only %1$s / %1$d style placeholders are allowed)")
    if text.startswith(("@", "?")):
        problems.append("starts with @ or ? (must be escaped)")
    return problems


def load_strings(path: pathlib.Path) -> tuple[dict[str, str], dict[str, dict[str, str]], set[str]]:
    """Returns (strings, plurals, names that are marked translatable="false")."""
    strings: dict[str, str] = {}
    plurals: dict[str, dict[str, str]] = {}
    untranslatable: set[str] = set()
    root = parse_xml(path)
    if root is None:
        return strings, plurals, untranslatable
    for item in root:
        name = item.get("name")
        if item.tag == "string":
            if name in strings:
                error(f"{rel(path)}: string '{name}' is defined twice")
            strings[name] = "".join(item.itertext())
            if item.get("translatable") == "false":
                untranslatable.add(name)
        elif item.tag == "plurals":
            plurals[name] = {child.get("quantity"): "".join(child.itertext()) for child in item}
    for name, text in strings.items():
        for problem in string_problems(text):
            error(f"{rel(path)}: string '{name}': {problem}")
    for name, forms in plurals.items():
        for quantity, text in forms.items():
            for problem in string_problems(text):
                error(f"{rel(path)}: plurals '{name}' ({quantity}): {problem}")
    return strings, plurals, untranslatable


def check_translations() -> None:
    base_strings: dict[str, str] = {}
    base_plurals: dict[str, dict[str, str]] = {}
    untranslatable: set[str] = set()
    for file in sorted((RES / "values").glob("*.xml")):
        strings, plurals, skip = load_strings(file)
        base_strings.update(strings)
        base_plurals.update(plurals)
        untranslatable |= skip

    for folder in sorted(RES.glob("values-*")):
        for file in sorted(folder.glob("*.xml")):
            strings, plurals, _ = load_strings(file)
            if not strings and not plurals:
                continue
            full_translation = folder.name == "values-de"
            for name in strings:
                if name not in base_strings:
                    error(f"{rel(file)}: string '{name}' does not exist in values/")
                elif name in untranslatable:
                    error(f"{rel(file)}: string '{name}' is marked translatable=\"false\" in values/")
                elif set(PLACEHOLDER.findall(strings[name])) != set(PLACEHOLDER.findall(base_strings[name])):
                    error(f"{rel(file)}: string '{name}' has other placeholders than the English text")
            for name, forms in plurals.items():
                if name not in base_plurals:
                    error(f"{rel(file)}: plurals '{name}' does not exist in values/")
                    continue
                if "other" not in forms:
                    error(f"{rel(file)}: plurals '{name}' has no quantity=\"other\"")
            if full_translation:
                for name in base_strings:
                    if name not in strings and name not in untranslatable:
                        error(f"{rel(file)}: translation of string '{name}' is missing")
                for name in base_plurals:
                    if name not in plurals:
                        error(f"{rel(file)}: translation of plurals '{name}' is missing")


# -------------------------------------------------------------- vector paths

PATH_ARGS = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}
NUMBER = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")


def check_path_data(data: str) -> str | None:
    """Returns a problem description, or None when the path is syntactically valid."""
    position = 0
    command = None
    first = True
    length = len(data)
    while position < length:
        char = data[position]
        if char in " ,\t\r\n":
            position += 1
            continue
        if char.isalpha():
            if char.upper() not in PATH_ARGS:
                return f"unknown command '{char}' at offset {position}"
            if first and char.upper() != "M":
                return "path does not start with M"
            command = char
            first = False
            position += 1
            if PATH_ARGS[char.upper()] == 0:
                command = None
            continue
        if command is None:
            return f"number without a command at offset {position}"
        for _ in range(PATH_ARGS[command.upper()]):
            while position < length and data[position] in " ,\t\r\n":
                position += 1
            match = NUMBER.match(data, position)
            if not match:
                return f"command '{command}' needs {PATH_ARGS[command.upper()]} numbers (offset {position})"
            position = match.end()
        if command.upper() == "M":      # further pairs after a moveto are linetos
            command = "L" if command == "M" else "l"
    return None


def check_vectors() -> None:
    android = "{http://schemas.android.com/apk/res/android}"
    for file in sorted(RES.glob("drawable*/*.xml")):
        root = parse_xml(file)
        if root is None or root.tag != "vector":
            continue
        for path in root.iter("path"):
            data = path.get(android + "pathData")
            if not data:
                error(f"{rel(file)}: <path> without pathData")
                continue
            problem = check_path_data(data)
            if problem:
                error(f"{rel(file)}: invalid pathData: {problem}")


# ----------------------------------------------------------------- references

def check_references(resources: dict[str, set[str]]) -> None:
    used: dict[str, set[str]] = {}
    for src in SRC_DIRS:
        for file in sorted(src.rglob("*.kt")):
            text = file.read_text(encoding="utf-8")
            for kind, name in re.findall(r"\bR\.([a-z]+)\.([A-Za-z0-9_]+)", text):
                used.setdefault(kind, set()).add(name)
                if name not in resources.get(kind, set()):
                    error(f"{rel(file)}: R.{kind}.{name} does not exist in res/")
    xml_files = [MANIFEST] + sorted(RES.rglob("*.xml"))
    for file in xml_files:
        text = file.read_text(encoding="utf-8")
        for kind, name in re.findall(r"@([a-z]+)/([A-Za-z0-9_.]+)", text):
            if kind == "android":
                continue
            used.setdefault(kind, set()).add(name.replace(".", "_"))
            if name.replace(".", "_") not in resources.get(kind, set()):
                error(f"{rel(file)}: @{kind}/{name} does not exist in res/")
    for kind in ("string", "plurals", "drawable"):
        for name in sorted(resources.get(kind, set()) - used.get(kind, set())):
            warn(f"res: {kind} '{name}' is never used")


# --------------------------------------------------------------------- Kotlin

def strip_kotlin(source: str) -> str:
    """Removes comments and string contents, so that the code can be scanned with regexes."""
    out = []
    i, n = 0, len(source)
    while i < n:
        if source.startswith("//", i):
            j = source.find("\n", i)
            i = n if j < 0 else j
        elif source.startswith("/*", i):
            j = source.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif source.startswith('"""', i):
            j = source.find('"""', i + 3)
            i = n if j < 0 else j + 3
            out.append('""')
        elif source[i] == '"':
            i += 1
            while i < n and source[i] != '"':
                i += 2 if source[i] == "\\" else 1
            i += 1
            out.append('""')
        elif source[i] == "'":
            j = i + 1
            while j < n and source[j] != "'":
                j += 2 if source[j] == "\\" else 1
            i = j + 1
            out.append("' '")
        else:
            out.append(source[i])
            i += 1
    return "".join(out)


DECLARATION = re.compile(
    r"^(?:(?:private|internal|public|const|inline|data|enum|sealed|abstract|open|override|suspend|lateinit)\s+)*"
    r"(?:fun(?:\s+<[^>]+>)?|val|var|class|object|interface|typealias)\s+(?:[A-Za-z0-9_<>?, ]+\.)?([A-Za-z_][A-Za-z0-9_]*)",
    re.MULTILINE,
)


def check_kotlin() -> None:
    files = []
    for src in SRC_DIRS:
        files += sorted(src.rglob("*.kt"))
    parsed = []
    imported_anywhere: dict[str, str] = {}
    declared_in_package: dict[str, set[str]] = {}
    for file in files:
        source = file.read_text(encoding="utf-8")
        code = strip_kotlin(source)
        for opening, closing in ("{}", "()", "[]"):
            if code.count(opening) != code.count(closing):
                error(f"{rel(file)}: unbalanced {opening}{closing} ({code.count(opening)} vs {code.count(closing)})")
        package = re.search(r"^package\s+([\w.]+)", code, re.MULTILINE)
        package_name = package.group(1) if package else ""
        imports = {}
        for full in re.findall(r"^import\s+([\w.]+)", code, re.MULTILINE):
            imports[full.rsplit(".", 1)[-1]] = full
        body = re.sub(r"^(?:package|import)\s+[\w.]+.*$", "", code, flags=re.MULTILINE)
        declared = set(DECLARATION.findall(body))
        # also parameters, local names and enum constants declared in this file
        declared |= set(re.findall(r"\b(?:val|var)\s+([A-Za-z_]\w*)", body))
        declared |= set(re.findall(r"[(,]\s*([a-z_]\w*)\s*:", body))
        declared |= set(re.findall(r"\b([a-z_]\w*)\s*->", body))
        declared |= set(re.findall(r"\bfor\s*\(\s*([a-z_]\w*)\s+in\b", body))
        declared_in_package.setdefault(package_name, set()).update(set(DECLARATION.findall(body)))
        for name, full in imports.items():
            imported_anywhere.setdefault(name, full)
        parsed.append((file, package_name, imports, declared, body))

    for file, package_name, imports, declared, body in parsed:
        known = set(imports) | declared | declared_in_package.get(package_name, set())
        reported = set()
        for match in re.finditer(r"(?<![\w@])([A-Za-z_]\w*)", body):
            name = match.group(1)
            if name in known or name in reported or name not in imported_anywhere:
                continue
            origin = imported_anywhere[name]
            before = body[: match.start()].rstrip()
            after = body[match.end():].lstrip()
            is_member_access = before.endswith(".")
            is_named_argument = after.startswith("=") and not after.startswith("==")
            if is_named_argument:
                continue
            # After a dot only extensions need an import: lower-case names imported from
            # androidx / kotlinx elsewhere in the project that are called like a function
            # (".padding(") or are one of the unit extension properties (".dp").
            if is_member_access:
                is_call = after.startswith("(")
                is_extension = name[0].islower() and origin.startswith(("androidx.", "kotlinx."))
                if not (is_extension and (is_call or name in ("dp", "sp"))):
                    continue
            reported.add(name)
            kind = "extension call" if is_member_access else "name"
            warn(f"{rel(file)}: {kind} '{name}' is used but not imported here (imported elsewhere from {origin})")


# -------------------------------------------------------------------- listing

LIMITS = {"title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000}


def check_listing() -> None:
    if not LISTING.exists():
        warn("play-store/listing does not exist yet")
        return
    for locale in sorted(p for p in LISTING.iterdir() if p.is_dir()):
        for name, limit in LIMITS.items():
            file = locale / name
            if not file.exists():
                error(f"{rel(file)} is missing")
                continue
            text = file.read_text(encoding="utf-8").strip()
            if len(text) > limit:
                error(f"{rel(file)}: {len(text)} characters, the limit is {limit}")
            else:
                print(f"  {rel(file)}: {len(text)}/{limit} characters")


def check_privacy_policy() -> None:
    page = ROOT / "docs" / "privacy-policy.html"
    if not page.exists():
        error("docs/privacy-policy.html is missing")
        return
    text = page.read_text(encoding="utf-8")
    body = text.split("</head>", 1)[-1]
    placeholders = sorted(set(re.findall(r"\[\[[A-Z_]+\]\]", body)))
    if placeholders:
        warn("docs/privacy-policy.html still contains placeholders: " + ", ".join(placeholders)
             + " - fill them in before publishing the page")
    config = (RES / "values" / "config.xml").read_text(encoding="utf-8")
    url = re.search(r'name="privacy_policy_url"[^>]*>([^<]+)<', config)
    if not url or not url.group(1).startswith("https://"):
        error("values/config.xml: privacy_policy_url must be an https:// address")


def main() -> int:
    print("Checking resources ...")
    resources = collect_resources()
    check_translations()
    check_vectors()
    check_references(resources)
    print("Checking Kotlin sources ...")
    check_kotlin()
    print("Checking Play Store listing ...")
    check_listing()
    check_privacy_policy()
    for message in warnings:
        print("WARNING", message)
    for message in errors:
        print("ERROR  ", message)
    print(f"{len(errors)} error(s), {len(warnings)} warning(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
