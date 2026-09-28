import assert from 'node:assert/strict';
import test from 'node:test';

import { classifyItem } from '../js/topics.js';

/**
 * The topic classifier decides which band of the Loom a story lands in, so when
 * it is wrong the app's central claim — "here is what flowed and what you read"
 * — is wrong with it, silently.
 *
 * It used to build every keyword matcher as `new RegExp('\\b' + term + '\\b')`.
 * JavaScript defines `\b` against `[A-Za-z0-9_]` and nothing else, so it can
 * never fire adjacent to a Devanagari, Arabic, Thai or CJK character. Every item
 * from a non-English feed classified as 'general': the Loom collapsed to one
 * band and the Contrast dumbbells emptied, with no error anywhere. With the
 * catalogue now carrying feeds in eleven Indian languages, that was most of the
 * catalogue.
 *
 * These tests are deliberately about the *boundary mechanism* rather than about
 * any particular keyword — the English lexicon is a separate, later problem, and
 * translating it would have been useless while the matcher could not fire.
 */

test('English keywords still classify, and still respect word boundaries', () => {
  assert.equal(classifyItem({ title: 'Parliament votes on new legislation' }), 'politics');
  assert.equal(classifyItem({ title: 'Inflation and interest rate worry investors' }), 'business');
  assert.equal(classifyItem({ title: 'Airstrike hits militant position' }), 'conflict');
  // "warden" must not match "war" — the boundary is load-bearing, not decorative.
  assert.equal(classifyItem({ title: 'The warden opened the gate' }), 'general');
});

test("a feed's own category still wins over keywords", () => {
  assert.equal(classifyItem({ category: 'Business', title: 'Airstrike hits militant position' }), 'business');
});

/**
 * The regression guard. These assert the matcher *construction* can fire in
 * each script, independently of which words are in the lexicon — reproduced
 * here because `termMatcher` is module-private by design.
 */
const WORD_CHAR = '[\\p{L}\\p{N}\\p{M}]';
const UNSPACED_SCRIPT = /[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}\p{Script=Thai}\p{Script=Khmer}\p{Script=Lao}]/u;

function termMatcher(term) {
  const escaped = term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  if (UNSPACED_SCRIPT.test(term)) return new RegExp(escaped, 'iu');
  return new RegExp('(^|(?!' + WORD_CHAR + ').)' + escaped + '(?!' + WORD_CHAR + ')', 'iu');
}

test('word boundaries fire in every script the catalogue ships', () => {
  const cases = [
    ['Devanagari, mid-sentence', 'राजनीति', 'आज की राजनीति खबर', true],
    ['Devanagari, at start', 'राजनीति', 'राजनीति की खबर', true],
    ['Devanagari, partial word must not match', 'राजनी', 'राजनीति की खबर', false],
    ['Urdu', 'سیاست', 'آج کی سیاست خبر', true],
    ['Tamil', 'அரசியல்', 'இன்றைய அரசியல் செய்தி', true],
    ['Telugu', 'రాజకీయ', 'నేటి రాజకీయ వార్తలు', true],
    ['Bengali', 'রাজনীতি', 'আজকের রাজনীতি খবর', true],
    ['Latin, bounded', 'war', 'The warden opened the gate', false],
    ['Latin, matches', 'war', 'The war continues', true],
  ];
  for (const [label, term, text, expected] of cases) {
    assert.equal(termMatcher(term).test(text), expected, label);
  }
});

test('scripts written without spaces match by containment', () => {
  // A "word boundary" is not a meaningful idea inside a run of Han or Thai, so
  // the boundary form could never match mid-sentence there.
  assert.equal(termMatcher('政治').test('今天的政治新闻'), true, 'Chinese');
  assert.equal(termMatcher('経済').test('今日の経済ニュース'), true, 'Japanese');
  assert.equal(termMatcher('การเมือง').test('ข่าวการเมืองวันนี้'), true, 'Thai');
});

test('no keyword regex throws on construction', () => {
  // A regex construct an older engine rejects throws at build time and takes
  // the whole module — and therefore the app — down with it. Lookbehind is
  // avoided for exactly this reason (Safari only gained it in 16.4).
  for (const term of ['war', 'राजनीति', 'سیاست', '政治', 'interest rate', 'c++']) {
    assert.doesNotThrow(() => termMatcher(term), `constructing matcher for ${term}`);
  }
});

/**
 * The localized lexicon, and the fact that both clients read the same one.
 *
 * `TopicLexiconL10n.kt` and `topics-l10n.js` are generated from the same
 * `i18n/lexicon/` files, so they cannot disagree — but only as long as nobody
 * hand-edits one of the generated files, which is exactly the kind of thing
 * that works locally and is silently reverted by the next generator run.
 */
test('headlines classify in every script the catalogue ships', () => {
  const cases = [
    ['लोकसभा चुनाव में मतदान शुरू', 'politics'],
    ['হাসপাতালে টিকা কর্মসূচি', 'health'],
    ['స్టాక్ మార్కెట్‌లో ద్రవ్యోల్బణం ఆందోళన', 'business'],
    ['கிரிக்கெட் போட்டியில் வெற்றி', 'sport'],
    ['وزیراعظم نے پالیسی کا اعلان کیا', 'politics'],
    ['ಇಸ್ರೋ ಉಪಗ್ರಹ ಉಡಾವಣೆ', 'science'],
    ['വെള്ളപ്പൊക്കം: കാലാവസ്ഥ മുന്നറിയിപ്പ്', 'climate'],
    ['ચૂંટણી પ્રચાર શરૂ', 'politics'],
    ['ਹਸਪਤਾਲ ਵਿੱਚ ਟੀਕਾ ਮੁਹਿੰਮ', 'health'],
    ['ବନ୍ୟା ପରିସ୍ଥିତି ଗମ୍ଭୀର', 'climate'],
    ['क्रिकेट सामन्यात विजय', 'sport'],
  ];
  for (const [title, expected] of cases) {
    assert.equal(classifyItem({ title }), expected, `"${title}"`);
  }
});

test('the generated lexicon is the same one the Android app compiles', async () => {
  const fs = await import('node:fs');
  const path = await import('node:path');
  const repo = path.resolve(import.meta.dirname, '..', '..');
  const web = (await import('../js/topics-l10n.js')).default;
  const kotlin = fs.readFileSync(
    path.join(repo, 'core/model/src/commonMain/kotlin/xyz/mdhv/riverwip/model/TopicLexiconL10n.kt'),
    'utf8',
  );
  for (const [topic, terms] of Object.entries(web)) {
    assert.ok(kotlin.includes(`"${topic}" to listOf(`), `${topic} is missing from the Kotlin lexicon`);
    for (const term of terms) {
      assert.ok(kotlin.includes(`"${term}"`), `"${term}" (${topic}) is in the web lexicon but not the Kotlin one`);
    }
  }
});
