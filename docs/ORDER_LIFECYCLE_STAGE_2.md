# PARSA — Order Lifecycle, Stage 2: Durable Event Ledger

**Stage:** 2 — append-only local persistence for order events  
**Execution authority:** NONE  
**Live trading:** PROHIBITED  
**Paper trading:** NOT ENABLED BY THIS STAGE  
**External exchange calls:** NONE

## Objective

Persist order-event records in a local Room/SQLite database so event history survives database closure and can be inspected by a later, separately gated component.

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
- No claim of encrypted-at-rest storage, cryptographic tamper evidence, or cross-device replication.

## Acceptance evidence

1. Full `:app:testDebugUnitTest` suite: **PASS**.
2. Dedicated ledger test class is included in the successful full test task; its cases cover durability after close/reopen, idempotent replay, conflicting replay, exact decimal preservation, per-order event identity, and invalid identifiers.
3. `:app:assembleDebug`: **PASS**.
4. Completed CI run inspected on exact commit `8f754f7791c4fa125a5600022fd13b28c6eba79e`: [Run #45432889770](https://github.com/alirezayazdani7-create/parsa-market-discovery/actions/runs/45432889770).
5. Scope audit confirms this stage does not introduce exchange execution, credentials, trading activation, or automatic state-machine replay.

**Validation status: PASS — implementation and CI acceptance checks verified.**

## Important limitations

- This is a standalone local ledger; it is not yet wired into the app's production order flow or automatic state recovery.
- The database is not encrypted by this stage.
- The payload fingerprint is a deterministic identity string, not a cryptographic hash or tamper-proof integrity mechanism.
- Append-only behavior is enforced by the exposed DAO API; it is not a guarantee against direct database-file modification.
- No cross-device synchronization or backup guarantee is provided.

Stage 2 validation does not imply paper/live trading readiness or grant execution authority. Those capabilities remain prohibited and require separate explicit gates.
