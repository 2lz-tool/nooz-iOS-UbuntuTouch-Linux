#!/usr/bin/env python3
"""
Turn the translation catalogues into what each platform actually reads.

WHY A GENERATOR AT ALL
Nooz has two front ends and one set of words. Keeping Android's strings.xml and
the web reader's catalogue as separate hand-maintained files means a translator
does every language twice, and the two drift the moment anyone is in a hurry --
which in practice means the web reader stays English. One source, two outputs,
so "the web reader must also do the same" is structural rather than a promise.

SOURCE OF TRUTH
  i18n/strings/en.json    the base catalogue: key -> English
  i18n/strings/<tag>.json a translation, keyed the same way

Text in these files is plain and human: real apostrophes, real quotes, no
platform escaping. Escaping belongs to whichever platform needs it, and doing it
here would put backslashes in front of a translator.

A translation may be partial. Any key it omits falls back to English -- Android
resolves per key, and the web layer does the same -- so a locale can ship at a
third done and show that third. Never a blank, never a key name.

OUTPUTS (both generated; edit the JSON, not these)
  core/design/src/main/res/values-<qualifier>/strings.xml
  web/i18n/<tag>.json

Run:  python3 tools/i18n/generate.py          write the outputs
      python3 tools/i18n/generate.py --check  fail if they are out of date (CI)
"""

import json
import pathlib
import sys

REPO = pathlib.Path(__file__).resolve().parents[2]
CATALOGUES = REPO / "i18n" / "strings"
ANDROID_RES = REPO / "core" / "design" / "src" / "main" / "res"
LOCALE_CONFIG = REPO / "app" / "src" / "main" / "res" / "xml" / "locales_config.xml"
COVERAGE_KT = (
    REPO / "core" / "model" / "src" / "commonMain" / "kotlin"
    / "xyz" / "mdhv" / "riverwip" / "model" / "LocaleCoverage.kt"
)
WEB_OUT = REPO / "web" / "i18n"
WEB_INDEX = WEB_OUT / "index.json"
LEXICON_IN = REPO / "i18n" / "lexicon"
LEXICON_KT = (
    REPO / "core" / "model" / "src" / "commonMain" / "kotlin"
    / "xyz" / "mdhv" / "riverwip" / "model" / "TopicLexiconL10n.kt"
)
LEXICON_WEB = REPO / "web" / "js" / "topics-l10n.js"

BASE_TAG = "en"

BASE_HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!--
  GENERATED FILE \u2014 do not edit. Source: i18n/strings/en.json (words) and
  i18n/strings/_sections.json (grouping); generator: tools/i18n/generate.py.

  ADDING A LANGUAGE
  Drop i18n/strings/<bcp47>.json next to en.json, translate the values, run the
  generator. That is the whole procedure \u2014 no Kotlin, no build change.
  Locales.kt in :core:model is the list the app offers.

  Android resolves each string separately and falls back to this file per key,
  so a half-finished locale shows the half it has and English for the rest.
  Never a blank, never a key name. Partial translations are therefore safe to
  ship, which is what makes it possible to start thirty languages rather than
  finish two.

  WRITING THE COPY
  The register is set in STATE.md and is not decoration: descriptive, never
  scolding; shapes and counts, never FOMO or praise; and every total carries its
  denominator \u2014 the reader's own chosen sources, never "all the news". A
  translation that reads naturally but drops the denominator has changed what
  the app claims, so keep it.
-->
<resources>
"""

HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!--
  GENERATED FILE — do not edit.

  Source:    i18n/strings/{tag}.json
  Generator: tools/i18n/generate.py

  Edit the JSON and re-run the generator. Keys absent here fall back to
  values/strings.xml, per key, so a partial translation is safe to ship.
  {coverage} of {total} strings translated.
-->
<resources>
"""


def android_qualifier(tag: str) -> str:
    """BCP 47 -> the `values-` suffix Android wants (`zh-Hans` -> `b+zh+Hans`)."""
    return "b+" + tag.replace("-", "+")


def escape_android(value: str) -> str:
    """
    Android's string resource escaping.

    `&` and `<` are XML; `'` and `"` are aapt's own, and an unescaped apostrophe
    is a build error rather than a warning. A leading `@` or `?` would be read as
    a resource reference.
    """
    out = (
        value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", "\\'")
        .replace('"', '\\"')
        .replace("\n", "\\n")
    )
    if out[:1] in ("@", "?"):
        out = "\\" + out
    return out


def load(tag: str) -> dict:
    path = CATALOGUES / f"{tag}.json"
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def render_android(tag: str, strings: dict, base: dict) -> str:
    lines = [HEADER.format(tag=tag, coverage=len(strings), total=len(base))]
    for key in base:  # base order, so files line up for review
        if key not in strings:
            continue
        lines.append(f'    <string name="{key}">{escape_android(strings[key])}</string>')
    lines.append("</resources>\n")
    return "\n".join(lines)


def render_web(tag: str, strings: dict, base: dict) -> str:
    ordered = {key: strings[key] for key in base if key in strings}
    return json.dumps(ordered, ensure_ascii=False, indent=2) + "\n"


def xml_comment(text: str) -> str:
    """
    A comment aapt will accept.

    `--` is illegal inside an XML comment and aapt refuses the whole resource
    file over it, with an error that names a line number and nothing else. Prose
    written for humans reaches for a double dash constantly, so this converts it
    rather than leaving a trap for whoever writes the next section note.
    """
    return "<!-- " + text.replace("--", "\u2014") + " -->"


def render_base(base: dict) -> str:
    """The English resources, grouped and annotated from _sections.json."""
    sections = json.loads((CATALOGUES / "_sections.json").read_text(encoding="utf-8"))["sections"]
    placed = set()
    out = [BASE_HEADER]
    for section in sections:
        keys = [k for k in base if k.startswith(section["prefix"])]
        if not keys:
            continue
        placed.update(keys)
        out.append("    " + xml_comment(section["title"]))
        notes = section.get("notes", {})
        for key in keys:
            if key in notes:
                out.append("    " + xml_comment(notes[key]))
            out.append(f'    <string name="{key}">{escape_android(base[key])}</string>')
        out.append("")
    leftover = [k for k in base if k not in placed]
    if leftover:
        out.append("    <!-- Uncategorised: give these a section in _sections.json. -->")
        for key in leftover:
            out.append(f'    <string name="{key}">{escape_android(base[key])}</string>')
        out.append("")
    out.append("</resources>\n")
    return "\n".join(out)


def render_locale_config(tags: list[str]) -> str:
    """
    android:localeConfig -- the list Android's own per-app language picker
    shows, from Android 13 onward.

    Generated from the catalogues that actually exist, not from the languages
    the app intends to support. Listing a locale with nothing translated would
    offer someone their language in the system settings and then hand them an
    English app, which is a worse answer than not offering it.
    """
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!--",
        "  GENERATED FILE \u2014 do not edit. Generator: tools/i18n/generate.py",
        "",
        "  Only locales with a catalogue in i18n/strings/ appear here. A language",
        "  listed in Locales.kt but not yet translated is a commitment, not a",
        "  shipped locale, and must not be offered in the system picker.",
        "-->",
        '<locale-config xmlns:android="http://schemas.android.com/apk/res/android">',
    ]
    for tag in tags:
        lines.append(f'    <locale android:name="{tag}" />')
    lines.append("</locale-config>")
    return "\n".join(lines) + "\n"


def render_coverage(coverage: dict, total: int) -> str:
    """
    How much of the app each locale actually carries, as data the picker can
    show. Measured, never asserted: a reader choosing a language deserves to
    know before they switch that a third of it will still be in English.
    """
    rows = "\n".join(
        f'    "{tag}" to {count},' for tag, count in sorted(coverage.items())
    )
    return f'''package xyz.mdhv.riverwip.model

/**
 * GENERATED FILE — do not edit. Generator: tools/i18n/generate.py
 *
 * How many of the interface's strings each locale has, counted from the
 * catalogues in i18n/strings/. The language picker shows this so nobody
 * chooses a language and is then surprised by a half-English screen.
 */
object LocaleCoverage {{
    /** Strings in the base catalogue — the denominator. */
    const val TOTAL = {total}

    /** BCP 47 tag -> strings translated. Absent means none, so English. */
    val TRANSLATED: Map<String, Int> = mapOf(
{rows}
    )

    fun percentFor(tag: String): Int =
        if (TOTAL == 0) 0 else (TRANSLATED[tag] ?: 0) * 100 / TOTAL

    /** Locales with at least one translated string, i.e. worth offering. */
    val SHIPPED: Set<String> = TRANSLATED.filterValues {{ it > 0 }}.keys
}}
'''


def render_web_index(locales: list, coverage: dict, total: int) -> str:
    """
    One file the web reader fetches to know what it can offer: tag, the
    language's own name for itself, and how much of the interface it carries.

    The endonyms come from Locales.kt, parsed rather than duplicated -- a
    second list of thirty language names is a second list to get out of step.
    """
    rows = [
        {
            "tag": tag,
            "endonym": locales[tag]["endonym"],
            "english": locales[tag]["english"],
            "dir": locales[tag]["dir"],
            "coverage": round(100 * coverage[tag] / total) if total else 0,
        }
        for tag in sorted(coverage)
        if coverage[tag] > 0 and tag in locales
    ]
    return json.dumps({"total": total, "locales": rows}, ensure_ascii=False, indent=2) + "\n"


def parse_locales_kt() -> dict:
    """
    Read the endonyms out of Locales.kt.

    Deliberately a parse of the real declaration rather than a copy: the list of
    languages Nooz offers is a decision, and it should be written down exactly
    once. A regex over Kotlin is a little crude, and it fails loudly (an empty
    result fails the generator) rather than silently drifting.
    """
    import re

    source = (
        REPO / "core" / "model" / "src" / "commonMain" / "kotlin"
        / "xyz" / "mdhv" / "riverwip" / "model" / "Locales.kt"
    ).read_text(encoding="utf-8")
    # The base entry is written `Locale(BASE_TAG, …)` so the constant stays the
    # single spelling of "en"; substitute it in before matching.
    base_tag = re.search(r'const val BASE_TAG = "([\w-]+)"', source)
    if base_tag:
        source = source.replace("Locale(BASE_TAG,", f'Locale("{base_tag.group(1)}",')

    out = {}
    pattern = re.compile(
        r'Locale\(\s*"(?P<tag>[\w-]+)"\s*,\s*"(?P<endonym>[^"]+)"\s*,\s*"(?P<english>[^"]+)"'
        r'(?:\s*,\s*Direction\.(?P<dir>LTR|RTL))?',
    )
    for match in pattern.finditer(source):
        out[match.group("tag")] = {
            "endonym": match.group("endonym"),
            "english": match.group("english"),
            "dir": (match.group("dir") or "LTR").lower(),
        }
    return out


def load_lexicon() -> dict:
    """
    Topic keywords in the languages the source catalogue actually publishes in,
    merged across every language file into one map of topic -> terms.

    Merged rather than kept per-language on purpose: an article is classified by
    what its words are, not by a language label the feed may not carry. A Tamil
    headline should match Tamil terms without anyone having had to tag the feed
    correctly first.
    """
    merged: dict[str, list[str]] = {}
    if not LEXICON_IN.exists():
        return merged
    for path in sorted(LEXICON_IN.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        for topic, terms in data.items():
            bucket = merged.setdefault(topic, [])
            for term in terms:
                if term not in bucket:
                    bucket.append(term)
    return merged


def render_lexicon_kt(lexicon: dict) -> str:
    blocks = []
    for topic in sorted(lexicon):
        terms = ",\n".join(f'            "{t}"' for t in lexicon[topic])
        blocks.append(f'        "{topic}" to listOf(\n{terms},\n        ),')
    body = "\n".join(blocks)
    return f'''package xyz.mdhv.riverwip.model

/**
 * GENERATED FILE — do not edit.
 * Source:    the files in i18n/lexicon/  (a glob here would open a nested
 *            Kotlin block comment, which does not close)
 * Generator: tools/i18n/generate.py
 *
 * Topic keywords in the languages the source catalogue publishes in, merged
 * onto [TopicLexicon.terms].
 *
 * The gap this closes: the lexicon was English-only while the catalogue ships
 * 33 India regional feeds across eleven scripts, so every one of those articles
 * classified as `general`. The Loom collapsed to a single band and the Contrast
 * dumbbells emptied — for exactly the readers the India expansion was for — and
 * nothing errored. It simply looked like a quiet news day, every day.
 *
 * The same JSON generates the web reader's `topics-l10n.json`. A classifier
 * that disagrees between two clients showing the same numbers is worse than one
 * that is merely incomplete.
 */
internal object TopicLexiconL10n {{
    val TERMS: Map<String, List<String>> = mapOf(
{body}
    )
}}
'''


def render_lexicon_web(lexicon: dict) -> str:
    """
    A JS module rather than JSON on purpose: `classifyItem` is synchronous and
    called from every render path, so the terms have to be there at module
    init. A fetch would make classification async, or -- worse -- make it
    silently return `general` for everything until the fetch landed, which is
    the exact bug this whole file exists to fix.
    """
    body = json.dumps({k: lexicon[k] for k in sorted(lexicon)}, ensure_ascii=False, indent=2)
    return (
        "// GENERATED FILE \u2014 do not edit. Source: i18n/lexicon/*.json\n"
        "// Generator: tools/i18n/generate.py\n"
        "//\n"
        "// Topic keywords in the languages the source catalogue publishes in.\n"
        "// The same JSON generates the Android app's TopicLexiconL10n.kt, so the\n"
        "// two clients cannot classify the same story differently.\n"
        f"export default {body};\n"
    )


def main() -> int:
    check = "--check" in sys.argv
    base = load(BASE_TAG)
    if not base:
        print(f"no base catalogue at {CATALOGUES / (BASE_TAG + '.json')}", file=sys.stderr)
        return 2

    wanted: dict[pathlib.Path, str] = {}
    tags = sorted(p.stem for p in CATALOGUES.glob("*.json") if not p.stem.startswith("_"))
    for tag in tags:
        strings = load(tag)
        # Keys a translation carries that the base does not are almost always a
        # rename that was not propagated; silently dropping them would hide it.
        unknown = [k for k in strings if k not in base]
        if unknown:
            print(f"{tag}.json: {len(unknown)} key(s) not in en.json: {', '.join(unknown[:5])}", file=sys.stderr)
            return 2
        wanted[WEB_OUT / f"{tag}.json"] = render_web(tag, strings, base)
        if tag == BASE_TAG:
            wanted[ANDROID_RES / "values" / "strings.xml"] = render_base(base)
        else:
            qualifier = android_qualifier(tag)
            wanted[ANDROID_RES / f"values-{qualifier}" / "strings.xml"] = render_android(tag, strings, base)

    coverage = {tag: len(load(tag)) for tag in tags}
    shipped = [t for t in tags if coverage[t] > 0]
    wanted[LOCALE_CONFIG] = render_locale_config(shipped)
    wanted[COVERAGE_KT] = render_coverage(coverage, len(base))

    locales = parse_locales_kt()
    if not locales:
        print("could not read any locale out of Locales.kt", file=sys.stderr)
        return 2
    missing = [t for t in shipped if t not in locales]
    if missing:
        # A catalogue for a language the app does not list is a locale nobody
        # can choose. Silently generating it would hide the mistake.
        print(f"catalogue(s) with no entry in Locales.kt: {', '.join(missing)}", file=sys.stderr)
        return 2
    wanted[WEB_INDEX] = render_web_index(locales, coverage, len(base))

    lexicon = load_lexicon()
    wanted[LEXICON_KT] = render_lexicon_kt(lexicon)
    wanted[LEXICON_WEB] = render_lexicon_web(lexicon)
    stale_json = REPO / "web" / "js" / "topics-l10n.json"
    if stale_json.exists() and not check:
        stale_json.unlink()

    stale = []
    for path, content in wanted.items():
        current = path.read_text(encoding="utf-8") if path.exists() else None
        if current == content:
            continue
        stale.append(path.relative_to(REPO))
        if not check:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")

    # A locale removed from i18n/strings must not leave its generated output
    # behind, or the app keeps shipping a language nobody maintains.
    orphans = []
    for existing in list(ANDROID_RES.glob("values-b+*/strings.xml")) + list(WEB_OUT.glob("*.json")):
        if existing not in wanted:
            orphans.append(existing.relative_to(REPO))
            if not check:
                existing.unlink()
                if existing.parent.name.startswith("values-b+") and not any(existing.parent.iterdir()):
                    existing.parent.rmdir()

    if check and (stale or orphans):
        for p in stale:
            print(f"out of date: {p}")
        for p in orphans:
            print(f"orphaned:    {p}")
        print("\nRun: python3 tools/i18n/generate.py", file=sys.stderr)
        return 1

    total = len(base)
    lex_terms = sum(len(v) for v in lexicon.values())
    print(f"{len(tags)} locale(s), {total} strings; "
          f"{lex_terms} topic keyword(s) across {len(list(LEXICON_IN.glob('*.json')))} language(s).")
    for tag in tags:
        pct = round(100 * coverage[tag] / total) if total else 0
        print(f"  {tag:<8} {coverage[tag]:>4}/{total}  {pct:>3}%")
    if stale or orphans:
        print(f"wrote {len(stale)} file(s), removed {len(orphans)}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
