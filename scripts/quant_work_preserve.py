"""Create-only local before-images. No DB, network, deletion or in-place restore."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil


def digest(path):
    hasher = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            hasher.update(block)
    return hasher.hexdigest()


def create_snapshot(root, paths, output):
    root, output = Path(root).resolve(), Path(output).resolve()
    sources = {}
    for candidate in paths:
        original = Path(candidate)
        path = original.resolve()
        try:
            relative = path.relative_to(root).as_posix()
        except ValueError as error:
            raise ValueError("Snapshot sources must be inside the repository") from error
        if not path.is_file() or original.is_symlink() or ".git" in Path(relative).parts:
            raise ValueError("Source must be an existing regular work file")
        sources[relative] = path
    if not sources:
        raise ValueError("No source files selected")
    output.relative_to(root)
    output.mkdir(parents=True, exist_ok=False)
    (output / "objects").mkdir()
    records = []
    for relative, source in sorted(sources.items()):
        sha = digest(source)
        obj = "objects/" + sha + ".bin"
        target = output / obj
        if not target.exists():
            shutil.copyfile(source, target)
        if digest(target) != sha or digest(source) != sha:
            raise ValueError("Source changed during preservation; snapshot is not complete")
        records.append(dict(path=relative, object=obj, sha256=sha, bytes=target.stat().st_size))
    manifest = dict(format="QUANT_LOCAL_BEFORE_IMAGES_V1", createdAtUtc=datetime.now(timezone.utc).isoformat(),
                    root=str(root), files=records, offDeviceBackup=False,
                    recovery="Verify snapshot, then copy chosen object to a separate inspection directory; never overwrite live files blindly")
    with (output / "manifest.json").open("x", encoding="utf-8") as stream:
        json.dump(manifest, stream, indent=2, ensure_ascii=False)
    verify_snapshot(output)
    return manifest


def verify_snapshot(output):
    output = Path(output).resolve()
    manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
    if manifest.get("format") != "QUANT_LOCAL_BEFORE_IMAGES_V1" or not manifest.get("files"):
        raise ValueError("Unsupported or empty snapshot")
    seen = set()
    for record in manifest["files"]:
        sha = record["sha256"]
        relative = Path(record["path"])
        if (not re.fullmatch("[0-9a-f]{64}", sha) or record["object"] != "objects/" + sha + ".bin"
                or relative.is_absolute() or ".." in relative.parts or record["path"] in seen):
            raise ValueError("Invalid snapshot identity/path")
        seen.add(record["path"])
        obj = output / record["object"]
        if not obj.is_file() or obj.is_symlink() or obj.stat().st_size != record["bytes"] or digest(obj) != sha:
            raise ValueError("Preserved object is missing or corrupt")
    return dict(status="VERIFIED", files=len(seen), offDeviceBackup=False)


def default_paths(root):
    root = Path(root)
    paths = [root / "pom.xml", root / ".gitignore", root / "AGENTS.md"]
    for folder in ("scripts", "tests/python", "src/main/java", "src/test/java", "src/test/python", "src/main/resources"):
        paths += [p for p in (root / folder).rglob("*")
                  if p.is_file() and p.suffix in {".py", ".java", ".xml", ".sql", ".html", ".css", ".js", ".properties", ".yml"}]
    for pattern in ("data/quant-forward/**/*.json", "data/quant-forward-validation-*/*/*.json",
                    "data/quant-candidate-forward-*/*/*.json", "data/quant-candidate-evaluation-*/*/bundle.json",
                    "docs/quant*.md", "docs/superpowers/plans/*quant*.md"):
        paths += list(root.glob(pattern))
    # Safe financial source/metadata only; never read the old60 target/outcome files.
    for name in ("protocol.json", "inputs.json", "merged-financials.json", "revenue-overlay-audit.json", "remaining-revenue-targets.json"):
        candidate = root / "tmp/quant-full-financial-study-20261101-v3" / name
        if candidate.exists():
            paths.append(candidate)
    for directory in ("quant-amgn-quarter-originals-20261101-v1", "quant-gs-period-reconciliation-20261101-v1",
                      "quant-kvue-trading-cessation-20261103-v1"):
        paths += [p for p in (root / "tmp" / directory).rglob("*") if p.is_file()]
    return paths


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("create", "verify"))
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    result = (create_snapshot(root, default_paths(root), args.output) if args.mode == "create"
              else verify_snapshot(args.output))
    print(json.dumps({key: value for key, value in result.items() if key != "files"}, indent=2))
    print("Preserved files:", len(result["files"]) if isinstance(result["files"], list) else result["files"])
