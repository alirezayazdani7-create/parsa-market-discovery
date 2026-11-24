#!/usr/bin/env python3
"""Paired moving-block bootstrap audit for the older blind next-candle pilot CSV.

This is deliberately separate from multi_period_walk_forward.json. The CSV
contains only BTCUSDT and ETHUSDT, one fold and 1,000 rows per asset. It cannot
replace the 9-asset, 6-fold study or produce Holm-adjusted inference for that
study. Requires numpy.
"""
from __future__ import annotations
import argparse
import csv
import json
from pathlib import Path
import numpy as np

COMPARATORS = ("hmm_log_score", "zero_log_score", "mean_log_score")


def mbb_mean(x: np.ndarray, block: int, reps: int, rng: np.random.Generator):
    n = len(x)
    if n < block:
        raise ValueError("sample shorter than block")
    starts = np.arange(n - block + 1)
    blocks_per_draw = int(np.ceil(n / block))
    boot = np.empty(reps, dtype=float)
    for b in range(reps):
        chosen = rng.choice(starts, size=blocks_per_draw, replace=True)
        indices = np.concatenate([np.arange(s, s + block) for s in chosen])[:n]
        boot[b] = x[indices].mean()
    observed = float(x.mean())
    ci_low, ci_high = np.quantile(boot, [0.025, 0.975])
    centered_null = boot - observed
    p = (1 + np.count_nonzero(np.abs(centered_null) >= abs(observed))) / (reps + 1)
    return observed, float(ci_low), float(ci_high), float(p)


def holm_adjust(p_values):
    p = np.asarray(p_values, dtype=float)
    order = np.argsort(p)
    adjusted = np.empty_like(p)
    running = 0.0
    m = len(p)
    for rank, idx in enumerate(order):
        running = max(running, min(1.0, (m - rank) * p[idx]))
        adjusted[idx] = running
    return adjusted.tolist()


def run(path: Path, block: int = 60, reps: int = 10_000, seed: int = 20261125):
    with path.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    required = {"symbol", "fold", "timestamp", "msar_log_score", *COMPARATORS}
    if not rows or not required.issubset(rows[0]):
        raise ValueError("CSV missing required columns")
    symbols = sorted({r["symbol"] for r in rows})
    results = []
    rng = np.random.default_rng(seed)
    for symbol in symbols:
        subset = [r for r in rows if r["symbol"] == symbol]
        folds = sorted({r["fold"] for r in subset})
        if len(folds) != 1:
            raise ValueError(f"{symbol}: expected exactly one fold in this pilot")
        for col in COMPARATORS:
            x = np.array([float(r["msar_log_score"]) - float(r[col]) for r in subset])
            mean, lo, hi, p = mbb_mean(x, block, reps, rng)
            results.append({
                "symbol": symbol,
                "comparison": "MSAR-" + col.removesuffix("_log_score").upper(),
                "n": len(x),
                "folds": len(folds),
                "mean_delta": mean,
                "ci95_low": lo,
                "ci95_high": hi,
                "p_raw": p,
            })
    adjusted = holm_adjust([r["p_raw"] for r in results])
    for row, p_adj in zip(results, adjusted):
        row["p_holm"] = float(p_adj)
        row["reject_0.05_after_holm"] = bool(p_adj < 0.05)
    return {
        "source_file": path.name,
        "scope_warning": "Older pilot only: BTCUSDT and ETHUSDT, one fold and 1,000 rows per asset; not the 9-asset/6-fold multi-period study.",
        "assets": symbols,
        "block_length_rows": block,
        "bootstrap_replicates": reps,
        "seed": seed,
        "family_size": len(results),
        "method": "non-circular moving-block bootstrap; percentile 95% CI; centered bootstrap two-sided p; Holm step-down adjustment across the six pilot comparisons",
        "results": results,
        "decision": "NO_COMPARISON_REJECTED_AFTER_HOLM" if not any(r["reject_0.05_after_holm"] for r in results) else "SOME_COMPARISONS_REJECTED",
        "limitations": [
            "This is not a recomputation of the 9-asset multi-period artifact.",
            "Only one fold per asset; inference is exploratory and weak for generalization.",
            "Forecast log-score differences are not trading PnL or evidence of tradable alpha.",
        ],
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv", type=Path)
    ap.add_argument("--output", type=Path)
    ap.add_argument("--block", type=int, default=60)
    ap.add_argument("--reps", type=int, default=10_000)
    ap.add_argument("--seed", type=int, default=20261125)
    args = ap.parse_args()
    result = run(args.csv, args.block, args.reps, args.seed)
    rendered = json.dumps(result, indent=2, sort_keys=True)
    if args.output:
        args.output.write_text(rendered + "\\n", encoding="utf-8")
    print(rendered)


if __name__ == "__main__":
    main()
