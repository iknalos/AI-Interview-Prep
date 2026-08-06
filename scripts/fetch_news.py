#!/usr/bin/env python3
"""
Build docs/news.json from public AI RSS/Atom feeds.

Standard library only, so the workflow needs no pip install and cannot break
because of a dependency update. Every source is fetched independently and a
failing source is skipped rather than failing the run, because one lab changing
its feed URL should not take the whole daily update down.

Usage:  python scripts/fetch_news.py [--out docs/news.json] [--max 60]
"""

import argparse
import datetime as dt
import hashlib
import html
import json
import os
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

USER_AGENT = "AI-Interview-Prep-news-bot/1.0 (+https://github.com/iknalos/AI-Interview-Prep)"
TIMEOUT = 25

# (display name, url, default tag)
SOURCES = [
    ("OpenAI", "https://openai.com/news/rss.xml", "labs"),
    ("Google Research", "https://research.google/blog/rss/", "research"),
    ("DeepMind", "https://deepmind.google/blog/rss.xml", "labs"),
    ("Hugging Face", "https://huggingface.co/blog/feed.xml", "open-source"),
    ("BAIR", "https://bair.berkeley.edu/blog/feed.xml", "research"),
    ("MIT News AI", "https://news.mit.edu/rss/topic/artificial-intelligence2", "research"),
    ("Import AI", "https://importai.substack.com/feed", "newsletter"),
    ("Ahead of AI", "https://magazine.sebastianraschka.com/feed", "newsletter"),
    ("MarkTechPost", "https://www.marktechpost.com/feed/", "industry"),
    (
        "arXiv cs.CL",
        "http://export.arxiv.org/api/query?search_query=cat:cs.CL"
        "&sortBy=submittedDate&sortOrder=descending&max_results=12",
        "paper",
    ),
    (
        "arXiv cs.LG",
        "http://export.arxiv.org/api/query?search_query=cat:cs.LG"
        "&sortBy=submittedDate&sortOrder=descending&max_results=12",
        "paper",
    ),
]

# Keywords mapped onto the app's study topics, so an item can say which topic it
# is worth revising alongside.
TOPIC_KEYWORDS = {
    "rag": ["retrieval", "rag ", "vector search", "reranker", "embedding", "knowledge base"],
    "llm": ["llm", "language model", "transformer", "attention", "gpt", "context window",
            "tokenizer", "mixture of experts", "moe"],
    "fine-tuning": ["fine-tun", "finetun", "lora", "rlhf", "dpo", "preference", "alignment",
                    "instruction tun", "distill"],
    "evaluation": ["benchmark", "eval", "leaderboard", "hallucinat", "faithful"],
    "serving": ["inference", "quantiz", "latency", "throughput", "serving", "kv cache",
                "speculative decoding", "vllm"],
    "agents": ["agent", "tool use", "tool-use", "function calling", "mcp"],
    "reasoning": ["reasoning", "chain of thought", "chain-of-thought", "test-time", "verifier"],
    "multimodal": ["multimodal", "vision-language", "image generation", "diffusion", "speech"],
    "safety": ["safety", "jailbreak", "prompt injection", "red team", "interpretability"],
}

TAG_RE = re.compile(r"<[^>]+>")
WS_RE = re.compile(r"\s+")


def log(msg):
    print(msg, file=sys.stderr)


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
        return resp.read()


def clean_text(raw, limit=340):
    if not raw:
        return ""
    text = html.unescape(raw)
    text = TAG_RE.sub(" ", text)
    text = html.unescape(text)
    text = WS_RE.sub(" ", text).strip()
    if len(text) > limit:
        cut = text[:limit].rsplit(" ", 1)[0]
        text = cut.rstrip(",.;:") + "..."
    return text


def strip_ns(tag):
    return tag.split("}", 1)[-1] if "}" in tag else tag


def parse_date(value):
    """Return an ISO date string, or '' if the format is unrecognised."""
    if not value:
        return ""
    value = value.strip()
    # Atom / ISO 8601
    iso = value.replace("Z", "+00:00")
    try:
        return dt.datetime.fromisoformat(iso).date().isoformat()
    except ValueError:
        pass
    # RFC 822 as used by RSS
    for fmt in ("%a, %d %b %Y %H:%M:%S %z", "%a, %d %b %Y %H:%M:%S %Z",
                "%a, %d %b %Y %H:%M:%S", "%d %b %Y %H:%M:%S %z"):
        try:
            return dt.datetime.strptime(value, fmt).date().isoformat()
        except ValueError:
            continue
    m = re.search(r"(\d{4})-(\d{2})-(\d{2})", value)
    return m.group(0) if m else ""


def child_text(node, names):
    for child in node:
        if strip_ns(child.tag) in names:
            if child.text and child.text.strip():
                return child.text
    return ""


def entry_link(node):
    # RSS puts the URL in <link>text</link>; Atom uses <link href="...">.
    for child in node:
        if strip_ns(child.tag) != "link":
            continue
        rel = child.attrib.get("rel", "alternate")
        href = child.attrib.get("href")
        if href and rel == "alternate":
            return href
        if child.text and child.text.strip():
            return child.text.strip()
    return ""


def topics_for(title, summary):
    blob = (title + " " + summary).lower()
    found = [topic for topic, words in TOPIC_KEYWORDS.items()
             if any(w in blob for w in words)]
    return found[:3]


def parse_feed(name, xml_bytes, default_tag):
    root = ET.fromstring(xml_bytes)
    nodes = [n for n in root.iter() if strip_ns(n.tag) in ("item", "entry")]
    items = []
    for node in nodes:
        title = clean_text(child_text(node, ("title",)), 200)
        if not title:
            continue
        url = entry_link(node)
        summary = clean_text(
            child_text(node, ("summary", "description", "content", "encoded"))
        )
        published = parse_date(
            child_text(node, ("published", "pubDate", "updated", "date"))
        )
        tags = [default_tag] + topics_for(title, summary)
        items.append(
            {
                "id": hashlib.sha1((url or title).encode("utf-8")).hexdigest()[:16],
                "title": title,
                "summary": summary,
                "source": name,
                "url": url,
                "published": published,
                "tags": list(dict.fromkeys(tags)),
            }
        )
    return items


def collect():
    items, ok, failed = [], [], []
    for name, url, tag in SOURCES:
        try:
            raw = fetch(url)
            parsed = parse_feed(name, raw, tag)
            if parsed:
                items.extend(parsed)
                ok.append(f"{name} ({len(parsed)})")
            else:
                failed.append(f"{name} (empty)")
        except (urllib.error.URLError, urllib.error.HTTPError, ET.ParseError,
                TimeoutError, OSError, ValueError) as exc:
            failed.append(f"{name} ({type(exc).__name__})")
    log("ok:     " + (", ".join(ok) or "none"))
    log("failed: " + (", ".join(failed) or "none"))
    return items


def dedupe(items):
    seen, out = set(), []
    for it in items:
        key = it["id"]
        title_key = re.sub(r"[^a-z0-9]", "", it["title"].lower())[:70]
        if key in seen or title_key in seen:
            continue
        seen.add(key)
        seen.add(title_key)
        out.append(it)
    return out


def interleave(items):
    """Round-robin across sources so one prolific feed cannot dominate the top."""
    by_source = {}
    for it in items:
        by_source.setdefault(it["source"], []).append(it)
    for group in by_source.values():
        group.sort(key=lambda x: x["published"], reverse=True)
    ordered, index = [], 0
    while any(index < len(g) for g in by_source.values()):
        for group in by_source.values():
            if index < len(group):
                ordered.append(group[index])
        index += 1
    return ordered


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join("docs", "news.json"))
    ap.add_argument("--max", type=int, default=60)
    ap.add_argument("--min-items", type=int, default=8,
                    help="Fail rather than publish a nearly empty feed.")
    args = ap.parse_args()

    items = interleave(dedupe(collect()))[: args.max]

    if len(items) < args.min_items:
        log(f"ERROR: only {len(items)} items collected, refusing to overwrite the feed.")
        return 1

    feed = {
        "generated": dt.date.today().isoformat(),
        "generatedAt": dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat(),
        "sourceCount": len({i["source"] for i in items}),
        "items": items,
    }

    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(feed, fh, indent=1, ensure_ascii=False)
        fh.write("\n")

    log(f"wrote {len(items)} items from {feed['sourceCount']} sources to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
