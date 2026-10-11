# Credits and licenses

## Brand
- **Logo and app icon:** the original images (`docs/brand/icon-source.png`, `docs/brand/wordmark-source.png`)
  were generated with an AI image tool by the user for personal use. The icon was redrawn by hand as
  vector paths (`tools/brand/make_icon.py`).
- **Wordmark lettering:** [Zen Maru Gothic](https://github.com/googlefonts/zen-marugothic) Bold,
  © 2021 The Zen Maru Gothic Project Authors, SIL Open Font License 1.1. The glyphs are converted to
  vector outlines (`tools/brand/make_wordmark.py`); the font file itself is not shipped.

## Fonts
- UI text uses the device's system font (covers Thai and Japanese on Samsung One UI). No font is bundled yet.

## Libraries (Apache License 2.0)
- Kotlin, kotlinx.coroutines, kotlinx.serialization (JetBrains)
- AndroidX: Core, Activity, Lifecycle, Navigation, Compose, Material 3, Room (Google)

## Piano samples
- **Salamander Grand Piano V3** by Alexander Holm (Yamaha C5), licensed **CC BY 3.0**
  (https://creativecommons.org/licenses/by/3.0/). Source: https://github.com/sfzinstruments/SalamanderGrandPiano
  (originally archive.org/details/SalamanderGrandPianoV3). License confirmed from the repository's LICENSE file.
- Changes made for ミミコピ (`tools/samples/build_samples.py`): 8 of 16 velocity layers kept; leading silence
  trimmed using the SFZ offsets; decay tails trimmed at -60 dB (capped per register) with a 300 ms fade-out;
  one global gain to -1 dBFS; converted to 16-bit with TPDF dither. The pack is downloaded by the app from the
  public releases repo; it is not stored in git.
- Considered and not used: University of Iowa MIS piano (only 3 dynamics, small-room recording).

## Audio
- **Oboe** (Google), Apache License 2.0, linked as a prefab package (`com.google.oboe:oboe`).

## Music notation
- **Bravura** (Steinberg Media Technologies GmbH), SMuFL music font, SIL Open Font License 1.1 with Reserved
  Font Name "Bravura". Shipped unmodified (`app/src/main/res/font/bravura.otf`) from
  https://github.com/steinbergmedia/bravura (redist/otf). Licence confirmed from the repository's LICENSE.txt.
