from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DOCS_DIR = ROOT / "docs"
DATA_PATH = DOCS_DIR / "data" / "midjourney.json"


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def load_ids(args: argparse.Namespace) -> set[str]:
    ids: set[str] = set()
    if args.ids:
        for value in args.ids:
            ids.update(part.strip() for part in value.split(",") if part.strip())
    if args.file:
        payload = json.loads(Path(args.file).read_text(encoding="utf-8"))
        if isinstance(payload, list):
            for item in payload:
                if isinstance(item, str):
                    ids.add(item)
                elif isinstance(item, dict) and item.get("id"):
                    ids.add(str(item["id"]))
        elif isinstance(payload, dict):
            for item in payload.get("items", payload.get("ids", [])):
                if isinstance(item, str):
                    ids.add(item)
                elif isinstance(item, dict) and item.get("id"):
                    ids.add(str(item["id"]))
    return ids


def update_counts(archive: dict[str, Any]) -> None:
    counts = {"images": 0, "videos": 0, "styles": 0, "total": 0}
    for item in archive.get("items", []):
        tab = item.get("tab")
        if tab in counts:
            counts[tab] += 1
            counts["total"] += 1
    archive["counts"] = counts


def media_path(item: dict[str, Any]) -> Path | None:
    value = item.get("asset_path")
    if not value or str(value).startswith(("http://", "https://")):
        return None
    return DOCS_DIR.joinpath(*str(value).split("/"))


def main() -> int:
    parser = argparse.ArgumentParser(description="Apply queued Midjourney archive deletions.")
    parser.add_argument("--ids", action="append", help="Comma-separated item ids to delete.")
    parser.add_argument("--file", help="JSON copied from the homepage Copy deletes button.")
    args = parser.parse_args()

    ids = load_ids(args)
    if not ids:
        print("No deletion ids provided.")
        return 1

    archive = json.loads(DATA_PATH.read_text(encoding="utf-8"))
    kept = []
    removed = []
    for item in archive.get("items", []):
        if item.get("id") in ids:
            removed.append(item)
        else:
            kept.append(item)

    if not removed:
        print("No matching archive items found.")
        return 0

    deleted_files = 0
    for item in removed:
        path = media_path(item)
        if path and path.exists() and path.is_file():
            path.unlink()
            deleted_files += 1

    archive["items"] = kept
    archive["updated_at"] = utc_now()
    update_counts(archive)
    DATA_PATH.write_text(json.dumps(archive, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"deleted_items={len(removed)}")
    print(f"deleted_files={deleted_files}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
