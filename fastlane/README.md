# fastlane/

Store-listing metadata skeleton (brief §P7: "F-Droid metadata for `foss`
(license-gated), Play listing skeleton (name RESERVED)").

`fastlane/metadata/android/<locale>/` is a single, shared convention both
F-Droid (which reads it directly from an app's own source repo — see
[F-Droid's docs on descriptions, graphics, and screenshots][fdroid-docs]) and
Google Play (via fastlane's own `supply` tool, or the Triple-T Gradle Play
Publisher plugin) understand, so one skeleton serves both listings the brief
asks for.

## Why every text file here is a placeholder, not copy

Two RESERVED decisions (see `STATE.md` §RESERVED) block writing a real,
publishable listing:

1. **The app name.** `title.txt` is the single most user-facing surface this
   repo could get wrong by guessing — it *is* the name decision, just made in
   a metadata file instead of code. It stays a placeholder until the owner
   decides.
2. **The license.** F-Droid will not include an app at all without an
   OSI/FSF-approved license and a real `LICENSE` file. This fork now carries
   an MIT `LICENSE` (owner-chosen), which satisfies that requirement.

`short_description.txt`/`full_description.txt` hold draft, functional
copy — what the app *does* — with no name, no metaphor/mythological language,
and no taxonomy-label decisions, per §RESERVED. Treat them as a draft to
revise once naming lands, not a final listing.

## What's here vs. what's still missing

- `title.txt`, `short_description.txt`, `full_description.txt` — present,
  draft/placeholder (see above).
- `changelogs/1.txt` — present, matches `versionCode = 1` in
  `app/build.gradle.kts`.
- `images/` — **empty**. Icon, feature graphic, and phone screenshots all
  need either a real device/emulator (this build session has neither) or the
  final icon (RESERVED, brief §9: "icon and any visual metaphor language").
  `scripts/capture_screenshots.sh` is the intended way to populate
  `images/phoneScreenshots/` once someone has a device to run it on.

## When the RESERVED decisions land
Fill in `title.txt` with the real name, rewrite the descriptions around it,
add the icon/feature graphic/screenshots, and add the SPDX identifier
alongside the `LICENSE` file.

[fdroid-docs]: https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/
