#!/usr/bin/env python3
"""Build script for LunarWrath.
Compiles all Java source files under src/ and packages them into LunarWrath.jar.
"""
import glob
import os
import shutil
import subprocess
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC_DIR = os.path.join(ROOT, "src")
BUILD_DIR = os.path.join(ROOT, "build")
CLASSES_DIR = os.path.join(BUILD_DIR, "classes")
OUT_JAR = os.path.join(ROOT, "LunarWrath.jar")

# Discover javac
javac = shutil.which("javac")
if not javac:
    jdk_candidates = glob.glob(r"C:\Users\*\.jdks\**\bin\javac.exe", recursive=True)
    if jdk_candidates:
        javac = jdk_candidates[0]
if not javac:
    javac = "javac"

# Discover paper-api and PlaceholderAPI dependencies
gradle_jars = glob.glob(r"C:\Users\*\.gradle\caches\modules-2\files-2.1\**\*.jar", recursive=True)
libs = glob.glob(os.path.join(ROOT, "libs", "*.jar")) + glob.glob(os.path.join(ROOT, "deps", "*.jar"))
cp = os.pathsep.join(gradle_jars + libs)

sources = glob.glob(os.path.join(SRC_DIR, "**", "*.java"), recursive=True)
print(f"Compiling {len(sources)} java source files...")

os.makedirs(CLASSES_DIR, exist_ok=True)
cmd = [javac, "-cp", cp, "-d", CLASSES_DIR, "--release", "21", "-encoding", "UTF-8"] + sources
res = subprocess.run(cmd, capture_output=True, text=True)
if res.returncode != 0:
    print("Compilation FAILED:")
    print(res.stderr)
    exit(1)
print("Compilation SUCCESSFUL!")

# Package JAR
if os.path.exists(OUT_JAR):
    os.remove(OUT_JAR)

with zipfile.ZipFile(OUT_JAR, "w", zipfile.ZIP_DEFLATED) as z:
    manifest_path = os.path.join(SRC_DIR, "META-INF", "MANIFEST.MF")
    if os.path.exists(manifest_path):
        z.write(manifest_path, "META-INF/MANIFEST.MF")

    for root, dirs, files in os.walk(CLASSES_DIR):
        for file in files:
            full_path = os.path.join(root, file)
            rel_path = os.path.relpath(full_path, CLASSES_DIR).replace("\\", "/")
            z.write(full_path, rel_path)

    for item in ["plugin.yml", "config.yml", "bosses.yml", "items.yml", "loot.yml"]:
        item_path = os.path.join(SRC_DIR, item)
        if os.path.exists(item_path):
            z.write(item_path, item)

    for sub in ["locales", "examples", "worlds-template"]:
        sub_dir = os.path.join(SRC_DIR, sub)
        if os.path.exists(sub_dir):
            for root, dirs, files in os.walk(sub_dir):
                for file in files:
                    full_path = os.path.join(root, file)
                    rel_path = os.path.relpath(full_path, SRC_DIR).replace("\\", "/")
                    z.write(full_path, rel_path)

print(f"JAR successfully packaged: {OUT_JAR} ({os.path.getsize(OUT_JAR)} bytes)")
