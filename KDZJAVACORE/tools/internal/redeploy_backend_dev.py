#!/usr/bin/env python3
"""redeploy_backend_dev.py - Deploy backend in DEV mode (NO blue-green)."""
import subprocess
import sys
from pathlib import Path


def run_command(cmd: list, cwd: Path) -> int:
    """Run a shell command and return exit code."""
    result = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"ERROR: Command failed: {' '.join(cmd)}")
        print(result.stderr)
    return result.returncode


def main():
    print("=" * 50)
    print("Redeploying Backend (DEV)")
    print("=" * 50)
    print("Environment: DEV (localhost)")
    print("Blue-Green: DISABLED")
    print("Database: PostgreSQL (localhost)")
    print()

    script_dir = Path(__file__).parent.resolve()
    project_dir = script_dir.parent.parent / "generic_backend"
    profile = "dev"

    # Set environment
    env = {"SPRING_PROFILES_ACTIVE": profile}
    print(f"Step 1: Building application with '{profile}' profile...")

    # Build
    result = run_command(["mvn", "clean", "package", "-DskipTests", "-q"], project_dir)
    if result != 0:
        print("Build failed!")
        sys.exit(1)

    print()
    print("Step 2: Database migrations (automatic on startup)")
    print()

    print("Step 3: Starting application on localhost")
    print("Note: Blue-green deployment is DISABLED in dev mode")
    print()

    print("Dev deployment ready!")
    print("Run: java -jar target/*.jar --spring.profiles.active=dev")
    print("Application will start on port 8080")

    sys.exit(0)


if __name__ == "__main__":
    main()
