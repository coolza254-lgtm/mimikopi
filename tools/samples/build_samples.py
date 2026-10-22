"""Build the ミミコピ piano sample pack from Salamander Grand Piano V3.

Source: https://github.com/sfzinstruments/SalamanderGrandPiano (Alexander Holm, CC BY 3.0),
48 kHz / 24-bit FLAC, 30 sampled notes (minor thirds, A0..C8) x 16 velocity layers.

What this does (all changes are listed in the pack README, as CC BY requires):
  * keeps 8 of the 16 velocity layers (v2, v4, ... v16) and merges their velocity ranges
  * trims the silence before each attack using the per-layer offsets from the SFZ (keeps 2 ms)
  * trims the decay tail where it falls 60 dB below the sample's peak (with a per-register cap)
    and fades out the last 300 ms
  * applies one global gain so the loudest sample peaks at -1 dBFS (relative levels are kept)
  * converts to 16-bit with TPDF dither, keeps 48 kHz stereo, writes FLAC
Outputs in --out: samples-v<N>.zip (manifest.json + FLACs + license/readme) and samples-v<N>.json.

Usage: python3 build_samples.py --src SalamanderGrandPiano --out out --version 1
       [--url-base https://github.com/OWNER/REPO/releases/download]
"""
import argparse
import hashlib
import json
import pathlib
import re
import zipfile

import numpy as np
import soundfile as sf

RATE = 48000
PREROLL = 96  # samples kept before the SFZ offset (2 ms)
FADE_IN = 48
FADE_OUT_S = 0.3
TAIL_DB = -60.0
LAYERS = [2, 4, 6, 8, 10, 12, 14, 16]


def parse_sfz(src: pathlib.Path):
    data = src / "Data"
    regions = []
    for line in (data / "region.txt").read_text().splitlines():
        m = re.search(r"region_label=(\d+).*?lokey=(\d+) hikey=(\d+) pitch_keycenter=(\d+) sample=(\S+?)\$VEL", line)
        if m:
            label, lo, hi, center, name = m.groups()
            rel = re.search(r"ampeg_release=(\d+(?:\.\d+)?)", line)
            regions.append({
                "index": int(label), "lo": int(lo), "hi": int(hi), "keyCenter": int(center), "name": name,
                # Regions with an explicit release have no dampers (top 20 keys).
                "damped": rel is None, "releaseSec": float(rel.group(1)) if rel else 0.35,
            })
    vel_ranges = {}
    for line in (data / "notes.txt").read_text().splitlines():
        m = re.search(r"vel_(\d+)\.txt.*lovel=(\d+) hivel=(\d+)", line)
        if m:
            vel_ranges[int(m.group(1))] = (int(m.group(2)), int(m.group(3)))
    offsets = {}
    for v in range(1, 17):
        text = (data / f"vel_{v:02d}.txt").read_text()
        offsets[v] = {int(a): int(b) for a, b in re.findall(r"\$OFF(\d+) (\d+)", text)}
    assert len(regions) == 30, f"expected 30 regions, got {len(regions)}"
    assert len(vel_ranges) == 16
    return regions, vel_ranges, offsets


def merged_ranges(vel_ranges):
    """Each kept layer v covers its own range plus the dropped layer v-1 below it."""
    return {v: (vel_ranges[v - 1][0], vel_ranges[v][1]) for v in LAYERS}


def max_seconds(key_center: int) -> float:
    if key_center <= 48:
        return 14.0
    if key_center <= 72:
        return 10.0
    if key_center <= 88:
        return 7.0
    return 5.0


def process(audio: np.ndarray, offset: int, key_center: int) -> np.ndarray:
    if audio.ndim == 1:
        audio = np.stack([audio, audio], axis=1)
    start = max(0, offset - PREROLL)
    x = audio[start:].astype(np.float64)
    # Tail: last 10 ms window whose RMS is within TAIL_DB of the peak window.
    win = RATE // 100
    n = len(x) // win
    rms = np.sqrt(np.mean(x[: n * win].reshape(n, win, 2) ** 2, axis=(1, 2)) + 1e-20)
    db = 20 * np.log10(rms / rms.max())
    above = np.nonzero(db > TAIL_DB)[0]
    end = min(len(x), (above[-1] + 1) * win + RATE // 5, int(max_seconds(key_center) * RATE))
    x = x[:end]
    fi = min(FADE_IN, len(x))
    x[:fi] *= (0.5 - 0.5 * np.cos(np.linspace(0, np.pi, fi)))[:, None]
    fo = min(int(FADE_OUT_S * RATE), len(x))
    x[-fo:] *= (0.5 + 0.5 * np.cos(np.linspace(0, np.pi, fo)))[:, None]
    return x


def to_int16(x: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    tpdf = (rng.random(x.shape) - rng.random(x.shape)) / 32768.0
    return np.clip(np.round((x + tpdf) * 32767.0), -32768, 32767).astype(np.int16)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--version", type=int, required=True)
    ap.add_argument("--url-base", default="https://github.com/coolza254-lgtm/mimikopi/releases/download")
    args = ap.parse_args()
    src, out = pathlib.Path(args.src), pathlib.Path(args.out)
    work = out / "work"
    work.mkdir(parents=True, exist_ok=True)

    regions, vel_ranges, offsets = parse_sfz(src)
    ranges = merged_ranges(vel_ranges)
    processed = {}
    peak = 0.0
    for r in regions:
        for v in LAYERS:
            audio, rate = sf.read(src / "Samples" / f"{r['name']}v{v}.flac", always_2d=True)
            assert rate == RATE, f"{r['name']}v{v}: {rate} Hz"
            x = process(audio, offsets[v][r["index"]], r["keyCenter"])
            processed[(r["index"], v)] = x
            peak = max(peak, float(np.abs(x).max()))
    gain = 10 ** (-1 / 20) / peak
    print(f"global gain {20 * np.log10(gain):+.2f} dB")

    rng = np.random.default_rng(1234)  # deterministic dither -> reproducible pack
    manifest_regions = []
    total_frames = 0
    files = []
    for r in regions:
        layers = []
        for v in LAYERS:
            pcm = to_int16(processed[(r["index"], v)] * gain, rng)
            fname = f"r{r['index']:02d}_v{v:02d}.flac"
            sf.write(work / fname, pcm, RATE, subtype="PCM_16", format="FLAC")
            files.append(fname)
            total_frames += len(pcm)
            lo, hi = ranges[v]
            layers.append({"file": fname, "loVel": lo, "hiVel": hi, "frames": len(pcm)})
        manifest_regions.append({
            "lo": r["lo"], "hi": r["hi"], "keyCenter": r["keyCenter"],
            "damped": r["damped"], "releaseSec": r["releaseSec"], "layers": layers,
        })

    manifest = {
        "format": 1,
        "packVersion": args.version,
        "name": "Salamander Grand Piano V3 (ミミコピ edition)",
        "sampleRate": RATE,
        "channels": 2,
        "bitsPerSample": 16,
        "regions": manifest_regions,
    }
    (work / "manifest.json").write_text(json.dumps(manifest, indent=1))
    (work / "LICENSE-Salamander.txt").write_text((src / "LICENSE").read_text())
    (work / "README.txt").write_text(
        "Salamander Grand Piano V3 by Alexander Holm, licensed CC BY 3.0\n"
        "https://creativecommons.org/licenses/by/3.0/\n"
        "Source: https://github.com/sfzinstruments/SalamanderGrandPiano (archive.org/details/SalamanderGrandPianoV3)\n\n"
        "Changes made for the ミミコピ app: 8 of 16 velocity layers kept (v2..v16 even); leading silence\n"
        "trimmed using the SFZ offsets; decay tails trimmed at -60 dB (capped per register) with a 300 ms\n"
        "fade-out; one global gain to -1 dBFS peak; converted to 16-bit with TPDF dither.\n"
    )

    zip_name = f"samples-v{args.version}.zip"
    zip_path = out / zip_name
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_STORED) as z:
        for name in ["manifest.json", "README.txt", "LICENSE-Salamander.txt", *files]:
            z.write(work / name, name)
    sha = hashlib.sha256(zip_path.read_bytes()).hexdigest()
    descriptor = {
        "schema": 1,
        "packVersion": args.version,
        "url": f"{args.url_base}/samples-v{args.version}/{zip_name}",
        "size": zip_path.stat().st_size,
        "sha256": sha,
        "decodedBytes": total_frames * 2 * 2,
        "files": len(files),
        "license": "CC BY 3.0 (Salamander Grand Piano V3, Alexander Holm)",
    }
    (out / f"samples-v{args.version}.json").write_text(json.dumps(descriptor, indent=1))
    print(json.dumps(descriptor, indent=1))


if __name__ == "__main__":
    main()
