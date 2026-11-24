# PARSA — Stage 4: Continuous Scanner-to-Hunter Adapter

**Stage:** 4 — orchestration adapter and deterministic integration tests  
**Execution authority:** NONE  
**Live trading:** PROHIBITED  
**Paper trading:** NOT ENABLED  
**Real market scanner / real Hunter implementations:** NOT PRESENT

## Implemented

- `ContinuousOpportunityScanner` defines a start/stop callback contract. It can emit candidates while same-symbol orders are open.
- `ContinuousOpportunityHandoff` connects scanner callbacks to the Stage 3 `OpportunityQueue`.
- Order-open notifications mark the symbol as occupied; order-close notifications release eligible candidates only after the final same-symbol order closes.
- Candidates discovered when the symbol is not occupied are handed to the direction-matched review gateway immediately.
- Claimed candidates are sent to the matching Long or Short review method once.
- Hunter exceptions are surfaced as failed review results; they do not trigger an automatic retry or trade.
- Hunter review is a boundary contract: a real implementation must obtain fresh market data and independently revalidate the candidate.

## Explicit gap

Repository inspection found no concrete continuously running market scanner, no real Long Hunter or Short Hunter implementation, and no production order lifecycle event source to connect. The existing historical-data provider is a local in-memory archive provider, not a live market feed. Therefore this stage implements and tests the adapter boundary, not actual market scanning or real Hunter analysis.

The next integration requires selecting/implementing a real read-only market data source, a scanner implementation, fresh-data Long/Short review implementations, and a confirmed order-state event source. These must remain read-only and must not place orders.

## Safety

- No exchange API client or credentials added.
- No network request, timer, scheduler, background worker, or webhook added.
- No order submission, wallet, balance, position accounting, fees, or PnL functionality.
- No automatic conversion from a Hunter review to a trade.
- The scanner adapter must be stopped explicitly by its owner.
- Queue remains in-memory and is not restart-durable.

## Validation

Unit tests cover continuous scanner callbacks while an order is open, delayed handoff until final close, immediate independent-symbol handoff, expiry, idempotent lifecycle start/stop, and Hunter failure handling.
