#!/usr/bin/env python3
"""
Bundle the question bank and lessons into a single versioned file for Pages.

The app fetches this instead of relying only on the copies baked into the APK,
so adding or editing questions reaches phones without anyone installing anything.

Two files are written:
  docs/content-index.json  tiny; the app polls this to see if anything changed
  docs/content.json        the full bundle, fetched only when the version moved

Version is the number of commits that touched the assets directory, so it rises
monotonically and only when content actually changed. Falls back to a date-based
number outside a git checkout.

Usage:  python scripts/build_content_bundle.py [--out-dir docs]
"""

import argparse
import datetime as dt
import glob
import hashlib
import json
import os
import subprocess
import sys

ASSETS = os.path.join("app", "src", "main", "assets")


def content_version(paths):
    """
    Commits that touched the card and lesson files, so the version only moves when
    content actually moves.

    Deliberately scoped to those paths rather than all of assets/: CI writes
    content-version.txt and news.json into assets/ too, and counting those would bump
    the version on every build and republish an unchanged bundle forever.
    """
    try:
        out = subprocess.run(
            ["git", "rev-list", "--count", "HEAD", "--"] + paths,
            capture_output=True, text=True, timeout=30, check=True,
        )
        n = int(out.stdout.strip())
        if n > 0:
            return n
    except (subprocess.SubprocessError, ValueError, OSError):
        pass
    # No git history available; degrade to something still monotonic.
    epoch = dt.date(2026, 1, 1)
    return 1000 + (dt.date.today() - epoch).days


def content_date(paths):
    """
    Date the content last actually changed, taken from git rather than the clock.

    Using today's date would give the bundle different bytes on every build, so CI
    would commit and republish an identical question bank each time and the digest
    would churn for no reason.
    """
    try:
        out = subprocess.run(
            ["git", "log", "-1", "--format=%cs", "--"] + paths,
            capture_output=True, text=True, timeout=30, check=True,
        )
        stamp = out.stdout.strip()
        if len(stamp) == 10:
            return stamp
    except (subprocess.SubprocessError, OSError):
        pass
    return dt.date.today().isoformat()


def load(path):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out-dir", default="docs")
    args = ap.parse_args()

    card_files = sorted(glob.glob(os.path.join(ASSETS, "cards_*.json")))
    if not card_files:
        print("ERROR: no cards_*.json found", file=sys.stderr)
        return 1

    topics = []
    total_cards = 0
    for path in card_files:
        data = load(path)
        topics.append(data)
        total_cards += len(data.get("cards", []))

    flash_files = sorted(glob.glob(os.path.join(ASSETS, "flash_*.json")))
    flashcards = []
    total_flash = 0
    for path in flash_files:
        data = load(path)
        flashcards.append(data)
        total_flash += len(data.get("cards", []))

    lessons_path = os.path.join(ASSETS, "lessons.json")
    lessons = load(lessons_path).get("lessons", [])

    if total_cards == 0 or not lessons:
        print("ERROR: refusing to publish an empty bundle", file=sys.stderr)
        return 1

    content_paths = card_files + flash_files + [lessons_path]
    version = content_version(content_paths)

    bundle = {
        "contentVersion": version,
        "generated": content_date(content_paths),
        "cardCount": total_cards,
        "topicCount": len(topics),
        "flashCount": total_flash,
        "topics": topics,
        "flashcards": flashcards,
        "lessons": lessons,
    }

    os.makedirs(args.out_dir, exist_ok=True)
    bundle_path = os.path.join(args.out_dir, "content.json")

    # Compact, and sorted so an unchanged bundle produces an identical file and the
    # workflow does not create empty commits.
    #
    # Written and hashed as the same bytes, in binary mode. Hashing the payload but
    # writing payload + newline made the published digest disagree with the served
    # file, so the app rejected every update; text mode on Windows would break it
    # again by translating the newline.
    payload = json.dumps(
        bundle, ensure_ascii=False, separators=(",", ":"), sort_keys=True
    ) + "\n"
    data = payload.encode("utf-8")

    with open(bundle_path, "wb") as fh:
        fh.write(data)

    digest = hashlib.sha256(data).hexdigest()

    index = {
        "contentVersion": version,
        "generated": bundle["generated"],
        "cardCount": total_cards,
        "topicCount": len(topics),
        "lessonCount": len(lessons),
        "flashCount": total_flash,
        "bytes": len(data),
        "sha256": digest,
        "url": "https://iknalos.github.io/AI-Interview-Prep/content.json",
    }
    with open(os.path.join(args.out_dir, "content-index.json"), "w", encoding="utf-8") as fh:
        json.dump(index, fh, indent=1, ensure_ascii=False)
        fh.write("\n")

    print(f"content v{version}: {total_cards} cards, {total_flash} flashcards, "
          f"{len(topics)} topics, {len(lessons)} lessons, {len(payload) / 1024:.0f} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
