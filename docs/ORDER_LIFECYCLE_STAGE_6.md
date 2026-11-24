# PARSA — Order Lifecycle Stage 6: Durable Integration and Recovery

**Stage:** 6 — durable opportunity queue, startup order-occupancy recovery, and lifecycle-owned read-only handoff  
**Execution authority:** NONE  
**Live trading:** PROHIBITED  
**Paper trading:** NOT ENABLED  
**Exchange order endpoints:** NOT IMPLEMENTED

## Implemented

- Added an isolated Room database, `parsa_durable_opportunity_queue.db`, for open-order occupancy and opportunity state. It does not alter the existing core database schema or invoke destructive migration.
- Open order identities and normalized symbols are persisted independently from the in-memory Stage 3 queue.
- Opportunity identity, direction, score, timestamps, expiry and state are persisted.
- Same-symbol candidates remain waiting until the final persisted open order is closed.
- Candidate release, expiry, score/time priority, idempotent replay and one-time claim are implemented transactionally in Room.
- Startup recovery can restore the authoritative open-order snapshot before the scanner starts.
- Added `PersistentOpportunityHandoff`: restores open-order occupancy, subscribes to an injected confirmed order-event source, then starts the read-only scanner; callbacks feed the durable queue and the matching fresh-data Hunter.
- Added `ParsaOpportunityRuntimeOwner` and attached it to `MainActivity`; runtime starts while the Activity is started, stops when it leaves the foreground, and closes its scheduler/databases when the Activity is destroyed.
- Added close/reopen, waiting-candidate recovery, duplicate/conflict, final-order-release, expiry and one-time-claim tests.

## Remaining order-source boundary

The app now has a durable local confirmed-order event source and a state-machine publisher adapter. The publisher must be called by the owner that processes each accepted OrderLifecycle result. This repository still has no exchange order/account adapter and no order submission feature, by design; therefore there are no exchange-originated open-order events to import. The local runtime starts from its durable local occupancy snapshot, and the event publisher is the supported integration point for any future authorized order-state owner.

## Safety

- The only exchange adapter remains public Binance market-data GET.
- No credentials, private/account endpoints, order placement, cancel requests, wallet or balance access are added.
- No paper or live trading activation.
- A Hunter acceptance means eligible for further analysis only.
- The Activity explicitly constructs the read-only runtime. It performs public market-data GET requests only while the Activity is started.

## Acceptance validation

- Full `:app:testDebugUnitTest` suite.
- `:app:assembleDebug`.
- Dedicated durable queue tests verify database close/reopen behavior and recovery semantics.
- CI evidence applies only to the exact validated commit and does not establish strategy profitability or target-device network reliability.


## Runtime defaults

- Runtime attaches to the Activity lifecycle and starts on Activity start; it stops on Activity stop.
- Read-only scan allowlist: BTC/USDT, ETH/USDT, SOL/USDT.
- Scan timeframe: 5m; polling interval: 30 seconds; candle history: 30 per symbol.
- Hunter review fetches fresh public data and only marks a candidate eligible for further analysis.
- Runtime does not place orders, enable paper trading, or access private exchange endpoints.
