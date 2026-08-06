#!/usr/bin/env python3
"""
Validate the bundled question bank and lessons before a build.

A malformed card would only surface as a crash on the user's phone, so this runs
in CI as a gate. Also enforces that every topic with cards has a lesson, since
the app's topic ordering is driven by lessons.json.
"""

import glob
import json
import os
import sys

ASSETS = os.path.join("app", "src", "main", "assets")
DIFFICULTIES = {"easy", "medium", "hard"}
REQUIRED_CARD_FIELDS = ("id", "difficulty", "question", "options", "answer",
                        "explanation", "modelAnswer")

errors = []
warnings = []


def err(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


def load(path):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def validate_cards():
    files = sorted(glob.glob(os.path.join(ASSETS, "cards_*.json")))
    if not files:
        err("no cards_*.json files found in assets")
        return {}, 0

    seen_ids = {}
    topics = {}
    total = 0

    for path in files:
        name = os.path.basename(path)
        try:
            data = load(path)
        except json.JSONDecodeError as exc:
            err(f"{name}: invalid JSON: {exc}")
            continue

        for key in ("topicId", "topicName", "cards"):
            if key not in data:
                err(f"{name}: missing top-level key '{key}'")
        if "cards" not in data:
            continue

        topic_id = data.get("topicId", "?")
        if topic_id in topics:
            err(f"{name}: topicId '{topic_id}' already used by {topics[topic_id]}")
        topics[topic_id] = name

        for i, card in enumerate(data["cards"]):
            where = f"{name}[{i}]"
            for field in REQUIRED_CARD_FIELDS:
                if field not in card:
                    err(f"{where}: missing field '{field}'")
            if not all(f in card for f in REQUIRED_CARD_FIELDS):
                continue

            cid = card["id"]
            where = f"{name}:{cid}"
            if cid in seen_ids:
                err(f"{where}: duplicate id, also in {seen_ids[cid]}")
            seen_ids[cid] = name

            if card["difficulty"] not in DIFFICULTIES:
                err(f"{where}: difficulty '{card['difficulty']}' not one of {sorted(DIFFICULTIES)}")

            opts = card["options"]
            if not isinstance(opts, list) or len(opts) != 4:
                err(f"{where}: expected exactly 4 options, got "
                    f"{len(opts) if isinstance(opts, list) else type(opts).__name__}")
            elif len(set(o.strip() for o in opts)) != 4:
                err(f"{where}: duplicate option text")
            elif any(not str(o).strip() for o in opts):
                err(f"{where}: blank option")

            ans = card["answer"]
            if not isinstance(ans, int) or not 0 <= ans <= 3:
                err(f"{where}: answer must be an int 0-3, got {ans!r}")

            for field in ("question", "explanation", "modelAnswer"):
                if not str(card[field]).strip():
                    err(f"{where}: '{field}' is empty")

            # Quality guards: these caught real drafting mistakes.
            if len(str(card["explanation"]).split()) < 15:
                warn(f"{where}: explanation looks thin "
                     f"({len(str(card['explanation']).split())} words)")
            if len(str(card["modelAnswer"]).split()) < 25:
                warn(f"{where}: modelAnswer looks thin "
                     f"({len(str(card['modelAnswer']).split())} words)")

            total += 1

    return topics, total


def validate_lessons(card_topics):
    path = os.path.join(ASSETS, "lessons.json")
    if not os.path.exists(path):
        err("lessons.json missing")
        return 0
    try:
        data = load(path)
    except json.JSONDecodeError as exc:
        err(f"lessons.json: invalid JSON: {exc}")
        return 0

    lessons = data.get("lessons")
    if not isinstance(lessons, list) or not lessons:
        err("lessons.json: 'lessons' must be a non-empty list")
        return 0

    lesson_topics = set()
    for i, lesson in enumerate(lessons):
        where = f"lessons[{i}]"
        for key in ("topicId", "title", "subtitle", "sections"):
            if key not in lesson:
                err(f"{where}: missing '{key}'")
        if "topicId" not in lesson or "sections" not in lesson:
            continue
        tid = lesson["topicId"]
        if tid in lesson_topics:
            err(f"lessons.json: duplicate topicId '{tid}'")
        lesson_topics.add(tid)
        if not isinstance(lesson["sections"], list) or not lesson["sections"]:
            err(f"lessons.json:{tid}: sections must be a non-empty list")
            continue
        for j, section in enumerate(lesson["sections"]):
            if not str(section.get("heading", "")).strip():
                err(f"lessons.json:{tid}: section {j} has no heading")
            if len(str(section.get("body", "")).split()) < 30:
                warn(f"lessons.json:{tid}: section '{section.get('heading')}' looks thin")

    for tid in card_topics:
        if tid not in lesson_topics:
            err(f"topic '{tid}' has cards but no lesson (breaks topic ordering)")
    for tid in lesson_topics:
        if tid not in card_topics:
            warn(f"lesson '{tid}' has no cards")

    return len(lessons)


def validate_news():
    path = os.path.join(ASSETS, "news.json")
    if not os.path.exists(path):
        err("assets/news.json missing (bundled fallback feed)")
        return 0
    try:
        data = load(path)
    except json.JSONDecodeError as exc:
        err(f"assets/news.json: invalid JSON: {exc}")
        return 0
    items = data.get("items", [])
    if not isinstance(items, list) or not items:
        err("assets/news.json: needs at least one item so first launch is not empty")
        return 0
    for i, item in enumerate(items):
        for key in ("id", "title"):
            if not str(item.get(key, "")).strip():
                err(f"assets/news.json[{i}]: missing '{key}'")
    return len(items)


def main():
    topics, cards = validate_cards()
    lessons = validate_lessons(set(topics))
    news = validate_news()

    print(f"topics:  {len(topics)}")
    print(f"cards:   {cards}")
    print(f"lessons: {lessons}")
    print(f"news:    {news} bundled items")

    if warnings:
        print(f"\n{len(warnings)} warning(s):")
        for w in warnings[:25]:
            print(f"  ! {w}")
        if len(warnings) > 25:
            print(f"  ... and {len(warnings) - 25} more")

    if errors:
        print(f"\n{len(errors)} error(s):")
        for e in errors:
            print(f"  x {e}")
        return 1

    print("\nContent OK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
