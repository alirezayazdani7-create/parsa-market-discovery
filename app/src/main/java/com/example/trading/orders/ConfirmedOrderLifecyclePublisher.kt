package com.example.trading.orders

/**
 * Bridges accepted pure state-machine transitions to the durable local order event source.
 * This adapter never creates an order or contacts an exchange. The caller must pass the actual
 * result returned by OrderLifecycle.apply after receiving an authoritative order event.
 */
class ConfirmedOrderLifecyclePublisher(
    private val eventSource: RoomConfirmedOrderLifecycleEventSource
) {
    suspend fun publishTransition(
        previous: OrderState,
        result: OrderTransitionResult,
        observedAtMillis: Long
    ) {
        if (!result.accepted || result.duplicate || previous.status == result.state.status) return
        val wasOpen = previous.status.isOccupyingSymbol()
        val isOpen = result.state.status.isOccupyingSymbol()
        when {
            !wasOpen && isOpen -> eventSource.publishConfirmedOpen(
                result.state.orderId, result.stateSymbolFallback(previous), observedAtMillis
            )
            wasOpen && !isOpen -> eventSource.publishConfirmedClose(
                result.state.orderId, result.stateSymbolFallback(previous), observedAtMillis
            )
        }
    }

    private fun OrderStatus.isOccupyingSymbol(): Boolean = this in setOf(
        OrderStatus.SUBMITTED,
        OrderStatus.UNKNOWN,
        OrderStatus.ACKNOWLEDGED,
        OrderStatus.PARTIALLY_FILLED,
        OrderStatus.PENDING_CANCEL
    )

    /**
     * OrderState deliberately has no market symbol. The caller should use the overload below
     * whenever it has the canonical symbol from its order intent.
     */
    private fun OrderState.stateSymbolFallback(previous: OrderState): String =
        throw IllegalStateException("Market symbol is required; call publishTransition with symbol")

    suspend fun publishTransition(
        previous: OrderState,
        result: OrderTransitionResult,
        symbol: String,
        observedAtMillis: Long
    ) {
        require(symbol.isNotBlank())
        if (!result.accepted || result.duplicate || previous.status == result.state.status) return
        val wasOpen = previous.status.isOccupyingSymbol()
        val isOpen = result.state.status.isOccupyingSymbol()
        when {
            !wasOpen && isOpen -> eventSource.publishConfirmedOpen(result.state.orderId, symbol, observedAtMillis)
            wasOpen && !isOpen -> eventSource.publishConfirmedClose(result.state.orderId, symbol, observedAtMillis)
        }
    }

    private fun OrderStatus.isOccupyingSymbol(): Boolean = this in setOf(
        OrderStatus.SUBMITTED,
        OrderStatus.UNKNOWN,
        OrderStatus.ACKNOWLEDGED,
        OrderStatus.PARTIALLY_FILLED,
        OrderStatus.PENDING_CANCEL
    )
}
