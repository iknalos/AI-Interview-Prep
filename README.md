# AI Interview Prep

An Android app (Kotlin + Jetpack Compose) for preparing for AI / ML engineer interviews.
200 curated questions and 80 flashcards across 8 topics, spaced repetition, scored quizzes,
a mock interview mode, topic deep-dive lessons, and an AI news feed that refreshes daily.

**[Download the APK](https://github.com/iknalos/AI-Interview-Prep/releases/latest)** ·
**[Project page](https://iknalos.github.io/AI-Interview-Prep/)**

## Study modes

| Mode | What it does |
|---|---|
| **Flashcards** | One prompt, two options, instant feedback. Many cards are a flowchart, a comparison table or a diagram to read rather than a paragraph. Choosing is the reveal; then it is Next, or Learn more for the explanation and a link into the topic lesson. |
| **Study** | SM-2 spaced repetition. Miss a card and it returns in the same session and again tomorrow; know it and the interval stretches to weeks. |
| **Quiz** | Scored multiple choice with per-topic breakdown and a review of everything you missed. Answers also feed the review schedule. |
| **Mock interview** | Open-ended questions, no options. Answer out loud, then compare against a strong spoken answer and self-grade missed / partial / solid. |
| **Lessons** | ~12,000 words of deep dives, one per topic, written the way you would explain the concept in an interview. |

Topic and difficulty filters apply across every mode, so you can drive hard at one weak area.

## Content

| Topic | Cards | Flashcards |
|---|---|---|
| ML Fundamentals | 25 | 10 |
| Deep Learning | 25 | 10 |
| Transformers & LLMs | 25 | 10 |
| RAG & Vector Search | 25 | 10 |
| Fine-Tuning & Alignment | 25 | 10 |
| Evaluation & Metrics | 25 | 10 |
| MLOps & Serving | 25 | 10 |
| AI System Design | 25 | 10 |

Each card carries four options, the correct answer, a written explanation of *why*, and a
`modelAnswer` — how a strong candidate would actually say it out loud. Difficulty is graded
easy / medium / hard.

Cards live in `app/src/main/assets/cards_<topic>.json`, one file per topic, so the bank grows
by adding a file — no code change needed. `scripts/validate_content.py` gates every build on
schema, duplicate IDs, option count, and answer-index sanity.

### Flashcard visuals

Flashcards live alongside the bank in `flash_<topic>.json` and carry two options, the answer
index and an explanation. Any card may attach a `visual`, which the app draws from structured
JSON rather than shipping bitmaps — so a diagram inherits the theme, stays sharp at any
density, and rides the over-the-air content bundle like everything else:

| `type` | Fields | Rendered as |
|---|---|---|
| `flowchart` | `steps[]` | Boxes top to bottom with arrows between them |
| `table` | `headers[]`, `rows[][]` | An even-column comparison grid |
| `diagram` | `text` | Monospace block, scrolls sideways, alignment preserved |
| `code` | `text` | Same, tinted as code |
| `image` | `data` | Base64 PNG or JPEG, decoded on device |

`image` is the escape hatch for content that genuinely needs pixels. Prefer the structural
types: they are a fraction of the bytes and readable in both light and dark themes. A visual
that is malformed is dropped rather than shown broken, so every prompt has to stand on its
own text.

## Automatic updates

Install once, grant one Android permission, and the app maintains itself.

**Content updates need no install at all.** The question bank and lessons are published
as a versioned bundle to GitHub Pages (`docs/content.json`, plus a small
`content-index.json` the app polls). The app downloads it only when the published version
is higher than what it has, verifies the SHA-256, and prefers it over the copies baked
into the APK. The bundled copy remains the offline floor, so first launch and no-network
both work. Adding 50 questions reaches every installed copy without anyone installing
anything.

**App updates install themselves.** A WorkManager job runs daily at about 4am on
unmetered network, refreshes news, pulls new content, then reads `docs/app-version.json`,
downloads the release APK, verifies its SHA-256, and installs it through
`PackageInstaller` with `USER_ACTION_NOT_REQUIRED`. That is genuinely silent on Android 12+
because the app is updating *itself* with a matching signature — which the committed
keystore guarantees. On Android 8–11 the silent path does not exist, so those devices get
one confirmation per update.

**The one manual step:** Android requires the user to grant "install unknown apps" once,
in Settings. No app can skip this, including apps distributed through Play. The app
surfaces it as an actionable card on Home and in Settings rather than retrying forever.

Both digests are asserted in CI, because a mismatch would make the app silently reject
every update — a failure that is invisible until someone notices they have not been
updated in a month. `.gitattributes` marks the checksummed files `-text` so line-ending
translation cannot corrupt them either.

The Settings screen shows app version, content version, content source (bundled vs over
the air), last check time and result, an auto-update toggle, a check-now button, and a
button that queues the exact 4am job so the whole path can be verified on demand.

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
  assets/            cards_*.json (question bank), flash_*.json (flashcards),
                     lessons.json, news.json + content-version.txt (offline fallbacks)
  java/com/iknalos/aiprep/
    MainActivity.kt        Compose nav host + bottom bar, schedules the 4am job
    AppViewModel.kt        all session state (study / flash / quiz / mock / news / sync)
    Models.kt              content + persisted progress models
    Content.kt             loads bundled or over-the-air content, topic ordering
    ContentSync.kt         versioned content bundle fetch, verify, cache
    Progress.kt            SM-2 scheduler, JSON progress store, streaks
    News.kt                feed fetch + cache + bundled fallback
    Updater.kt             version manifest, APK download, silent self-install
    DailySync.kt           4am WorkManager job + settings wrapper
    ui/                    theme, shared composables, flashcard visual renderers
    screens/               Home, Flash, Study, Quiz, Mock, Lessons, News, Stats,
                           Focus, Settings

scripts/
  validate_content.py      schema gate on the question bank (runs in CI)
  build_content_bundle.py  deterministic content bundle + index for Pages
  fetch_news.py            RSS aggregator for the daily feed
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
