#!/usr/bin/env python3
"""Safely distribute versioned AIBI artifacts to registered host apps."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
CONSUMERS_DIR = PROJECT_ROOT / "consumers"
VERSION_FILE = PROJECT_ROOT / "aibi-version.json"


class SyncError(RuntimeError):
    pass


def read_json(path: Path) -> dict[str, Any]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise SyncError(f"missing file: {path}") from error
    except json.JSONDecodeError as error:
        raise SyncError(f"invalid JSON: {path}: {error}") from error


def sha256(path: Path) -> str | None:
    if not path.is_file():
        return None
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def inside(root: Path, relative: str, label: str) -> Path:
    candidate = (root / relative).resolve()
    try:
        candidate.relative_to(root.resolve())
    except ValueError as error:
        raise SyncError(f"{label} escapes its root: {relative}") from error
    return candidate


def repository_root(relative: str) -> Path:
    candidate = (PROJECT_ROOT / relative).resolve()
    workspace_root = PROJECT_ROOT.parent.resolve()
    try:
        candidate.relative_to(workspace_root)
    except ValueError as error:
        raise SyncError(f"repository root escapes the workspace: {relative}") from error
    if candidate == workspace_root:
        raise SyncError(f"repository root is too broad: {relative}")
    return candidate


def load_consumer(name: str) -> dict[str, Any]:
    manifest = inside(CONSUMERS_DIR, f"{name}.json", "consumer manifest")
    data = read_json(manifest)
    if data.get("schemaVersion") != 1 or data.get("consumer") != name:
        raise SyncError(f"unsupported or mismatched consumer manifest: {manifest}")
    return data


def load_lock(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {"schemaVersion": 1, "repositories": {}}
    lock = read_json(path)
    if lock.get("schemaVersion") != 1:
        raise SyncError(f"unsupported lock schema: {path}")
    return lock


def atomic_copy(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=".aibi-sync-", dir=destination.parent)
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        shutil.copyfile(source, temporary)
        os.replace(temporary, destination)
    finally:
        if temporary.exists():
            temporary.unlink()


def atomic_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=".aibi-lock-", dir=path.parent)
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        os.replace(temporary, path)
    finally:
        if temporary.exists():
            temporary.unlink()


def classify(source_hash: str, destination_hash: str | None, installed_hash: str | None) -> str:
    if destination_hash is None:
        return "missing"
    if destination_hash == source_hash:
        return "current"
    if installed_hash is None:
        return "untracked-conflict"
    if destination_hash == installed_hash:
        return "update-available"
    if source_hash == installed_hash:
        return "local-modified"
    return "diverged-conflict"


def repository_entries(manifest: dict[str, Any]):
    for repository in manifest.get("repositories", []):
        repo_root = repository_root(repository["root"])
        lock_path = inside(repo_root, repository.get("lockFile", ".aibi-lock.json"), "lock file")
        lock = load_lock(lock_path)
        locked_files = lock.get("repositories", {}).get(repository["id"], {}).get("files", {})
        yield repository, repo_root, lock_path, lock, locked_files


def status(name: str) -> tuple[list[dict[str, str]], bool]:
    manifest = load_consumer(name)
    rows: list[dict[str, str]] = []
    healthy = True
    for repository, repo_root, _, _, locked_files in repository_entries(manifest):
        if not repo_root.is_dir():
            rows.append({"repository": repository["id"], "file": "-", "state": "repository-missing"})
            healthy = False
            continue
        for mapping in repository.get("files", []):
            source = inside(PROJECT_ROOT, mapping["source"], "source")
            destination = inside(repo_root, mapping["destination"], "destination")
            source_hash = sha256(source)
            if source_hash is None:
                raise SyncError(f"source is missing: {source}")
            installed_hash = locked_files.get(mapping["destination"], {}).get("sha256")
            state = classify(source_hash, sha256(destination), installed_hash)
            if state != "current":
                healthy = False
            rows.append({"repository": repository["id"], "file": mapping["destination"], "state": state})
    return rows, healthy


def apply(name: str) -> list[dict[str, str]]:
    manifest = load_consumer(name)
    version = read_json(VERSION_FILE)["version"]
    results: list[dict[str, str]] = []
    for repository, repo_root, lock_path, lock, locked_files in repository_entries(manifest):
        if not repo_root.is_dir():
            raise SyncError(f"repository is missing: {repo_root}")
        next_files: dict[str, dict[str, str]] = {}
        planned: list[tuple[Path, Path, str, str]] = []
        for mapping in repository.get("files", []):
            source = inside(PROJECT_ROOT, mapping["source"], "source")
            destination = inside(repo_root, mapping["destination"], "destination")
            source_hash = sha256(source)
            if source_hash is None:
                raise SyncError(f"source is missing: {source}")
            destination_hash = sha256(destination)
            installed_hash = locked_files.get(mapping["destination"], {}).get("sha256")
            state = classify(source_hash, destination_hash, installed_hash)
            if state in {"untracked-conflict", "local-modified", "diverged-conflict"}:
                raise SyncError(
                    f"refusing to overwrite {repository['id']}:{mapping['destination']} ({state}); "
                    "preserve or reconcile the app-local change first"
                )
            planned.append((source, destination, source_hash, state))

        for source, destination, source_hash, state in planned:
            if state != "current":
                atomic_copy(source, destination)
            next_files[str(destination.relative_to(repo_root))] = {
                "sha256": source_hash,
                "source": str(source.relative_to(PROJECT_ROOT)),
            }
            results.append({
                "repository": repository["id"],
                "file": str(destination.relative_to(repo_root)),
                "state": "installed" if state != "current" else "recorded",
            })

        repositories = lock.setdefault("repositories", {})
        repositories[repository["id"]] = {"version": version, "files": next_files}
        lock["consumer"] = name
        lock["aibiVersion"] = version
        atomic_json(lock_path, lock)
    return results


def print_rows(rows: list[dict[str, str]]) -> None:
    for row in rows:
        print(f"{row['repository']}: {row['state']}: {row['file']}")


def consumer_names(requested: str) -> list[str]:
    if requested != "all":
        return [requested]
    return sorted(path.stem for path in CONSUMERS_DIR.glob("*.json"))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("status", "check", "apply"))
    parser.add_argument("consumer", help="consumer name, for example starmanager, or all")
    arguments = parser.parse_args()
    try:
        names = consumer_names(arguments.consumer)
        if not names:
            raise SyncError("no consumer manifests found")
        if arguments.command == "apply":
            if arguments.consumer == "all":
                unsafe = {"untracked-conflict", "local-modified", "diverged-conflict", "repository-missing"}
                preflight = [row for name in names for row in status(name)[0]]
                blocked = [row for row in preflight if row["state"] in unsafe]
                if blocked:
                    print_rows(blocked)
                    raise SyncError("all-consumer preflight found app-local conflicts")
            print_rows([row for name in names for row in apply(name)])
            return 0
        collected = [status(name) for name in names]
        rows = [row for consumer_rows, _ in collected for row in consumer_rows]
        healthy = all(is_healthy for _, is_healthy in collected)
        print_rows(rows)
        if arguments.command == "check" and not healthy:
            return 1
        return 0
    except SyncError as error:
        print(f"aibi-sync: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
