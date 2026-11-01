"""Validate release versions before signing or publishing anything."""

import re
import subprocess
from pathlib import Path


def parse_version(source):
    name = re.search(r'^\s*versionName\s*=\s*"([^"]+)"\s*(?://.*)?$', source, re.MULTILINE)
    code = re.search(r'^\s*versionCode\s*=\s*([0-9]+)\s*(?://.*)?$', source, re.MULTILINE)
    if not name or not code or not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", name[1]) or int(code[1]) < 1:
        raise ValueError("Use a versionName like 0.1.31 and a positive integer versionCode in app/build.gradle.kts")
    return name[1], int(code[1])


def release_version():
    version, code = parse_version(Path("app/build.gradle.kts").read_text())
    previous = subprocess.run(
        ["git", "describe", "--tags", "--abbrev=0", "--match=v[0-9]*", "HEAD^"],
        capture_output=True, text=True,
    )
    if previous.returncode == 0:
        source = subprocess.check_output(["git", "show", previous.stdout.strip() + ":app/build.gradle.kts"], text=True)
        old_version, old_code = parse_version(source)
        if tuple(map(int, version.split("."))) <= tuple(map(int, old_version.split("."))) or code <= old_code:
            raise ValueError(f"Increase both versionName and versionCode beyond {old_version} / {old_code} before releasing")
    tag = "v" + version
    existing = subprocess.run(["git", "rev-parse", "--verify", "--quiet", f"refs/tags/{tag}^{{commit}}"], capture_output=True, text=True)
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    if existing.returncode == 0 and existing.stdout.strip() != head:
        raise ValueError(f"Tag {tag} already belongs to another commit; choose a new version")
    return version, code


if __name__ == "__main__":
    try:
        version, code = release_version()
    except (ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error)) from error
    print(f"version={version}\nversion_code={code}\ntag=v{version}")
