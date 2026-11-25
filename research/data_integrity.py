"""Strict byte-level validation for research dataset part ZIP manifests.
Offline-only; no trading or network capabilities.
"""
from __future__ import annotations
import hashlib
import re
import zipfile
from pathlib import PurePosixPath
from typing import Any
from urllib.parse import urlparse

_SHA256 = re.compile(r"^[0-9a-fA-F]{64}$")

def _record_member(record: dict[str, Any]) -> str:
    for key in ("member", "path", "filename", "file", "name"):
        value = record.get(key)
        if isinstance(value, str) and value:
            return value
    # The production Binance manifests identify each source archive by URL,
    # symbol and month. In the packaged dataset the original archive bytes are
    # retained as SYMBOL/SOURCE_BASENAME.zip; normalized_csv is not the hash target.
    source_url = record.get("source_url")
    symbol = record.get("symbol")
    if isinstance(source_url, str) and isinstance(symbol, str) and symbol:
        basename = PurePosixPath(urlparse(source_url).path).name
        if basename and basename.endswith(".zip"):
            return f"{symbol}/{basename}"
    raise ValueError("DATA_INTEGRITY: manifest file record has no member path")

def _record_digest(record: dict[str, Any]) -> str:
    for key in ("sha256", "archive_sha256", "checksum_sha256", "sha256sum"):
        value = record.get(key)
        if isinstance(value, str) and _SHA256.fullmatch(value):
            return value.lower()
    raise ValueError("DATA_INTEGRITY: manifest file record has no valid SHA-256")

def _sha256_member(zf: zipfile.ZipFile, member: str) -> str:
    digest = hashlib.sha256()
    try:
        with zf.open(member, "r") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    except (KeyError, OSError, zipfile.BadZipFile) as exc:
        raise ValueError(f"DATA_INTEGRITY: cannot read manifest member {member!r}") from exc
    return digest.hexdigest()

def validate_part_manifest(zf: zipfile.ZipFile, manifest: dict[str, Any], part_path: str = "<archive>") -> None:
    """Verify counts and recompute SHA-256 for every declared ZIP member.

    Fail closed on missing digest/member, duplicate member, false verification
    flag, count mismatch, or digest mismatch. External-source archive hashes
    cannot be proven from normalized-data ZIPs unless original bytes are present.
    """
    expected = manifest.get("monthly_archives_expected")
    verified = manifest.get("monthly_archives_verified")
    records = manifest.get("files")
    if (not isinstance(expected, int) or isinstance(expected, bool) or expected <= 0
            or not isinstance(verified, int) or isinstance(verified, bool)
            or expected != verified):
        raise ValueError(f"DATA_INTEGRITY: archive count mismatch in {part_path}")
    if not isinstance(records, list) or len(records) != expected:
        raise ValueError(f"DATA_INTEGRITY: manifest records/count mismatch in {part_path}")
    names = set(zf.namelist())
    seen: set[str] = set()
    for index, record in enumerate(records):
        if not isinstance(record, dict):
            raise ValueError(f"DATA_INTEGRITY: malformed file record {index} in {part_path}")
        if record.get("checksum_verified") is not True:
            raise ValueError(f"DATA_INTEGRITY: checksum flag not verified for record {index} in {part_path}")
        member = _record_member(record)
        if member in seen:
            raise ValueError(f"DATA_INTEGRITY: duplicate manifest member {member!r} in {part_path}")
        seen.add(member)
        if member not in names:
            raise ValueError(f"DATA_INTEGRITY: manifest member {member!r} is absent from {part_path}; external source hashes require original bytes")
        expected_sha = _record_digest(record)
        actual_sha = _sha256_member(zf, member)
        if actual_sha != expected_sha:
            raise ValueError(f"DATA_INTEGRITY: SHA-256 mismatch for {member!r} in {part_path}: expected {expected_sha}, got {actual_sha}")
