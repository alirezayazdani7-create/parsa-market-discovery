#!/usr/bin/env python3
"""Reproducible structural audit of PARSA multi_period_walk_forward.json.

This script checks artifact structure and reported summaries only. It does not
recompute forecasts, bootstrap replicates, p-values, Holm adjustment, or PnL.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path

COMPARISONS = (
    "msar_minus_hmm",
    "msar_minus_lagged_volatility",
    "msar_minus_train_gaussian",
)


def audit(path: Path) -> dict:
    doc = json.loads(path.read_text(encoding="utf-8"))
    assets = doc["assets"]
    results = []
    collapsed_folds = 0
    fold_count = 0
    for symbol, asset in sorted(assets.items()):
        if asset.get("fold_count") != 6 or len(asset.get("folds", [])) != 6:
            raise ValueError(f"{symbol}: expected six folds")
        fold_count += len(asset["folds"])
        for fold in asset["folds"]:
            hard = fold["hmm_diagnostics"]["state_occupancy"]["hard_assignment_fraction"]
            if len(hard) != 2 or abs(max(hard) - 1.0) < 1e-12:
                collapsed_folds += 1
        pooled = asset["pooled_mean_log_score"]
        if not all(pooled["msar"] > pooled[k] for k in ("hmm", "lagged_volatility", "train_gaussian")):
            raise ValueError(f"{symbol}: MS-AR is not the top pooled score")
        for key in COMPARISONS:
            item = asset["paired_block_bootstrap"][key]
            if item["bootstrap_replicates"] != 500 or item["block_length_minutes"] != 60:
                raise ValueError(f"{symbol}/{key}: unexpected bootstrap settings")
            results.append({
                "symbol": symbol,
                "comparison": key,
                "mean_delta": item["mean_delta"],
                "ci_95_low": item["ci_95_low"],
                "ci_95_high": item["ci_95_high"],
                "ci_excludes_zero": item["ci_95_low"] > 0,
            })
    positives = sum(x["ci_excludes_zero"] for x in results)
    return {
        "source_status": doc.get("status"),
        "source_data": doc.get("data_source"),
        "asset_count": len(assets),
        "fold_count": fold_count,
        "oos_observations": sum(a["total_test_observations"] for a in assets.values()),
        "msar_top_pooled_score_assets": sum(
            all(a["pooled_mean_log_score"]["msar"] > a["pooled_mean_log_score"][k]
                for k in ("hmm", "lagged_volatility", "train_gaussian"))
            for a in assets.values()
        ),
        "bootstrap_comparisons": len(results),
        "reported_95pct_intervals_above_zero": positives,
        "hmm_single_state_hard_assignment_folds": collapsed_folds,
        "hmm_total_folds": fold_count,
        "holm_status": "NOT_COMPUTABLE_FROM_THIS_ARTIFACT",
        "holm_blocker": "Raw paired per-observation scores or bootstrap replicate distributions and unadjusted p-values are not stored.",
        "trading_pnl_status": "NOT_TESTED",
        "comparisons": results,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("artifact", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = audit(args.artifact)
    rendered = json.dumps(result, indent=2, sort_keys=True)
    if args.output:
        args.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)


if __name__ == "__main__":
    main()
