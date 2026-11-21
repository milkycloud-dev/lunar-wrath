#!/usr/bin/env python3
"""Deploy a LunarWrath build to a server over SFTP and reload it.

Used by the release workflow when the repository variable DEPLOY_ENABLED is "true".
Everything server-specific comes from the environment:

  DEPLOY_SFTP_HOST, DEPLOY_SFTP_PORT, DEPLOY_SFTP_USER   SFTP login
  DEPLOY_SFTP_KEY                                        private key (file contents)
  DEPLOY_JAR_PATH      remote path of the plugin jar, e.g. plugins/LunarWrath.jar
  DEPLOY_DATA_DIR      remote plugin data folder, e.g. plugins/BloodMoonPlus
  DEPLOY_PRESET        optional: presets/<name> is uploaded over the data folder
  LINKGATE_URL, LINKGATE_CONSOLE_SECRET   optional: reload through a LinkGate remote console
  DEPLOY_RELOAD_COMMAND                   console command, default "plugman reload BloodMoonPlus"

Every replaced remote file is kept as <file>.bak-<UTC time>.
"""
import argparse
import glob
import io
import json
import os
import sys
import time
import urllib.request

import paramiko

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def env(name, required=True, default=None):
    v = os.environ.get(name, default)
    if required and not v:
        sys.exit(f"{name} is not set")
    return v


def load_key(text):
    for cls in (paramiko.Ed25519Key, paramiko.ECDSAKey, paramiko.RSAKey):
        try:
            return cls.from_private_key(io.StringIO(text))
        except Exception:
            continue
    sys.exit("DEPLOY_SFTP_KEY is not a supported private key")


def ensure_dir(sftp, path):
    parts = [p for p in path.split("/") if p]
    cur = ""
    for p in parts:
        cur = f"{cur}/{p}" if cur else p
        try:
            sftp.stat(cur)
        except FileNotFoundError:
            sftp.mkdir(cur)


def put(sftp, local, remote, stamp):
    data = open(local, "rb").read()
    try:
        sftp.stat(remote)
        with sftp.open(remote, "rb") as old, sftp.open(f"{remote}.bak-{stamp}", "wb") as bak:
            bak.write(old.read())
    except FileNotFoundError:
        ensure_dir(sftp, os.path.dirname(remote))
    with sftp.open(remote, "wb") as f:
        f.write(data)
    size = sftp.stat(remote).st_size
    if size != len(data):
        sys.exit(f"size mismatch for {remote}: {size} != {len(data)}")
    print(f"uploaded {os.path.relpath(local, ROOT)} -> {remote} ({size} bytes)")


def console(method, path, body=None):
    req = urllib.request.Request(
        env("LINKGATE_URL").rstrip("/") + path,
        data=json.dumps(body).encode() if body is not None else None,
        method=method,
        headers={"Authorization": "Bearer " + env("LINKGATE_CONSOLE_SECRET"),
                 "Content-Type": "application/json", "User-Agent": "LunarWrath-Deploy/1.0"})
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode())


def reload_and_check(version):
    after = (console("GET", "/console/log?after=-1&limit=1")["lines"] or [{"id": 0}])[-1]["id"]
    cmd = env("DEPLOY_RELOAD_COMMAND", required=False, default="plugman reload BloodMoonPlus")
    cid = console("POST", "/console/send", {"cmd": cmd, "by": "deploy"})["id"]
    deadline = time.time() + 40
    while time.time() < deadline and not console("GET", f"/console/cmd?id={cid}").get("done_at"):
        time.sleep(1)
    lines, end = [], time.time() + 15
    while time.time() < end:
        got = console("GET", f"/console/log?after={after}&limit=500")["lines"]
        if got:
            after = got[-1]["id"]
            lines += [r["line"] for r in got]
        time.sleep(1.5)
    text = "\n".join(lines)
    print(text)
    if f"Enabling BloodMoonPlus v{version}" not in text:
        sys.exit(f"reload check failed: 'Enabling BloodMoonPlus v{version}' not in the log")
    bad = [l for l in lines if ("Exception" in l or "ERROR" in l) and ("BloodMoonPlus" in l or "bloodmoonplus" in l)]
    if bad:
        sys.exit("reload logged errors:\n" + "\n".join(bad))
    print(f"reload ok: BloodMoonPlus v{version} enabled")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", required=True)
    ap.add_argument("--jar", default=os.path.join(ROOT, "LunarWrath.jar"))
    a = ap.parse_args()

    files = [(a.jar, env("DEPLOY_JAR_PATH"))]
    data_dir = env("DEPLOY_DATA_DIR").rstrip("/")
    for f in sorted(glob.glob(os.path.join(ROOT, "src", "locales", "*.yml"))):
        if os.path.basename(f) != "locales.yml":
            files.append((f, f"{data_dir}/locales/{os.path.basename(f)}"))
    preset = env("DEPLOY_PRESET", required=False)
    if preset:
        base = os.path.join(ROOT, "presets", preset)
        if not os.path.isdir(base):
            sys.exit(f"preset {preset} not found")
        for f in sorted(glob.glob(os.path.join(base, "**", "*.yml"), recursive=True)):
            files.append((f, f"{data_dir}/{os.path.relpath(f, base).replace(os.sep, '/')}"))

    t = paramiko.Transport((env("DEPLOY_SFTP_HOST"), int(env("DEPLOY_SFTP_PORT", required=False, default="22"))))
    t.connect(username=env("DEPLOY_SFTP_USER"), pkey=load_key(env("DEPLOY_SFTP_KEY")))
    sftp = paramiko.SFTPClient.from_transport(t)
    stamp = time.strftime("%Y%m%d%H%M%S", time.gmtime())
    try:
        for local, remote in files:
            put(sftp, local, remote, stamp)
    finally:
        sftp.close()
        t.close()

    if os.environ.get("LINKGATE_URL") and os.environ.get("LINKGATE_CONSOLE_SECRET"):
        reload_and_check(a.version)
    else:
        print("no console configured: restart or reload the server to load the new version")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    main()
