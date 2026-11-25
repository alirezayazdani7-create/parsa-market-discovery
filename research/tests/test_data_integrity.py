"""Unit tests for byte-level research dataset manifest validation."""
import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from data_integrity import validate_part_manifest

class ManifestHashValidationTests(unittest.TestCase):
    def make_archive(self, payload=b"normalized-candle-data", digest_override=None,
                     omit_digest=False, omit_member=False, count=1):
        tmp = tempfile.TemporaryDirectory()
        path = Path(tmp.name) / "part.zip"
        digest = digest_override or hashlib.sha256(payload).hexdigest()
        record = {"member": "BTCUSDT/2025-01.normalized.csv", "checksum_verified": True}
        if not omit_digest:
            record["sha256"] = digest
        manifest = {"monthly_archives_expected": count, "monthly_archives_verified": count, "files": [record]}
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
            if not omit_member:
                z.writestr(record["member"], payload)
            z.writestr("MANIFEST.json", json.dumps(manifest))
        return tmp, zipfile.ZipFile(path)

    def test_exact_member_hash_passes(self):
        tmp, zf = self.make_archive()
        with tmp, zf:
            validate_part_manifest(zf, json.loads(zf.read("MANIFEST.json")))

    def test_tampered_member_fails_hash(self):
        tmp, zf = self.make_archive(digest_override="0" * 64)
        with tmp, zf, self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            validate_part_manifest(zf, json.loads(zf.read("MANIFEST.json")))

    def test_missing_digest_fails_closed(self):
        tmp, zf = self.make_archive(omit_digest=True)
        with tmp, zf, self.assertRaisesRegex(ValueError, "no valid SHA-256"):
            validate_part_manifest(zf, json.loads(zf.read("MANIFEST.json")))

    def test_missing_member_fails_closed(self):
        tmp, zf = self.make_archive(omit_member=True)
        with tmp, zf, self.assertRaisesRegex(ValueError, "is absent"):
            validate_part_manifest(zf, json.loads(zf.read("MANIFEST.json")))

    def test_manifest_count_mismatch_fails(self):
        tmp, zf = self.make_archive(count=2)
        with tmp, zf, self.assertRaisesRegex(ValueError, "records/count mismatch"):
            validate_part_manifest(zf, json.loads(zf.read("MANIFEST.json")))

if __name__ == "__main__":
    unittest.main()
