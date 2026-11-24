# PARSA — Order Lifecycle, Stage 1

**Stage:** 1 — deterministic order state machine  
**Execution authority:** NONE  
**Live trading:** PROHIBITED  
**Paper trading:** NOT ENABLED BY THIS STAGE  
**External exchange calls:** NONE

## Purpose

Create one authoritative, deterministic definition of an order's lifecycle before connecting signal generation, risk, persistence, or any paper execution. No component may infer order status from a timeout, a UI label, or its own local assumption.

## States

- `NEW`: local intent exists; no submit attempt has been recorded.
- `SUBMITTED`: submit attempt recorded; exchange response may still be pending.
- `UNKNOWN`: transport outcome is ambiguous. Never submit a replacement merely because a response timed out.
- `ACKNOWLEDGED`: exchange acknowledged the order; this is not a fill.
- `PARTIALLY_FILLED`: one or more unique fills were received, but the requested quantity is not complete.
- `PENDING_CANCEL`: cancellation requested; the order is still potentially executable.
- `FILLED`: the full requested quantity is accounted for.
- `CANCELED`: exchange confirmed cancellation; any already-reported partial quantity remains recorded.
- `REJECTED`: exchange explicitly rejected the order.
- `EXPIRED`: exchange confirmed expiry.

## Allowed event semantics

1. Every event must carry a non-empty unique `eventId` and matching `orderId`.
2. Replayed event IDs are idempotent: they do not change state or quantities.
3. Fill events also carry a unique `fillId`. A repeated fill ID cannot increase cumulative filled quantity, even if delivered under another event ID.
4. Fill quantity is incremental, positive, and cannot make cumulative quantity exceed original quantity.
5. A cancel request is not a cancellation. Only `CANCEL_CONFIRMED` moves the order to `CANCELED`.
6. A timeout/ambiguous transport result moves `SUBMITTED` to `UNKNOWN`. It does not authorize a second submission.
7. A late fill received while cancellation is pending is recorded. If it completes the order, the resulting state is `FILLED`; otherwise it remains `PENDING_CANCEL` until cancellation is confirmed.
8. Reconciliation is the only permitted way to resolve `UNKNOWN` from an exchange-observed status. Reconciliation quantities must be monotonic and internally consistent.
9. Terminal exchange outcomes do not erase previously recorded fills.
10. Invalid transitions are rejected without mutating the prior state.

## Stage boundary

This stage implements a pure Kotlin state machine and deterministic unit tests only. It does **not** persist an order ledger, contact an exchange, create orders, manage positions, calculate fees/PnL, or enable paper/live trading. Those are separate gates and must not be represented as completed here.

## Acceptance tests

- Normal submit → acknowledge → partial fill → fill.
- Duplicate event replay.
- Duplicate fill under a different event ID.
- Cancel requested versus cancel confirmed.
- Fill arriving while cancellation is pending.
- Ambiguous submit outcome does not permit blind resubmission.
- Reconciliation of unknown status.
- Reject overfill, zero/negative/non-finite fill, invalid original quantity, wrong order ID, empty event ID, and illegal transitions.
- Reject decreasing/excessive reconciliation totals and inconsistent REJECTED-with-fills snapshots.
- A full fill arriving while cancellation is pending ends in FILLED.
- Preserve partial fills when cancellation is confirmed.

## Audit record required for this stage

Record branch, base commit, file paths, commit SHAs, test source, test execution result, and any unverified items. A test source file existing is not evidence that the test suite passed. The stage remains **BLOCKED_PENDING_EXECUTED_TESTS** until a real build/test run is executed in the project environment and independently inspected.

## Continuous integration

The repository now has a dedicated GitHub Actions workflow at
`.github/workflows/order-lifecycle-stage1.yml`. It runs the complete debug unit-test suite on JDK 21 (required by the configured
Robolectric API 36 tests), uploads the test report even on failure, and then
builds the debug Android application on pull requests to `main`. The workflow is a validation mechanism, not evidence of a passing
run. The stage remains blocked until a completed run is inspected and both
steps succeed.

CI path filter was updated to include this stage document; this commit requests the first automated validation run.
