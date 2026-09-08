"""Download three deterministic human recordings per language for opt-in ASR QA.

FLEURS is CC-BY-4.0: https://huggingface.co/datasets/google/fleurs
Only the first three WAV members of each test archive are streamed; archives
are never extracted to disk. Audio and the manifest stay in the ignored build/.
Requires requests (pip install requests).
"""
import csv
import io
import json
from pathlib import Path
import tarfile

import requests

REVISION = "70bb2e84b976b7e960aa89f1c648e09c59f894dd"
ROOT = Path(__file__).resolve().parents[1] / "composeApp/build/local-asr-test/fleurs"
BASE = f"https://hf-mirror.com/datasets/google/fleurs/resolve/{REVISION}/data"


def main():
    ROOT.mkdir(parents=True, exist_ok=True)
    manifest = []
    for language, config in [("zh", "cmn_hans_cn"), ("ja", "ja_jp"), ("en", "en_us")]:
        index = requests.get(f"{BASE}/{config}/test.tsv", timeout=(15, 60))
        index.raise_for_status()
        records = {row[1]: row for row in csv.reader(io.StringIO(index.text), delimiter="\t")}
        selected = []
        with requests.get(f"{BASE}/{config}/audio/test.tar.gz", stream=True, timeout=(15, 60)) as response:
            response.raise_for_status()
            with tarfile.open(fileobj=response.raw, mode="r|gz") as archive:
                for member in archive:
                    name = Path(member.name).name
                    if not member.isfile() or name not in records:
                        continue
                    if not name.endswith(".wav") or member.size > 10_000_000:
                        continue
                    row = records[name]
                    destination = f"{language}-{name}"
                    with archive.extractfile(member) as audio:
                        (ROOT / destination).write_bytes(audio.read())
                    selected.append({
                        "language": language, "file": destination, "text": row[2],
                        "gender": row[6], "dataset": "google/fleurs", "config": config,
                        "revision": REVISION, "license": "CC-BY-4.0",
                    })
                    if len(selected) == 3:
                        break
        if len(selected) != 3:
            raise RuntimeError(f"Could not retrieve three recordings for {config}")
        manifest.extend(selected)
        print(f"{language}: downloaded {len(selected)} FLEURS recordings", flush=True)
    (ROOT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
