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
- Added an Android lifecycle observer that starts/stops this runtime with its owner. Runtime is dependency-injected and is not silently auto-created or activated by the app.
- Added close/reopen, waiting-candidate recovery, duplicate/conflict, final-order-release, expiry and one-time-claim tests.

## Required source integration still outstanding

The repository does not contain a production order-state event source or an authoritative source of open orders. Stage 6 therefore defines `ConfirmedOrderLifecycleEventSource` as the integration contract and requires its implementation to supply:
1. A complete, authoritative open-order snapshot before scanner start.
2. Confirmed open and confirmed close notices only.
3. Stable order IDs and normalized symbols.

Until a real source is connected, callers must not start the runtime with an empty or guessed snapshot. This stage does not claim production order-event integration is complete.

## Safety

- The only exchange adapter remains public Binance market-data GET.
- No credentials, private/account endpoints, order placement, cancel requests, wallet or balance access are added.
- No paper or live trading activation.
- A Hunter acceptance means eligible for further analysis only.
- Runtime activation requires explicit construction/injection by the app owner; this change does not auto-start market polling.

## Acceptance validation

- Full `:app:testDebugUnitTest` suite.
- `:app:assembleDebug`.
- Dedicated durable queue tests verify database close/reopen behavior and recovery semantics.
- CI evidence applies only to the exact validated commit and does not establish strategy profitability or target-device network reliability.
