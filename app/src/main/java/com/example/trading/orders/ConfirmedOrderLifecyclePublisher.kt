package com.example.trading.orders

/**
 * Bridges accepted pure state-machine transitions to the durable local order event source.
 * It never creates orders or contacts an exchange. Call it with the actual result returned by
 * OrderLifecycle.apply after an authoritative order event has been processed.
 */
class ConfirmedOrderLifecyclePublisher(
    private val eventSource: RoomConfirmedOrderLifecycleEventSource
) {
    suspend fun publishTransition(
        previous: OrderState,
        result: OrderTransitionResult,
        symbol: String,
        observedAtMillis: Long
    ) {
        require(symbol.isNotBlank())
        require(observedAtMillis >= 0L)
        if (!result.accepted || result.duplicate || previous.status == result.state.status) return

        val wasOpen = previous.status.isOccupyingSymbol()
        val isOpen = result.state.status.isOccupyingSymbol()
        when {
            !wasOpen && isOpen ->
                eventSource.publishConfirmedOpen(result.state.orderId, symbol, observedAtMillis)
            wasOpen && !isOpen ->
                eventSource.publishConfirmedClose(result.state.orderId, symbol, observedAtMillis)
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
