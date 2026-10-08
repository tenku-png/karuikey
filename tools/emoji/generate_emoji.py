#!/usr/bin/env python3
"""Builds app/src/main/assets/emoji/emoji.tsv from Unicode emoji-test.txt and CLDR annotations.

Sources (pinned):
  https://unicode.org/Public/emoji/16.0/emoji-test.txt
  https://github.com/unicode-org/cldr-json  48.0.0  cldr-annotations-full / cldr-annotations-derived-full

Usage: generate_emoji.py <emoji-test.txt> <cldr dir with ann_<lang>.json and der_<lang>.json> <out.tsv>

Output, one base emoji per line in Unicode order:
  category  emoji  variants(space separated)  en name  en keywords(|)  ru name  ru keywords(|)
"""
import json
import sys

LANGS = ("en", "ru")
GROUPS = {
    "Smileys & Emotion": "FACES",
    "People & Body": "PEOPLE",
    "Animals & Nature": "ANIMALS",
    "Food & Drink": "FOOD",
    "Travel & Places": "TRAVEL",
    "Activities": "ACTIVITIES",
    "Objects": "OBJECTS",
    "Symbols": "SYMBOLS",
    "Flags": "FLAGS",
}
SKIN_TONES = {chr(c) for c in range(0x1F3FB, 0x1F400)}
VS16 = "️"


def read_emoji(path):
    entries = []  # (category, emoji)
    group = None
    with open(path, encoding="utf-8") as source:
        for line in source:
            if line.startswith("# group:"):
                group = GROUPS.get(line.split(":", 1)[1].strip())
                continue
            if not line.strip() or line.startswith("#") or group is None:
                continue
            fields, _, comment = line.partition("#")
            if fields.split(";")[1].strip() != "fully-qualified":
                continue
            emoji = comment.strip().split(" ", 1)[0]
            entries.append((group, emoji))
    return entries


def read_annotations(directory, lang):
    names, keywords = {}, {}
    for kind in ("ann", "der"):
        with open(f"{directory}/{kind}_{lang}.json", encoding="utf-8") as source:
            data = json.load(source)
        root = data["annotations"] if kind == "ann" else data["annotationsDerived"]
        for emoji, value in root["annotations"].items():
            if "tts" in value:
                names.setdefault(emoji, value["tts"][0])
            if "default" in value:
                keywords.setdefault(emoji, value["default"])
    return names, keywords


def lookup(table, emoji):
    return table.get(emoji) or table.get(emoji.replace(VS16, ""))


def clean(text):
    return text.replace("\t", " ").replace("\n", " ").replace("|", " ")


def main(test_path, cldr_dir, out_path):
    entries = read_emoji(test_path)
    annotations = {lang: read_annotations(cldr_dir, lang) for lang in LANGS}
    bases, order = {}, []
    for category, emoji in entries:
        stripped = "".join(c for c in emoji if c not in SKIN_TONES)
        if stripped != emoji and stripped in bases:
            bases[stripped][1].append(emoji)  # skin tone variant of an earlier base emoji
            continue
        bases[emoji] = (category, [])
        order.append(emoji)
    with open(out_path, "w", encoding="utf-8") as out:
        for emoji in order:
            category, variants = bases[emoji]
            columns = [category, emoji, " ".join(variants)]
            for lang in LANGS:
                names, keywords = annotations[lang]
                name = lookup(names, emoji) or ""
                words = [clean(word) for word in (lookup(keywords, emoji) or []) if word != name]
                columns += [clean(name), "|".join(words)]
            out.write("\t".join(columns) + "\n")
    print(f"{len(order)} base emoji, {sum(len(bases[e][1]) for e in order)} variants")


if __name__ == "__main__":
    main(*sys.argv[1:4])
