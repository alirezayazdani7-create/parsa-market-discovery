# PARSA — Original Input Hash Diagnostic

**Status:** `PROVENANCE_DISCREPANCY_NARROWED_NOT_FULLY_RESOLVED`

## Findings

- Compared the 198 monthly raw Binance archive SHA-256 entries in the recovered November 15 manifest against the three recovered dataset-part manifests: **198/198 exact matches**.
- Tested whether each original per-asset `input_sha256` is SHA-256 of the float64 byte representation of `np.diff(np.log(close))` reconstructed from the verified normalized candles.
- Exact input-hash matches: **2/9** (PEPEUSDT and SHIBUSDT).

| Asset | Monthly archive hashes | Input-array hash |
|---|---:|---|
| ADAUSDT | 22/22 | MISMATCH |
| BTCUSDT | 22/22 | MISMATCH |
| DOGEUSDT | 22/22 | MISMATCH |
| ETHUSDT | 22/22 | MISMATCH |
| PEPEUSDT | 22/22 | MATCH |
| SHIBUSDT | 22/22 | MATCH |
| SOLUSDT | 22/22 | MISMATCH |
| WIFUSDT | 22/22 | MISMATCH |
| XRPUSDT | 22/22 | MISMATCH |

## Interpretation

The recovered raw monthly archive checksums match the original November 15 manifest for all 198 monthly files. This rules out changed source archive bytes as the explanation for the seven input-hash mismatches.

The tested input hash recipe matches PEPEUSDT and SHIBUSDT exactly, but not the other seven symbols. The remaining discrepancy is therefore in per-asset input construction, ordering, column selection, or the original hash-generation procedure. The original experiment source code remains absent.

**Do not overwrite the original hashes or describe the independent rerun as an exact replay.**

## Required evidence to close the issue

Recover the original experiment runner (especially data loading and input hashing) or the exact per-asset serialized input arrays/checkpoint files from the original run.
