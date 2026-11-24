package com.example.trading.orders

import java.math.BigDecimal

/** Pure, deterministic order-state transition validator. No I/O or execution capability. */
enum class OrderStatus { NEW, SUBMITTED, UNKNOWN, ACKNOWLEDGED, PARTIALLY_FILLED, PENDING_CANCEL, FILLED, CANCELED, REJECTED, EXPIRED }
enum class OrderEventType { SUBMIT_SENT, EXCHANGE_ACKNOWLEDGED, TRANSPORT_OUTCOME_UNKNOWN, FILL_RECEIVED, CANCEL_REQUESTED, CANCEL_CONFIRMED, EXCHANGE_REJECTED, EXCHANGE_EXPIRED, RECONCILIATION_RESULT }

data class OrderState(
    val orderId: String,
    val clientOrderId: String,
    val originalQuantity: BigDecimal,
    val status: OrderStatus = OrderStatus.NEW,
    val cumulativeFilledQuantity: BigDecimal = BigDecimal.ZERO,
    /** Event ID -> canonical payload fingerprint. Same ID with different payload is a conflict. */
    val processedEventFingerprints: Map<String, String> = emptyMap(),
    /** Exchange fill ID -> exact quantity. Reuse with different quantity is a conflict. */
    val processedFillQuantities: Map<String, BigDecimal> = emptyMap(),
    /** After a snapshot reconciliation, subsequent fills must carry exchange cumulative quantity. */
    val requiresCumulativeFillOnNextEvents: Boolean = false,
    val lastEventTimeMillis: Long = 0L
)

data class OrderEvent(
    val eventId: String,
    val orderId: String,
    val type: OrderEventType,
    val eventTimeMillis: Long,
    val fillId: String? = null,
    val fillQuantity: BigDecimal? = null,
    val observedStatus: OrderStatus? = null,
    val observedCumulativeFilledQuantity: BigDecimal? = null,
    val reason: String? = null
)

data class OrderTransitionResult(val accepted: Boolean, val duplicate: Boolean, val state: OrderState, val error: String? = null)

/** Stable length-prefixed payload identity shared by lifecycle validation and durable storage. */
internal fun OrderEvent.canonicalFingerprint(): String {
    val fields = listOf(
        orderId, type.name, eventTimeMillis.toString(), fillId.orEmpty(),
        fillQuantity?.stripTrailingZeros()?.toPlainString().orEmpty(), observedStatus?.name.orEmpty(),
        observedCumulativeFilledQuantity?.stripTrailingZeros()?.toPlainString().orEmpty(), reason.orEmpty()
    )
    return fields.joinToString("") { it.length.toString() + ":" + it }
}

class OrderLifecycle {
    fun apply(current: OrderState, event: OrderEvent): OrderTransitionResult {
        if (current.orderId.isBlank() || current.clientOrderId.isBlank()) return reject(current, "orderId and clientOrderId must be non-empty")
        if (current.originalQuantity <= BigDecimal.ZERO) return reject(current, "originalQuantity must be greater than zero")
        if (current.cumulativeFilledQuantity < BigDecimal.ZERO || current.cumulativeFilledQuantity > current.originalQuantity) return reject(current, "current cumulative fill quantity is invalid")
        if (event.eventId.isBlank()) return reject(current, "eventId must be non-empty")
        if (event.orderId != current.orderId) return reject(current, "event orderId does not match state")

        val fingerprint = fingerprint(event)
        val priorFingerprint = current.processedEventFingerprints[event.eventId]
        if (priorFingerprint != null) {
            return if (priorFingerprint == fingerprint) OrderTransitionResult(true, true, current)
            else reject(current, "eventId was reused with a different payload")
        }
        val eventFingerprints = current.processedEventFingerprints + (event.eventId to fingerprint)
        val eventTime = maxOf(current.lastEventTimeMillis, event.eventTimeMillis)

        fun accepted(status: OrderStatus = current.status, filled: BigDecimal = current.cumulativeFilledQuantity,
                     fillQuantities: Map<String, BigDecimal> = current.processedFillQuantities,
                     requiresCumulative: Boolean = current.requiresCumulativeFillOnNextEvents) =
            OrderTransitionResult(true, false, current.copy(status = status, cumulativeFilledQuantity = filled,
                processedEventFingerprints = eventFingerprints, processedFillQuantities = fillQuantities,
                requiresCumulativeFillOnNextEvents = requiresCumulative, lastEventTimeMillis = eventTime))

        return when (event.type) {
            OrderEventType.SUBMIT_SENT -> if (current.status == OrderStatus.NEW) accepted(status = OrderStatus.SUBMITTED) else reject(current, "SUBMIT_SENT is only valid from NEW")
            OrderEventType.EXCHANGE_ACKNOWLEDGED -> when (current.status) {
                OrderStatus.SUBMITTED, OrderStatus.UNKNOWN -> accepted(status = OrderStatus.ACKNOWLEDGED)
                OrderStatus.PARTIALLY_FILLED, OrderStatus.PENDING_CANCEL, OrderStatus.FILLED, OrderStatus.CANCELED -> accepted()
                else -> reject(current, "acknowledgement is invalid from ${current.status}")
            }
            OrderEventType.TRANSPORT_OUTCOME_UNKNOWN -> if (current.status == OrderStatus.SUBMITTED) accepted(status = OrderStatus.UNKNOWN) else reject(current, "ambiguous transport result is only valid from SUBMITTED")
            OrderEventType.FILL_RECEIVED -> applyFill(current, event, eventFingerprints, eventTime)
            OrderEventType.CANCEL_REQUESTED -> if (current.status in setOf(OrderStatus.SUBMITTED, OrderStatus.ACKNOWLEDGED, OrderStatus.PARTIALLY_FILLED)) accepted(status = OrderStatus.PENDING_CANCEL) else reject(current, "cancel request is invalid from ${current.status}")
            OrderEventType.CANCEL_CONFIRMED -> if (current.status != OrderStatus.PENDING_CANCEL) reject(current, "cancel confirmation requires PENDING_CANCEL")
                else if (current.cumulativeFilledQuantity.compareTo(current.originalQuantity) == 0) accepted(status = OrderStatus.FILLED)
                else accepted(status = OrderStatus.CANCELED)
            OrderEventType.EXCHANGE_REJECTED -> if (current.cumulativeFilledQuantity > BigDecimal.ZERO) reject(current, "cannot mark a partially filled order REJECTED; reconcile/cancel its remainder")
                else if (current.status in setOf(OrderStatus.SUBMITTED, OrderStatus.UNKNOWN, OrderStatus.ACKNOWLEDGED)) accepted(status = OrderStatus.REJECTED)
                else reject(current, "rejection is invalid from ${current.status}")
            OrderEventType.EXCHANGE_EXPIRED -> if (current.status in setOf(OrderStatus.SUBMITTED, OrderStatus.UNKNOWN, OrderStatus.ACKNOWLEDGED, OrderStatus.PENDING_CANCEL))
                accepted(status = if (current.cumulativeFilledQuantity.compareTo(current.originalQuantity) == 0) OrderStatus.FILLED else OrderStatus.EXPIRED)
                else reject(current, "expiry is invalid from ${current.status}")
            OrderEventType.RECONCILIATION_RESULT -> applyReconciliation(current, event, eventFingerprints, eventTime)
        }
    }

    private fun applyFill(current: OrderState, event: OrderEvent, eventFingerprints: Map<String, String>, eventTime: Long): OrderTransitionResult {
        val fillId = event.fillId?.takeIf { it.isNotBlank() } ?: return reject(current, "fillId must be non-empty")
        val quantity = event.fillQuantity ?: return reject(current, "fillQuantity is required")
        if (quantity <= BigDecimal.ZERO) return reject(current, "fillQuantity must be greater than zero")
        val priorFillQuantity = current.processedFillQuantities[fillId]
        if (priorFillQuantity != null) {
            return if (priorFillQuantity.compareTo(quantity) == 0) OrderTransitionResult(true, true,
                current.copy(processedEventFingerprints = eventFingerprints, lastEventTimeMillis = maxOf(current.lastEventTimeMillis, eventTime)))
            else reject(current, "fillId was reused with a different quantity")
        }
        if (current.status !in setOf(OrderStatus.SUBMITTED, OrderStatus.UNKNOWN, OrderStatus.ACKNOWLEDGED, OrderStatus.PARTIALLY_FILLED, OrderStatus.PENDING_CANCEL, OrderStatus.CANCELED))
            return reject(current, "fill is invalid from ${current.status}")

        val observedCumulative = event.observedCumulativeFilledQuantity
        val nextFilled = if (current.requiresCumulativeFillOnNextEvents || observedCumulative != null) {
            if (observedCumulative == null) return reject(current, "cumulative fill quantity is required after reconciliation")
            if (observedCumulative < current.cumulativeFilledQuantity || observedCumulative > current.originalQuantity)
                return reject(current, "observed cumulative fill must be monotonic and within original quantity")
            val delta = observedCumulative.subtract(current.cumulativeFilledQuantity)
            // Zero means this execution was already included in the exchange snapshot.
            // A full quantity delta means it is a new execution; other deltas are ambiguous.
            if (delta.compareTo(BigDecimal.ZERO) != 0 && delta.compareTo(quantity) != 0)
                return reject(current, "fill quantity does not match cumulative quantity delta")
            observedCumulative
        } else current.cumulativeFilledQuantity.add(quantity)

        if (nextFilled > current.originalQuantity) return reject(current, "fill would exceed original order quantity")
        val nextStatus = when {
            nextFilled.compareTo(current.originalQuantity) == 0 -> OrderStatus.FILLED
            current.status == OrderStatus.PENDING_CANCEL -> OrderStatus.PENDING_CANCEL
            current.status == OrderStatus.CANCELED -> OrderStatus.CANCELED
            else -> OrderStatus.PARTIALLY_FILLED
        }
        return OrderTransitionResult(true, false, current.copy(status = nextStatus, cumulativeFilledQuantity = nextFilled,
            processedEventFingerprints = eventFingerprints, processedFillQuantities = current.processedFillQuantities + (fillId to quantity),
            requiresCumulativeFillOnNextEvents = current.requiresCumulativeFillOnNextEvents,
            lastEventTimeMillis = maxOf(current.lastEventTimeMillis, eventTime)))
    }

    private fun applyReconciliation(current: OrderState, event: OrderEvent, eventFingerprints: Map<String, String>, eventTime: Long): OrderTransitionResult {
        val observed = event.observedStatus ?: return reject(current, "observedStatus is required")
        val filled = event.observedCumulativeFilledQuantity ?: return reject(current, "observed cumulative fill quantity is required")
        if (filled < current.cumulativeFilledQuantity || filled > current.originalQuantity)
            return reject(current, "reconciled cumulative fill must be monotonic and within order quantity")
        if (current.status !in setOf(OrderStatus.UNKNOWN, OrderStatus.SUBMITTED, OrderStatus.ACKNOWLEDGED, OrderStatus.PARTIALLY_FILLED, OrderStatus.PENDING_CANCEL))
            return reject(current, "reconciliation is invalid from ${current.status}")
        val consistent = when (observed) {
            OrderStatus.ACKNOWLEDGED -> filled.compareTo(BigDecimal.ZERO) == 0
            OrderStatus.PARTIALLY_FILLED -> filled > BigDecimal.ZERO && filled < current.originalQuantity
            OrderStatus.FILLED -> filled.compareTo(current.originalQuantity) == 0
            OrderStatus.CANCELED, OrderStatus.EXPIRED -> filled < current.originalQuantity
            OrderStatus.REJECTED -> filled.compareTo(BigDecimal.ZERO) == 0
            else -> false
        }
        if (!consistent) return reject(current, "observed status and cumulative fill quantity are inconsistent")
        return OrderTransitionResult(true, false, current.copy(status = observed, cumulativeFilledQuantity = filled,
            processedEventFingerprints = eventFingerprints, requiresCumulativeFillOnNextEvents = true,
            lastEventTimeMillis = maxOf(current.lastEventTimeMillis, eventTime)))
    }

    private fun fingerprint(event: OrderEvent): String = event.canonicalFingerprint()
    private fun reject(current: OrderState, reason: String) = OrderTransitionResult(false, false, current, reason)
}
