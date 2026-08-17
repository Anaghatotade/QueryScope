#!/usr/bin/env python3
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def main() -> int:
    print("==> QueryLens local setup (no Docker/Java needed)\n")

    venv = ROOT / ".venv"
    if not venv.exists():
        print("Creating virtual environment...")
        subprocess.check_call([sys.executable, "-m", "venv", str(venv)])

    pip = venv / "bin" / "pip"
    python = venv / "bin" / "python"
    if not pip.exists():
        pip = venv / "Scripts" / "pip.exe"
        python = venv / "Scripts" / "python.exe"

    print("Installing dependencies...")
    subprocess.check_call([str(pip), "install", "-q", "-r", str(ROOT / "requirements.txt")])

    print("\nStarting QueryLens at http://localhost:3000\n")
    subprocess.check_call([str(python), str(ROOT / "run.py")])


if __name__ == "__main__":
    raise SystemExit(main())
