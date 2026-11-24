# PARSA — Independent 9-Asset Walk-Forward Re-Run

**Date:** 2026-11-25  
**Status:** `INDEPENDENT_REPRODUCTION_NOT_BITWISE_REPLAY`  
**Purpose:** Reconstruct paired out-of-sample forecast scores from the recovered Binance Spot 1-minute source data and perform Holm correction across 27 asset/comparator tests.

## 1. Data recovered and validated

- 9 assets: BTCUSDT, ETHUSDT, SOLUSDT, XRPUSDT, ADAUSDT, DOGEUSDT, SHIBUSDT, PEPEUSDT, WIFUSDT.
- Period: 2025-01 through 2026-10.
- 198 monthly Binance Spot Vision archives; all monthly entries in the three part-manifests are marked checksum-verified.
- 963,360 candles per asset; 8,670,240 source rows total.
- All nine reconstructed series passed the script's 60,000 ms timestamp continuity check.
- Six rolling folds per asset, matching the index boundaries recorded in `multi_period_walk_forward.json`: 10,000 training returns and 2,000 OOS returns per fold.
- Total OOS sample: 108,000 observations.

## 2. Explicit independent model specification

The original multi-period experiment's source code was not present in the repository or recovered archives. This re-run therefore uses an explicit independent implementation rather than claiming an exact replay:

- **MS-AR:** two-state Gaussian Markov-switching AR(2); shared AR(2) coefficients, state-specific intercepts and innovation standard deviations, two-state transition matrix; maximum likelihood using L-BFGS-B with deterministic initializations and bounded parameters.
- **HMM:** two-state Gaussian hidden Markov model with state-specific means and standard deviations; maximum likelihood using L-BFGS-B.
- **Train Gaussian:** Gaussian return model fitted on each training window, with training mean and standard deviation.
- **Lagged-volatility baseline:** zero-mean Gaussian with volatility estimated from the immediately preceding 60 returns.
- Forecast scores are sequential one-step predictive log densities. The filter is carried through the training sequence and updated with each observed test return; no future test observation is used before its own forecast.

The source artifact's per-asset `input_sha256` values do not match the reconstructed normalized CSV byte streams. Combined with the missing original model code, this means the new output is not bitwise reproducible against the original artifact. Differences in model specification, initialization, or input serialization may explain different rankings.

## 3. Paired inference

- 27 tests: 9 assets × 3 comparators (HMM, lagged volatility, train Gaussian).
- 10,000 non-circular moving-block bootstrap replicates per test.
- Block length: 60 observations; resampling is performed within each of the six folds, never across fold boundaries.
- Two-sided centered-bootstrap p-values; percentile 95% confidence intervals.
- Holm step-down correction across all 27 raw p-values.
- Minimum reportable Monte Carlo p-value is 0.0001 (1 / 10,001).

## 4. Results

**16 of 27 comparisons reject at 5% after Holm correction.** This is a forecast-distribution result, not a trading result.

| Comparator | Significant after Holm | Interpretation |
|---|---:|---|
| Train Gaussian | 9/9 | MS-AR log score is higher on all nine assets |
| HMM | 5/9 | MS-AR is significantly higher on four assets, but significantly lower on ETH; other four are inconclusive |
| Lagged volatility | 2/9 | MS-AR is significantly higher on SHIB and PEPE; other seven are inconclusive |

The five significant MS-AR-vs-HMM comparisons include one adverse result: ETHUSDT has a negative MS-AR-minus-HMM mean difference (approximately −0.00870 log-score units), with a Holm-adjusted p-value of approximately 0.00270. Thus, the independent re-run does **not** reproduce the original artifact's claim that MS-AR ranks first on all nine assets.

Full per-asset mean differences, confidence intervals, raw p-values, Holm-adjusted p-values, and decisions are in `independent_rerun_results.json`. The 432,000 stored values (four models × 108,000 OOS observations) are in `oos_paired_scores.npz`.

## 5. Decision and limitations

**Research decision: HOLD — not approved as a trading model.**

1. The independent run supports MS-AR over a training Gaussian baseline on all nine assets, but it does not establish consistent superiority over HMM or lagged volatility.
2. The difference from the original report is material. The original source code and exact input serialization must be recovered before the original 9/9 ranking can be treated as independently reproduced.
3. Log-score superiority does not establish directional accuracy, calibrated trade probabilities, tradable alpha, or positive net expectancy.
4. This experiment does not model exchange fees, bid/ask spread, slippage, funding, latency, market impact, position sizing, stop/target execution, or portfolio constraints.
5. No paper trading, live trading, order routing, API credentials, or execution authority is enabled.

## 6. Reproduction

The runner expects the three **inner** dataset ZIPs containing the monthly normalized CSVs. The Library's three `BINANCE_SPOT_1M_PART_*.zip` files each contain one inner ZIP; extract that nested ZIP first.

```bash
python research/rerun_multiperiod_independent.py \
  --part BINANCE_SPOT_1M_PART_1_BTC_ETH_SOL_inner.zip \
  --part BINANCE_SPOT_1M_PART_2_XRP_ADA_DOGE_inner.zip \
  --part BINANCE_SPOT_1M_PART_3_SHIB_PEPE_WIF_inner.zip \
  --output-dir parsa-rerun-output
```

The runner validates each part-manifest, row counts, and 60-second continuity; it writes the JSON result, all paired OOS scores, and resumable per-asset checkpoints.
