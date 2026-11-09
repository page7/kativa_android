# Delivered artwork

Drop the final SVG here, **file name = manifest id** (`ic_home.svg`, `logo_full.svg`, …).

The renderer (`./gradlew :app:generateAssets`) prefers files in this folder over `assigns/` and over
the generated placeholders, then writes PNGs into `app/src/main/res/drawable-*` / `mipmap-*`.
`assets/generated/` is intermediate output and is not committed.

Icons must be single-colour, fully opaque artwork on a transparent background — they are tinted at
runtime. Full-colour illustrations must paint their own colours. See `docs/ASSETS.md` §1.1.

`./gradlew :app:checkAssetsRegistered` fails on a file here whose name is not a manifest id, so a
typo cannot be mistaken for "the artwork did not take effect".

The 24dp icons in this folder are generated from [Tabler Icons](https://tabler.io/icons) v3.51.0
(MIT, see `LICENSE.txt`). Re-fetch/normalise them with `tools/fetch-tabler-icons.ps1` — do not edit
those SVGs by hand. `DeliveredIconsTest` also fails if an entry is marked `final` in
`assets/manifest.json` without a source file here.
