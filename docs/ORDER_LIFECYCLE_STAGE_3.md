# PARSA — Stage 3: Continuous Re-Scan Opportunity Handoff

**Stage:** 3 — deterministic opportunity queue and post-close handoff  
**Execution authority:** NONE  
**Live trading:** PROHIBITED  
**Paper trading:** NOT ENABLED  
**Market scanning / exchange calls:** NOT IMPLEMENTED IN THIS STAGE

## Goal

Do not discard a fresh opportunity discovered while an order for the same symbol is still open. Keep the candidate pending, and make it available to the appropriate Long or Short Hunter immediately after the final open order for that symbol closes, provided the candidate has not expired.

## Implemented domain contract

- A scanner may publish immutable `TradeOpportunity` candidates with unique ID, symbol, direction, score (1–10), detection time, and expiry time.
- `OpportunityQueue.onOrderOpened` / `onOrderClosed` receive order lifecycle notifications from a future integration adapter.
- Candidates for a symbol with one or more open orders remain `WAITING_FOR_ORDER_CLOSE`.
- Closing one order does not release candidates while another order for that symbol remains open.
- Closing the final order releases unexpired candidates as `READY_FOR_HUNTER`.
- Candidates for other symbols can be claimed independently.
- Long and Short candidates are claimed separately.
- Highest score wins; ties prefer the newest detection, then stable opportunity ID ordering.
- Expired candidates are never handed to Hunter.
- Same opportunity ID and same payload is idempotent; same ID with a different payload is rejected.
- A candidate can be claimed only once.

## Required integration contract for a later stage

1. A market scanner continues evaluating symbols regardless of whether an order is open.
2. Scanner emits a candidate only after its own signal/potential criteria are met.
3. An order lifecycle adapter reports every open and confirmed-close transition.
4. The queue holds same-symbol candidates until no open order remains for that symbol.
5. On release, the adapter notifies the matching Hunter immediately.
6. Hunter and Analyst must re-evaluate current market data; the stored candidate is a prompt for review, not an executable signal.
7. The order/risk gate must independently enforce one-position-per-symbol and all configured exposure limits.

## Deliberate limitations

- The queue is deterministic and in-memory only; it is not durable across process restart.
- No live scanner, market data subscription, timer, background worker, exchange API, order placement, or app UI is connected.
- The class does not itself monitor price action; it receives candidates from a future scanner.
- No candidate is automatically converted into an order or trade.
- The current PR remains a code/test stage, not production trading authorization.

## Acceptance tests

Covers open-order holding, multiple open orders for the same symbol, unrelated-symbol independence, release on final close, expiry while waiting, expiry before claim, independent Long/Short queues, score/time priority, idempotent replay, conflicting ID reuse, single claim, invalid candidates, and no double release.
