#!/usr/bin/env python3
"""Consistency checks run by CI before a build or release."""
import argparse
import glob
import os
import re
import sys

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CYR = re.compile("[А-Яа-яЁё]")
PH = re.compile(r"\{[\w-]+\}")


def load(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as f:
        return yaml.safe_load(f)


def flat(d, pre=""):
    out = {}
    for k, v in (d or {}).items():
        if isinstance(v, dict):
            out.update(flat(v, f"{pre}{k}."))
        else:
            out[f"{pre}{k}"] = v
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag")
    a = ap.parse_args()
    errors = []

    for f in glob.glob(os.path.join(ROOT, "**", "*.yml"), recursive=True):
        if os.sep + "build" + os.sep in f:
            continue
        try:
            with open(f, encoding="utf-8") as fh:
                yaml.safe_load(fh)
        except yaml.YAMLError as e:
            errors.append(f"invalid YAML {os.path.relpath(f, ROOT)}: {e}")

    version = str(load("src/plugin.yml")["version"])
    changelog = open(os.path.join(ROOT, "CHANGELOG.md"), encoding="utf-8").read()
    if not re.search(r"^## " + re.escape(version) + r" \(", changelog, re.M):
        errors.append(f"CHANGELOG.md has no section for {version}")
    if a.tag and a.tag.lstrip("v") != version:
        errors.append(f"tag {a.tag} does not match plugin.yml version {version}")

    en = flat(load("src/locales/en.yml"))
    for lang in ("ru",):
        other = flat(load(f"src/locales/{lang}.yml"))
        for k in sorted(set(en) - set(other)):
            errors.append(f"{lang}.yml is missing {k}")
        for k in sorted(set(other) - set(en)):
            errors.append(f"en.yml is missing {k} (present in {lang}.yml)")
        for k in sorted(set(en) & set(other)):
            if set(PH.findall(str(en[k]))) != set(PH.findall(str(other[k]))):
                errors.append(f"placeholders differ for {k} between en.yml and {lang}.yml")

    base = flat(load("src/config.yml"))
    for preset in glob.glob(os.path.join(ROOT, "presets", "*")):
        p = os.path.join(preset, "config.yml")
        if os.path.exists(p):
            with open(p, encoding="utf-8") as fh:
                pk = flat(yaml.safe_load(fh))
            missing = [k for k in base if k not in pk and not k.startswith("schedule.days.")]
            for k in missing:
                errors.append(f"{os.path.relpath(p, ROOT)} is missing {k}")

    english = [f for f in glob.glob(os.path.join(ROOT, "src", "**", "*.yml"), recursive=True)
               if os.path.basename(f) not in ("ru.yml", "es.yml")]
    for f in english:
        for i, line in enumerate(open(f, encoding="utf-8"), 1):
            if CYR.search(line):
                errors.append(f"Russian text in English default {os.path.relpath(f, ROOT)}:{i}")

    if errors:
        print("\n".join(errors))
        sys.exit(1)
    print(f"checks ok (version {version})")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    main()
