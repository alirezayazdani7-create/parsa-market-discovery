package com.example.trading.orders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderLifecycleTest {
    private val lifecycle = OrderLifecycle()

    private fun initial(quantity: Double = 10.0) = OrderState(
        orderId = "ord-1",
        clientOrderId = "client-1",
        originalQuantity = quantity
    )

    private fun event(
        id: String,
        type: OrderEventType,
        fillId: String? = null,
        fillQuantity: Double? = null,
        status: OrderStatus? = null,
        cumulative: Double? = null
    ) = OrderEvent(
        eventId = id,
        orderId = "ord-1",
        type = type,
        eventTimeMillis = id.filter { it.isDigit() }.toLongOrNull() ?: 1L,
        fillId = fillId,
        fillQuantity = fillQuantity,
        observedStatus = status,
        observedCumulativeFilledQuantity = cumulative
    )

    private fun apply(state: OrderState, event: OrderEvent): OrderState {
        val result = lifecycle.apply(state, event)
        assertTrue(result.error ?: "transition should be accepted", result.accepted)
        return result.state
    }

    @Test
    fun normalLifecycle_reachesFilled() {
        var state = initial()
        state = apply(state, event("e1", OrderEventType.SUBMIT_SENT))
        assertEquals(OrderStatus.SUBMITTED, state.status)
        state = apply(state, event("e2", OrderEventType.EXCHANGE_ACKNOWLEDGED))
        assertEquals(OrderStatus.ACKNOWLEDGED, state.status)
        state = apply(state, event("e3", OrderEventType.FILL_RECEIVED, "fill-1", 4.0))
        assertEquals(OrderStatus.PARTIALLY_FILLED, state.status)
        state = apply(state, event("e4", OrderEventType.FILL_RECEIVED, "fill-2", 6.0))
        assertEquals(OrderStatus.FILLED, state.status)
        assertEquals(10.0, state.cumulativeFilledQuantity, 0.0000001)
    }

    @Test
    fun duplicateEventId_isIdempotent() {
        val submitted = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        val first = lifecycle.apply(submitted, event("e2", OrderEventType.FILL_RECEIVED, "fill-1", 2.0))
        val replay = lifecycle.apply(first.state, event("e2", OrderEventType.FILL_RECEIVED, "fill-1", 2.0))
        assertTrue(replay.accepted)
        assertTrue(replay.duplicate)
        assertEquals(2.0, replay.state.cumulativeFilledQuantity, 0.0000001)
    }

    @Test
    fun duplicateFillId_withDifferentEventId_doesNotDoubleCount() {
        val submitted = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        val first = apply(submitted, event("e2", OrderEventType.FILL_RECEIVED, "same-fill", 3.0))
        val replay = lifecycle.apply(first, event("e3", OrderEventType.FILL_RECEIVED, "same-fill", 3.0))
        assertTrue(replay.accepted)
        assertTrue(replay.duplicate)
        assertEquals(3.0, replay.state.cumulativeFilledQuantity, 0.0000001)
    }

    @Test
    fun cancelRequest_isNotCancelConfirmation() {
        var state = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        state = apply(state, event("e2", OrderEventType.EXCHANGE_ACKNOWLEDGED))
        state = apply(state, event("e3", OrderEventType.CANCEL_REQUESTED))
        assertEquals(OrderStatus.PENDING_CANCEL, state.status)
        state = apply(state, event("e4", OrderEventType.CANCEL_CONFIRMED))
        assertEquals(OrderStatus.CANCELED, state.status)
    }

    @Test
    fun fillDuringPendingCancel_isRecordedBeforeCancelConfirmation() {
        var state = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        state = apply(state, event("e2", OrderEventType.EXCHANGE_ACKNOWLEDGED))
        state = apply(state, event("e3", OrderEventType.CANCEL_REQUESTED))
        state = apply(state, event("e4", OrderEventType.FILL_RECEIVED, "fill-1", 2.5))
        assertEquals(OrderStatus.PENDING_CANCEL, state.status)
        assertEquals(2.5, state.cumulativeFilledQuantity, 0.0000001)
        state = apply(state, event("e5", OrderEventType.CANCEL_CONFIRMED))
        assertEquals(OrderStatus.CANCELED, state.status)
        assertEquals(2.5, state.cumulativeFilledQuantity, 0.0000001)
    }

    @Test
    fun ambiguousSubmitOutcome_requiresReconciliation_notResubmission() {
        var state = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        state = apply(state, event("e2", OrderEventType.TRANSPORT_OUTCOME_UNKNOWN))
        assertEquals(OrderStatus.UNKNOWN, state.status)

        val blindResubmit = lifecycle.apply(state, event("e3", OrderEventType.SUBMIT_SENT))
        assertFalse(blindResubmit.accepted)
        assertEquals(OrderStatus.UNKNOWN, blindResubmit.state.status)

        state = apply(
            state,
            event("e4", OrderEventType.RECONCILIATION_RESULT, status = OrderStatus.PARTIALLY_FILLED, cumulative = 1.0)
        )
        assertEquals(OrderStatus.PARTIALLY_FILLED, state.status)
        assertEquals(1.0, state.cumulativeFilledQuantity, 0.0000001)
    }

    @Test
    fun overfill_isRejectedWithoutMutation() {
        val submitted = apply(initial(5.0), event("e1", OrderEventType.SUBMIT_SENT))
        val result = lifecycle.apply(submitted, event("e2", OrderEventType.FILL_RECEIVED, "fill-1", 6.0))
        assertFalse(result.accepted)
        assertEquals(submitted, result.state)
    }

    @Test
    fun malformedFill_isRejected() {
        val submitted = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        val result = lifecycle.apply(submitted, event("e2", OrderEventType.FILL_RECEIVED, "fill-1", 0.0))
        assertFalse(result.accepted)
        assertEquals(0.0, result.state.cumulativeFilledQuantity, 0.0000001)
    }

    @Test
    fun wrongOrderId_isRejected() {
        val result = lifecycle.apply(
            initial(),
            OrderEvent("e1", "another-order", OrderEventType.SUBMIT_SENT, 1L)
        )
        assertFalse(result.accepted)
        assertEquals(OrderStatus.NEW, result.state.status)
    }

    @Test
    fun cancelConfirmationWithoutPendingCancel_isRejected() {
        val submitted = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        val result = lifecycle.apply(submitted, event("e2", OrderEventType.CANCEL_CONFIRMED))
        assertFalse(result.accepted)
        assertEquals(OrderStatus.SUBMITTED, result.state.status)
    }

    @Test
    fun emptyEventId_isRejectedWithoutMutation() {
        val result = lifecycle.apply(initial(), event(" ", OrderEventType.SUBMIT_SENT))
        assertFalse(result.accepted)
        assertEquals(OrderStatus.NEW, result.state.status)
    }

    @Test
    fun invalidOriginalQuantities_areRejectedWithoutMutation() {
        for (quantity in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val result = lifecycle.apply(
                initial(quantity),
                event("e1", OrderEventType.SUBMIT_SENT)
            )
            assertFalse("quantity=$quantity", result.accepted)
            assertEquals(OrderStatus.NEW, result.state.status)
        }
    }

    @Test
    fun negativeAndNonFiniteFills_areRejectedWithoutMutation() {
        val submitted = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        for ((index, quantity) in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).withIndex()) {
            val result = lifecycle.apply(
                submitted,
                event("e${index + 2}", OrderEventType.FILL_RECEIVED, "fill-${index + 1}", quantity)
            )
            assertFalse("quantity=$quantity", result.accepted)
            assertEquals(submitted, result.state)
        }
    }

    @Test
    fun reconciliation_rejectsDecreasingAndExcessiveFillTotals() {
        var state = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        state = apply(state, event("e2", OrderEventType.FILL_RECEIVED, "fill-1", 3.0))

        val decreasing = lifecycle.apply(
            state,
            event("e3", OrderEventType.RECONCILIATION_RESULT, status = OrderStatus.PARTIALLY_FILLED, cumulative = 2.0)
        )
        assertFalse(decreasing.accepted)
        assertEquals(state, decreasing.state)

        val excessive = lifecycle.apply(
            state,
            event("e4", OrderEventType.RECONCILIATION_RESULT, status = OrderStatus.FILLED, cumulative = 11.0)
        )
        assertFalse(excessive.accepted)
        assertEquals(state, excessive.state)
    }

    @Test
    fun reconciliation_rejectsRejectedStatusWithExistingFills() {
        var state = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        state = apply(state, event("e2", OrderEventType.FILL_RECEIVED, "fill-1", 1.0))

        val result = lifecycle.apply(
            state,
            event("e3", OrderEventType.RECONCILIATION_RESULT, status = OrderStatus.REJECTED, cumulative = 1.0)
        )
        assertFalse(result.accepted)
        assertEquals(state, result.state)
    }

    @Test
    fun fullFillWhileCancelPending_finishesAsFilled() {
        var state = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        state = apply(state, event("e2", OrderEventType.CANCEL_REQUESTED))
        state = apply(state, event("e3", OrderEventType.FILL_RECEIVED, "fill-1", 10.0))
        assertEquals(OrderStatus.FILLED, state.status)
        assertEquals(10.0, state.cumulativeFilledQuantity, 0.0000001)
    }
}
