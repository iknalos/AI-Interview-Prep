#!/usr/bin/env python3
"""
Validate the bundled question bank, flashcards and lessons before a build.

A malformed card would only surface as a crash on the user's phone, so this runs
in CI as a gate. Also enforces that every topic with cards has a lesson, since
the app's topic ordering is driven by lessons.json.
"""

import base64
import binascii
import glob
import json
import os
import sys

ASSETS = os.path.join("app", "src", "main", "assets")
DIFFICULTIES = {"easy", "medium", "hard"}
REQUIRED_CARD_FIELDS = ("id", "difficulty", "question", "options", "answer",
                        "explanation", "modelAnswer")
REQUIRED_FLASH_FIELDS = ("id", "difficulty", "prompt", "options", "answer",
                         "explanation")
VISUAL_TYPES = {"flowchart", "table", "diagram", "code", "image"}

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


def validate_visual(where, visual):
    """A visual is optional, but a half-written one silently disappears in the app."""
    if not isinstance(visual, dict):
        err(f"{where}: 'visual' must be an object")
        return

    vtype = visual.get("type", "")
    if vtype not in VISUAL_TYPES:
        err(f"{where}: visual type '{vtype}' not one of {sorted(VISUAL_TYPES)}")
        return

    if vtype == "flowchart":
        steps = visual.get("steps", [])
        if not isinstance(steps, list) or len(steps) < 2:
            err(f"{where}: flowchart needs at least 2 steps")
        elif any(not str(s).strip() for s in steps):
            err(f"{where}: flowchart has a blank step")
    elif vtype == "table":
        headers = visual.get("headers", [])
        rows = visual.get("rows", [])
        if not isinstance(headers, list) or not headers:
            err(f"{where}: table needs headers")
        if not isinstance(rows, list) or not rows:
            err(f"{where}: table needs at least one row")
        elif isinstance(headers, list):
            # The app pads short rows rather than crashing, but a ragged table is a
            # content bug and reads as a missing cell on the phone.
            for r, row in enumerate(rows):
                if not isinstance(row, list) or len(row) != len(headers):
                    err(f"{where}: table row {r} has "
                        f"{len(row) if isinstance(row, list) else '?'} cells, "
                        f"expected {len(headers)}")
    elif vtype in ("diagram", "code"):
        if not str(visual.get("text", "")).strip():
            err(f"{where}: {vtype} needs 'text'")
        elif max((len(line) for line in str(visual["text"]).splitlines()), default=0) > 64:
            # Wide preformatted blocks scroll sideways, which is worse than wrapping
            # prose but tolerable; flag it so it is a decision rather than an accident.
            warn(f"{where}: {vtype} has a line over 64 characters, will scroll on a phone")
    elif vtype == "image":
        data = str(visual.get("data", ""))
        if not data.strip():
            err(f"{where}: image needs base64 'data'")
        else:
            try:
                decoded = base64.b64decode(data, validate=True)
            except (binascii.Error, ValueError):
                err(f"{where}: image 'data' is not valid base64")
            else:
                if len(decoded) > 400_000:
                    warn(f"{where}: image is {len(decoded) // 1024} KB; it rides the "
                         f"OTA content bundle, so keep it small")


def validate_flash(card_ids, lesson_topics_hint):
    """
    Two-option flashcards. Their ids share the progress map with the main card
    bank, so a collision would silently merge two different cards' history.
    """
    files = sorted(glob.glob(os.path.join(ASSETS, "flash_*.json")))
    if not files:
        warn("no flash_*.json files found; the flashcards tab will be empty")
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
            missing = [f for f in REQUIRED_FLASH_FIELDS if f not in card]
            for field in missing:
                err(f"{where}: missing field '{field}'")
            if missing:
                continue

            cid = card["id"]
            where = f"{name}:{cid}"
            if cid in seen_ids:
                err(f"{where}: duplicate id, also in {seen_ids[cid]}")
            if cid in card_ids:
                err(f"{where}: id collides with a question card; they share progress state")
            seen_ids[cid] = name

            if card["difficulty"] not in DIFFICULTIES:
                err(f"{where}: difficulty '{card['difficulty']}' not one of {sorted(DIFFICULTIES)}")

            opts = card["options"]
            if not isinstance(opts, list) or len(opts) != 2:
                err(f"{where}: flashcards need exactly 2 options, got "
                    f"{len(opts) if isinstance(opts, list) else type(opts).__name__}")
            elif any(not str(o).strip() for o in opts):
                err(f"{where}: blank option")
            elif opts[0].strip() == opts[1].strip():
                err(f"{where}: both options are the same text")

            ans = card["answer"]
            if not isinstance(ans, int) or ans not in (0, 1):
                err(f"{where}: answer must be 0 or 1, got {ans!r}")

            for field in ("prompt", "explanation"):
                if not str(card[field]).strip():
                    err(f"{where}: '{field}' is empty")

            if len(str(card["explanation"]).split()) < 20:
                warn(f"{where}: explanation looks thin "
                     f"({len(str(card['explanation']).split())} words); it is the whole "
                     f"payoff of Learn more")

            if card.get("visual") is not None:
                validate_visual(where, card["visual"])

            total += 1

    for tid in topics:
        if tid not in lesson_topics_hint:
            err(f"flashcard topic '{tid}' has no lesson; the Learn more link would dead-end")

    return topics, total


def lesson_topic_ids():
    path = os.path.join(ASSETS, "lessons.json")
    try:
        return {l.get("topicId") for l in load(path).get("lessons", [])}
    except (OSError, json.JSONDecodeError):
        return set()


def card_id_set():
    ids = set()
    for path in glob.glob(os.path.join(ASSETS, "cards_*.json")):
        try:
            for card in load(path).get("cards", []):
                if "id" in card:
                    ids.add(card["id"])
        except (OSError, json.JSONDecodeError):
            continue
    return ids


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
    flash_topics, flash = validate_flash(card_id_set(), lesson_topic_ids())
    news = validate_news()

    print(f"topics:  {len(topics)}")
    print(f"cards:   {cards}")
    print(f"flash:   {flash} across {len(flash_topics)} topics")
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
