"""Generate K-apt region selector data from official active legal-dong TSV."""
import argparse
import csv
import json
from pathlib import Path

def generate(source):
    raw = source.read_bytes()
    for encoding in ("utf-8-sig", "cp949"):
        try:
            text = raw.decode(encoding)
            break
        except UnicodeDecodeError:
            continue
    else:
        raise ValueError("Unsupported encoding; expected UTF-8 or CP949")
    rows = csv.DictReader(text.splitlines(), delimiter="\t")
    if not {"법정동코드", "법정동명", "폐지여부"}.issubset(rows.fieldnames or []):
        raise ValueError("Expected official TSV columns: 법정동코드, 법정동명, 폐지여부")
    active = {}
    for row in rows:
        code = row["법정동코드"].strip()
        name = row["법정동명"].strip()
        if row["폐지여부"].strip() != "존재":
            continue
        if len(code) != 10 or not code.isascii() or not code.isdigit() or not name:
            raise ValueError("Invalid active legal-dong record")
        active[code] = name
    provinces = {c[:2]: n for c, n in active.items() if c[2:] == "00000000"}
    districts = {c[:5]: n for c, n in active.items() if c[2:] != "00000000" and c[5:] == "00000"}
    # Sejong and other exceptional hierarchies may have a zero district suffix.
    for code in active:
        if code[5:] != "00000" and code[:5] not in districts and code[:2] in provinces:
            districts[code[:5]] = provinces[code[:2]]
    towns = {c: n for c, n in active.items() if c[5:] != "00000" and c[:5] in districts}
    if len(provinces) < 15 or len(districts) < 200 or len(towns) < 10000:
        raise ValueError("Input does not look like a nationwide active legal-dong catalog")
    def entries(values):
        return [{"code": c, "name": n} for c, n in sorted(values.items())]
    return {"complete": True, "provinces": entries(provinces), "districts": entries(districts), "towns": entries(towns)}

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "frontend/public/data/kapt-regions.json")
    args = parser.parse_args()
    result = generate(args.source)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print("Generated:", args.output, {k: len(result[k]) for k in ("provinces", "districts", "towns")})
