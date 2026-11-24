# PARSA — Stage 5: Read-only Live Market Discovery

**Stage:** 5 — public market data, continuous scanner, fresh-data Long/Short review  
**Execution authority:** NONE  
**Live trading:** PROHIBITED  
**Paper trading:** NOT ENABLED

## Scope implemented

- Binance Spot public GET `/api/v3/klines` adapter. No credentials or private/account/order endpoints.
- Decimal-string candle parsing, strict chronological validation, and malformed response rejection.
- Configurable read-only polling scanner for an explicit USDT symbol allowlist and supported candle interval.
- Closed-candle, gap, ordering, and freshness checks. Network/data errors become status events and never synthetic candles/opportunities.
- Deterministic momentum/volume candidate screening; Long and Short are separate directions.
- Independent fresh-data review gateway. A pass means only "eligible for further analysis"; it is not an entry, signal authorization, paper trade, or order.
- Stable candidate IDs derived from symbol, interval, candle open time, and direction so queue replay can deduplicate.

## Deliberate limitations

- Initial screen is a transparent deterministic momentum/volume heuristic, not a trained or statistically validated strategy.
- No funding, open interest, order book, spread, slippage, fees, PnL, risk sizing, stop loss, or take profit analysis.
- Scanner requires an owner to start and stop it; it is not auto-started or wired into the app lifecycle.
- The app does not yet seed/replay existing open orders at startup, persist the opportunity queue, or connect a production order-state event source.
- Public Binance endpoint reachability and live behavior must be verified from the target device/network; CI tests use deterministic fakes.

## Safety

- GET-only public market data.
- No API keys, private endpoints, wallet access, account access, order placement, scheduler service, webhook, or trading execution.
- Live trading remains PROHIBITED; paper trading remains NOT ENABLED.
