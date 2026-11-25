# Offline Paper Engine Accounting Gate

## Scope
This branch adds a deterministic, in-memory accounting harness for isolated Paper tests. It does not connect to an exchange, wallet, broker, order endpoint, API credential, or network service. Live trading and execution remain disabled / not authorized.

## Covered by unit tests
- Long and short direction-aware gross PnL
- Entry and exit fees using decimal rates (0.009% = 0.00009)
- Adverse spread and slippage applied to simulated fills
- Funding debit and funding credit
- Margin and return-on-margin calculation
- Signal freshness and minimum score rejection
- Invalid leverage / price / quantity rejection
- One open Paper position per symbol
- Duplicate signal and duplicate close idempotency
- Conflicting replay rejection
- Unknown position and invalid close-time rejection

## Limitations
This is a component accounting gate, not yet a full production-integrated signal-to-position-to-exit test. It does not establish predictive accuracy, profitable expectancy, out-of-sample net PnL, or readiness for live execution. The CI run must pass before this component is accepted. The next integration gate must connect actual signal output, persistent position/event ledger, exit rules, and a complete reconciled report using deterministic fixtures.

## Safety
No live execution path is implemented in this class. Keep live trading OFF and authorization NOT_AUTHORIZED.
