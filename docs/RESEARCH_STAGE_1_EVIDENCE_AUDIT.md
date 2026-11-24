# PARSA — Stage 1 Research Evidence Audit

**Audit date:** 2026-11-24  
**Source artifact:** `multi_period_walk_forward.json` (Library artifact inspected in full)  
**Source status:** `COMPLETED_MULTI_PERIOD_FORECAST_VALIDATION`  
**Data source:** Binance Spot monthly Vision normalized CSV  
**Method:** Six rolling walk-forward folds per asset; 10,000 training observations and 2,000 disjoint test observations per fold; sequential one-step forecasts.  
**Safety:** `live_trading=false`, `paper_only=true`, `strategy_backtest=false`.

## 1. Scope and sample

- Assets: BTCUSDT, ETHUSDT, SOLUSDT, XRPUSDT, ADAUSDT, DOGEUSDT, SHIBUSDT, PEPEUSDT, WIFUSDT.
- 9 assets × 6 folds × 2,000 OOS observations = **108,000 OOS forecast observations**.
- Each asset has 12,000 OOS observations and 963,360 source rows.
- Models compared: MS-AR, HMM, lagged-volatility baseline, and train-only Gaussian baseline.

## 2. Result

MS-AR has the highest pooled mean log score on **all 9 of 9 assets**.

| Asset | MS-AR | HMM | Lagged volatility | Train Gaussian |
|---|---:|---:|---:|---:|
| ADA | 5.5489 | 3.6802 | 5.4486 | 5.4170 |
| BTC | 6.2422 | 3.6843 | 6.0971 | 6.0401 |
| DOGE | 5.6787 | 3.6808 | 5.5514 | 5.5046 |
| ETH | 5.8692 | 3.6826 | 5.7219 | 5.6635 |
| PEPE | 4.9266 | 3.6668 | 4.8523 | 4.8502 |
| SHIB | 5.3250 | 3.6781 | 5.2475 | 5.2424 |
| SOL | 5.6586 | 3.6816 | 5.5485 | 5.5035 |
| WIF | 5.4958 | 3.6702 | 5.0391 | 5.0133 |
| XRP | 5.7864 | 3.6817 | 5.6526 | 5.5981 |

The artifact reports paired moving-block bootstrap comparisons with 500 replicates and 60-minute blocks. For each asset, the reported 95% confidence intervals for MS-AR minus each comparator are above zero. These are the artifact's reported intervals; the artifact does **not** report a Holm multiple-comparison adjustment.

## 3. Material diagnostics

- HMM hard assignments are degenerate in all 6 folds for each of the 9 assets (54/54 folds have one state assigned to 100% of observations). HMM should remain a comparator, not a production regime detector, until repaired and independently revalidated.
- The MS-AR optimizer reports successful convergence across its restarts in the artifact. This supports numerical fit completion, not forecast calibration or trading profitability.
- The artifact itself explicitly states that forecast log-score differences are not tradable alpha and that execution costs and net PnL remain untested.

## 4. Stage 1 gate decision

**Decision: PARTIAL PASS — MS-AR is the leading forecast-model candidate; not approved for trading.**

Passed:
- Multi-asset, multi-fold forecast comparison exists.
- MS-AR leads pooled log score on all 9 assets.
- The artifact's per-asset bootstrap intervals are positive against the three comparators.

Open blockers:
1. Recompute paired inference with a documented multiple-comparison correction (at minimum Holm across the 27 asset × comparator tests), or define and preregister a smaller primary hypothesis family.
2. Investigate HMM state collapse; do not use its regime labels in downstream decisions.
3. Validate probability calibration, forecast horizon alignment, and feature/label timing from the underlying implementation.
4. Run a separate cost-aware strategy evaluation. Forecast score is not net trading PnL.

## 5. Next implementation boundary

The next stage should implement an isolated MS-AR inference component that consumes only completed, timestamped market bars and emits:
- predictive distribution / return forecast,
- regime-state probabilities (with explicit uncertainty),
- horizon and data-cutoff metadata,
- model version and input hash,
- abstain / invalid-data reason.

It must not create orders or enable paper/live execution. The Android app's existing momentum/volume scanner is a separate heuristic and must not be described as the MS-AR engine.

## 6. Reproducibility

The source artifact's configuration records:
- folds: 6
- train size: 10,000
- test size: 2,000
- symbols: 9 listed above
- seed base: 20261115
- schema version: 1

The source artifact's final caveat is preserved: positive forecast log-score difference is not evidence of tradable alpha; execution costs and net PnL remain untested.
