# PARSA — Order Lifecycle, Stage 2: Durable Event Ledger

**Stage:** 2 — append-only local persistence for order events  
**Execution authority:** NONE  
**Live trading:** PROHIBITED  
**Paper trading:** NOT ENABLED BY THIS STAGE  
**External exchange calls:** NONE

## Objective

Persist immutable order-event records in a local Room/SQLite database so accepted event history survives process/database closure and can be audited or replayed by a later, separately gated component.

## Included

- Room entity with exact decimal quantities stored as canonical decimal strings.
- Unique composite key on (orderId, eventId).
- Transactional append operation.
- Same event ID and same payload fingerprint returns DUPLICATE without another row.
- Same event ID and different payload fingerprint returns CONFLICT without overwriting the original.
- Ordered per-order event retrieval by database sequence.
- Persistence verification across database close and reopen.
- No update/delete API is exposed by the ledger DAO.

## Excluded

- No exchange/network calls, order submission, cancellation, or execution.
- No paper or live trading activation.
- No API keys, credentials, scheduler, webhook, or background worker.
- No position, balance, fee, PnL, or risk calculations.
- No automatic mutation of OrderState; caller integration and replay/recovery are separate gates.
- No claim of encrypted-at-rest storage or cross-device replication.

## Acceptance gate

1. Full `:app:testDebugUnitTest` suite passes.
2. Dedicated ledger tests pass, including durability after close/reopen, idempotent replay, conflicting replay, exact decimal preservation, per-order event identity, and invalid identifiers.
3. `:app:assembleDebug` succeeds.
4. CI run is completed and inspected on the exact commit SHA.
5. No unreviewed changes are introduced outside this stage's scope.

The stage remains **BLOCKED_PENDING_EXECUTED_TESTS** until all acceptance evidence is independently verified.
