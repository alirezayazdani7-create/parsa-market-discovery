# PARSA — Older Blind Next-Candle Pilot: Paired Bootstrap Audit

**Audit date:** 2026-11-25  
**Source:** `BLIND_NEXT_CANDLE_FULL_ENGINE_TEST.csv` from the Library  
**This is a separate older pilot artifact, not the 9-asset / 6-fold `multi_period_walk_forward.json` study.**

## Scope

- Assets: BTCUSDT and ETHUSDT only.
- Observations: 1,000 per asset.
- Folds: one per asset.
- Comparisons: MSAR log score minus HMM, zero-return, and mean-return baseline log scores.
- Inference: non-circular moving-block bootstrap, 60-row blocks, 10,000 replicates, deterministic seed 20261125; percentile 95% intervals; centered-bootstrap two-sided p-values; Holm correction across six comparisons.

## Results

| Asset | Comparison | Mean difference | 95% CI | Raw p | Holm p |
|---|---|---:|---:|---:|---:|
| BTC | MSAR − HMM | 0.00418 | −0.00067 to 0.01125 | 0.1632 | 0.6671 |
| BTC | MSAR − Zero | 0.28264 | 0.01984 to 0.78981 | 0.1112 | 0.6671 |
| BTC | MSAR − Mean | 0.28261 | 0.01900 to 0.78656 | 0.1113 | 0.6671 |
| ETH | MSAR − HMM | 0.00249 | −0.00077 to 0.00569 | 0.1161 | 0.6671 |
| ETH | MSAR − Zero | 0.54886 | 0.02563 to 1.63003 | 0.1273 | 0.6671 |
| ETH | MSAR − Mean | 0.54860 | 0.02289 to 1.60591 | 0.1241 | 0.6671 |

## Decision

**No comparison rejects the null at 5% after Holm correction.** This small older pilot does not establish statistically significant MSAR superiority after correction. In particular, MSAR's advantage over HMM is small and its confidence interval includes zero for both assets.

This does not contradict or replace the later nine-asset study. It shows why the raw per-observation paired scores are required before making adjusted inference claims. The current nine-asset summary artifact does not contain those raw paired scores or bootstrap replicate distributions, so Holm-adjusted p-values for its 27 comparisons remain unavailable.

## Limitations

- Only two assets and one fold per asset; limited temporal and cross-asset generalization.
- The 60-row block length is a declared sensitivity choice for this pilot, not a proven optimal dependence horizon.
- This evaluates forecast log scores only. It is not a trading strategy backtest, does not include fees/spread/slippage/funding/latency, and says nothing about net PnL.
- No paper or live execution is enabled.

## Reproduction

Run:

```bash
python research/audit_blind_pilot.py BLIND_NEXT_CANDLE_FULL_ENGINE_TEST.csv --block 60 --reps 10000 --seed 20261125 --output blind_pilot_audit.json
```
