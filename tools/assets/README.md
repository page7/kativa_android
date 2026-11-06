# Asset pipeline

> Rule: every image and button icon in the app must come from `assets/manifest.json`; artwork is
> authored as **SVG and rendered to PNG**. See `docs/ASSETS.md` for the catalogue and the colour spec.

Generated PNGs under `app/src/main/res/` **are tracked in git**; everything under
`assets/generated/` is an intermediate and is gitignored.

## Source resolution

For each manifest entry the generator picks the first file that exists:

| Priority | Path | Meaning |
| --- | --- | --- |
| 1 | `assets/svg/<id>.svg` | Delivered artwork (the normal case) |
| 2 | `assigns/<id>.svg` | Maintainer drop-in, same naming rule |
| 3 | `assigns/kavita.svg` | Legacy fallback, `launcher` / `launcher-foreground` only |
| 4 | — | Script placeholder (always resolvable, never blocks a build) |

The file name **must equal the manifest `id`** (`ic_home.svg` → `ic_home`). Every run prints the
resolved source per entry and finishes with the list of entries that are still placeholders, so a
silently ignored drop-in is impossible.

## Commands

```bash
./gradlew :app:generateAssets           # render every entry into app/src/main/res
./gradlew :app:checkAssetsRegistered    # fail if a PNG under res/ is not in the manifest
./gradlew :tools:assets:test            # generator unit tests
```

## Colour

Icons are tinted at runtime by Material3 `Icon(...)` (`ColorFilter` with `BlendMode.SrcIn`), which
keeps alpha and discards RGB. Author them as **single-colour, fully opaque, transparent-background**
artwork — the colour you pick is irrelevant. Full-colour illustrations go through `Image(...)`,
are not tinted, and must therefore carry their own colours. Details in `docs/ASSETS.md` §1.1.
