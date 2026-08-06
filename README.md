# AI Interview Prep

An Android app (Kotlin + Jetpack Compose) for preparing for AI / ML engineer interviews.
200 curated questions across 8 topics, spaced repetition, scored quizzes, a mock interview
mode, topic deep-dive lessons, and an AI news feed that refreshes daily.

**[Download the APK](https://github.com/iknalos/AI-Interview-Prep/releases/latest)** ·
**[Project page](https://iknalos.github.io/AI-Interview-Prep/)**

## Study modes

| Mode | What it does |
|---|---|
| **Study** | SM-2 spaced repetition. Miss a card and it returns in the same session and again tomorrow; know it and the interval stretches to weeks. |
| **Quiz** | Scored multiple choice with per-topic breakdown and a review of everything you missed. Answers also feed the review schedule. |
| **Mock interview** | Open-ended questions, no options. Answer out loud, then compare against a strong spoken answer and self-grade missed / partial / solid. |
| **Lessons** | ~12,000 words of deep dives, one per topic, written the way you would explain the concept in an interview. |

Topic and difficulty filters apply across every mode, so you can drive hard at one weak area.

## Content

| Topic | Cards |
|---|---|
| ML Fundamentals | 25 |
| Deep Learning | 25 |
| Transformers & LLMs | 25 |
| RAG & Vector Search | 25 |
| Fine-Tuning & Alignment | 25 |
| Evaluation & Metrics | 25 |
| MLOps & Serving | 25 |
| AI System Design | 25 |

Each card carries four options, the correct answer, a written explanation of *why*, and a
`modelAnswer` — how a strong candidate would actually say it out loud. Difficulty is graded
easy / medium / hard.

Cards live in `app/src/main/assets/cards_<topic>.json`, one file per topic, so the bank grows
by adding a file — no code change needed. `scripts/validate_content.py` gates every build on
schema, duplicate IDs, option count, and answer-index sanity.

## Daily news feed

`scripts/fetch_news.py` aggregates public RSS/Atom feeds (OpenAI, Google Research, DeepMind,
Hugging Face, BAIR, MIT, arXiv cs.CL and cs.LG, plus a few newsletters), tags each item
against the app's study topics by keyword, de-duplicates, round-robins across sources so no
single feed dominates, and writes `docs/news.json`. GitHub Pages serves that file and the app
fetches it.

- Standard library only, so there is no dependency that can break the daily run.
- Each source is fetched independently; a dead feed is skipped, not fatal.
- The workflow refuses to publish a nearly empty feed rather than overwriting a good one.
- The app ships a bundled snapshot and caches the last successful fetch, so it is never blank.

No API keys anywhere. Runs daily at 06:20 UTC via `.github/workflows/daily-news.yml`.

## Build

CI builds the APK on every push and publishes it as a release — see
`.github/workflows/build.yml`. A keystore is generated once and committed, so every build is
signed identically and updates install in place instead of requiring an uninstall.

To build locally you need JDK 17 and the Android SDK:

```bash
gradle assembleDebug          # -> app/build/outputs/apk/debug/app-debug.apk
python scripts/validate_content.py
python scripts/fetch_news.py --out docs/news.json
```

## Architecture

```
app/src/main/
  assets/            cards_*.json (question bank), lessons.json, news.json (fallback)
  java/com/iknalos/aiprep/
    MainActivity.kt        Compose nav host + bottom bar
    AppViewModel.kt        all session state (study / quiz / mock / news)
    Models.kt              content + persisted progress models
    Content.kt             asset loader, topic ordering
    Progress.kt            SM-2 scheduler, JSON progress store, streaks
    News.kt                feed fetch + cache + bundled fallback
    ui/                    theme and shared composables
    screens/               Home, Study, Quiz, Mock, Lessons, News, Stats, Focus
```

Deliberately dependency-light: no Room, no Retrofit, no DI framework. Progress is a single
JSON file (`filesDir/progress.json`) and the feed is fetched with `HttpURLConnection`, which
keeps the APK small and the build reproducible.

- **minSdk** 26 (Android 8.0), **targetSdk** 34
- Kotlin 1.9.24, AGP 8.5.2, Compose BOM 2024.06.00, Material 3
- Everything except the news feed works fully offline

## Notes

Progress lives on the device only — nothing is uploaded and there is no account. Resetting
progress from the Progress screen clears review history and streaks but keeps your topic focus
and daily goal.
