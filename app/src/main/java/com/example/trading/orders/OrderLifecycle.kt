package com.example.trading.orders

/**
 * Deterministic, exchange-agnostic order lifecycle.
 *
 * This class has no network, persistence, scheduler, or execution capability.
 * It only validates an event against a supplied immutable order snapshot.
 */
enum class OrderStatus {
    NEW,
    SUBMITTED,
    UNKNOWN,
    ACKNOWLEDGED,
    PARTIALLY_FILLED,
    PENDING_CANCEL,
    FILLED,
    CANCELED,
    REJECTED,
    EXPIRED
}

enum class OrderEventType {
    SUBMIT_SENT,
    EXCHANGE_ACKNOWLEDGED,
    TRANSPORT_OUTCOME_UNKNOWN,
    FILL_RECEIVED,
    CANCEL_REQUESTED,
    CANCEL_CONFIRMED,
    EXCHANGE_REJECTED,
    EXCHANGE_EXPIRED,
    RECONCILIATION_RESULT
}

data class OrderState(
    val orderId: String,
    val clientOrderId: String,
    val originalQuantity: Double,
    val status: OrderStatus = OrderStatus.NEW,
    val cumulativeFilledQuantity: Double = 0.0,
    val processedEventIds: Set<String> = emptySet(),
    val processedFillIds: Set<String> = emptySet(),
    val lastEventTimeMillis: Long = 0L
)

data class OrderEvent(
    val eventId: String,
    val orderId: String,
    val type: OrderEventType,
    val eventTimeMillis: Long,
    val fillId: String? = null,
    val fillQuantity: Double? = null,
    val observedStatus: OrderStatus? = null,
    val observedCumulativeFilledQuantity: Double? = null,
    val reason: String? = null
)

data class OrderTransitionResult(
    val accepted: Boolean,
    val duplicate: Boolean,
    val state: OrderState,
    val error: String? = null
)

class OrderLifecycle {
    fun apply(current: OrderState, event: OrderEvent): OrderTransitionResult {
        if (current.orderId.isBlank() || current.clientOrderId.isBlank()) {
            return reject(current, "orderId and clientOrderId must be non-empty")
        }
        if (!current.originalQuantity.isFinite() || current.originalQuantity <= 0.0) {
            return reject(current, "originalQuantity must be finite and greater than zero")
        }
        if (!current.cumulativeFilledQuantity.isFinite() ||
            current.cumulativeFilledQuantity < 0.0 ||
            current.cumulativeFilledQuantity > current.originalQuantity
        ) {
            return reject(current, "current cumulative fill quantity is invalid")
        }
        if (event.eventId.isBlank()) return reject(current, "eventId must be non-empty")
        if (event.orderId != current.orderId) return reject(current, "event orderId does not match state")

        if (event.eventId in current.processedEventIds) {
            return OrderTransitionResult(true, true, current)
        }

        val eventIds = current.processedEventIds + event.eventId
        val eventTime = maxOf(current.lastEventTimeMillis, event.eventTimeMillis)

        fun accepted(
            status: OrderStatus = current.status,
            filled: Double = current.cumulativeFilledQuantity,
            fillIds: Set<String> = current.processedFillIds
        ) = OrderTransitionResult(
            accepted = true,
            duplicate = false,
            state = current.copy(
                status = status,
                cumulativeFilledQuantity = filled,
                processedEventIds = eventIds,
                processedFillIds = fillIds,
                lastEventTimeMillis = eventTime
            )
        )

        return when (event.type) {
            OrderEventType.SUBMIT_SENT -> {
                if (current.status != OrderStatus.NEW) reject(current, "SUBMIT_SENT is only valid from NEW")
                else accepted(status = OrderStatus.SUBMITTED)
            }

            OrderEventType.EXCHANGE_ACKNOWLEDGED -> {
                when (current.status) {
                    OrderStatus.SUBMITTED, OrderStatus.UNKNOWN -> accepted(status = OrderStatus.ACKNOWLEDGED)
                    OrderStatus.PARTIALLY_FILLED, OrderStatus.PENDING_CANCEL,
                    OrderStatus.FILLED, OrderStatus.CANCELED -> accepted()
                    else -> reject(current, "acknowledgement is invalid from ${current.status}")
                }
            }

            OrderEventType.TRANSPORT_OUTCOME_UNKNOWN -> {
                if (current.status == OrderStatus.SUBMITTED) accepted(status = OrderStatus.UNKNOWN)
                else reject(current, "ambiguous transport result is only valid from SUBMITTED")
            }

            OrderEventType.FILL_RECEIVED -> applyFill(current, event, eventIds, eventTime)

            OrderEventType.CANCEL_REQUESTED -> {
                when (current.status) {
                    OrderStatus.SUBMITTED, OrderStatus.ACKNOWLEDGED, OrderStatus.PARTIALLY_FILLED ->
                        accepted(status = OrderStatus.PENDING_CANCEL)
                    else -> reject(current, "cancel request is invalid from ${current.status}")
                }
            }

            OrderEventType.CANCEL_CONFIRMED -> {
                if (current.status != OrderStatus.PENDING_CANCEL) {
                    reject(current, "cancel confirmation requires PENDING_CANCEL")
                } else if (current.cumulativeFilledQuantity >= current.originalQuantity) {
                    accepted(status = OrderStatus.FILLED)
                } else {
                    accepted(status = OrderStatus.CANCELED)
                }
            }

            OrderEventType.EXCHANGE_REJECTED -> {
                if (current.cumulativeFilledQuantity > 0.0) {
                    reject(current, "cannot mark a partially filled order REJECTED; reconcile/cancel its remainder")
                } else if (current.status in setOf(OrderStatus.SUBMITTED, OrderStatus.UNKNOWN, OrderStatus.ACKNOWLEDGED)) {
                    accepted(status = OrderStatus.REJECTED)
                } else {
                    reject(current, "rejection is invalid from ${current.status}")
                }
            }

            OrderEventType.EXCHANGE_EXPIRED -> {
                if (current.status in setOf(OrderStatus.SUBMITTED, OrderStatus.UNKNOWN, OrderStatus.ACKNOWLEDGED, OrderStatus.PENDING_CANCEL)) {
                    if (current.cumulativeFilledQuantity >= current.originalQuantity) accepted(status = OrderStatus.FILLED)
                    else accepted(status = OrderStatus.EXPIRED)
                } else {
                    reject(current, "expiry is invalid from ${current.status}")
                }
            }

            OrderEventType.RECONCILIATION_RESULT -> applyReconciliation(current, event, eventIds, eventTime)
        }
    }

    private fun applyFill(
        current: OrderState,
        event: OrderEvent,
        eventIds: Set<String>,
        eventTime: Long
    ): OrderTransitionResult {
        val fillId = event.fillId
        val quantity = event.fillQuantity
        if (fillId.isNullOrBlank()) return reject(current, "fillId must be non-empty")
        if (quantity == null || !quantity.isFinite() || quantity <= 0.0) {
            return reject(current, "fillQuantity must be finite and greater than zero")
        }
        if (fillId in current.processedFillIds) {
            return OrderTransitionResult(
                accepted = true,
                duplicate = true,
                state = current.copy(
                    processedEventIds = eventIds,
                    lastEventTimeMillis = maxOf(current.lastEventTimeMillis, eventTime)
                )
            )
        }
        if (current.status !in setOf(
                OrderStatus.SUBMITTED,
                OrderStatus.UNKNOWN,
                OrderStatus.ACKNOWLEDGED,
                OrderStatus.PARTIALLY_FILLED,
                OrderStatus.PENDING_CANCEL,
                OrderStatus.CANCELED
            )
        ) {
            return reject(current, "fill is invalid from ${current.status}")
        }

        val total = current.cumulativeFilledQuantity + quantity
        if (total > current.originalQuantity + QUANTITY_EPSILON) {
            return reject(current, "fill would exceed original order quantity")
        }
        val normalizedTotal = minOf(total, current.originalQuantity)
        val nextStatus = when {
            normalizedTotal >= current.originalQuantity - QUANTITY_EPSILON -> OrderStatus.FILLED
            current.status == OrderStatus.PENDING_CANCEL -> OrderStatus.PENDING_CANCEL
            current.status == OrderStatus.CANCELED -> OrderStatus.CANCELED
            else -> OrderStatus.PARTIALLY_FILLED
        }
        return OrderTransitionResult(
            accepted = true,
            duplicate = false,
            state = current.copy(
                status = nextStatus,
                cumulativeFilledQuantity = normalizedTotal,
                processedEventIds = eventIds,
                processedFillIds = current.processedFillIds + fillId,
                lastEventTimeMillis = maxOf(current.lastEventTimeMillis, eventTime)
            )
        )
    }

    private fun applyReconciliation(
        current: OrderState,
        event: OrderEvent,
        eventIds: Set<String>,
        eventTime: Long
    ): OrderTransitionResult {
        val observed = event.observedStatus ?: return reject(current, "observedStatus is required")
        val filled = event.observedCumulativeFilledQuantity
            ?: return reject(current, "observed cumulative fill quantity is required")
        if (!filled.isFinite() || filled < current.cumulativeFilledQuantity ||
            filled > current.originalQuantity
        ) {
            return reject(current, "reconciled cumulative fill must be finite, monotonic, and within order quantity")
        }
        if (current.status !in setOf(
                OrderStatus.UNKNOWN,
                OrderStatus.SUBMITTED,
                OrderStatus.ACKNOWLEDGED,
                OrderStatus.PARTIALLY_FILLED,
                OrderStatus.PENDING_CANCEL
            )
        ) {
            return reject(current, "reconciliation is invalid from ${current.status}")
        }

        val consistent = when (observed) {
            OrderStatus.ACKNOWLEDGED -> filled == 0.0
            OrderStatus.PARTIALLY_FILLED -> filled > 0.0 && filled < current.originalQuantity
            OrderStatus.FILLED -> filled == current.originalQuantity
            OrderStatus.CANCELED, OrderStatus.EXPIRED ->
                filled < current.originalQuantity
            // A rejected order cannot already have exchange-confirmed fills.
            // If the remainder was rejected after partial execution, the exchange
            // outcome must be represented as CANCELED/EXPIRED plus the fill total.
            OrderStatus.REJECTED -> filled == 0.0
            else -> false
        }
        if (!consistent) return reject(current, "observed status and cumulative fill quantity are inconsistent")

        return OrderTransitionResult(
            accepted = true,
            duplicate = false,
            state = current.copy(
                status = observed,
                cumulativeFilledQuantity = filled,
                processedEventIds = eventIds,
                lastEventTimeMillis = maxOf(current.lastEventTimeMillis, eventTime)
            )
        )
    }

    private fun reject(current: OrderState, reason: String) =
        OrderTransitionResult(accepted = false, duplicate = false, state = current, error = reason)

    companion object {
        private const val QUANTITY_EPSILON = 1e-10
    }
}
