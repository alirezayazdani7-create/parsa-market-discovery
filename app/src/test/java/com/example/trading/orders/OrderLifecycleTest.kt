package com.example.trading.orders

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderLifecycleTest {
    private val lifecycle = OrderLifecycle()
    private fun q(value: String) = BigDecimal(value)
    private fun initial(quantity: String = "10") = OrderState("ord-1", "client-1", q(quantity))
    private fun event(id: String, type: OrderEventType, fillId: String? = null, fillQuantity: String? = null,
                      status: OrderStatus? = null, cumulative: String? = null) = OrderEvent(
        id, "ord-1", type, id.filter { it.isDigit() }.toLongOrNull() ?: 1L,
        fillId, fillQuantity?.let(::q), status, cumulative?.let(::q)
    )
    private fun apply(state: OrderState, event: OrderEvent): OrderState {
        val result = lifecycle.apply(state, event)
        assertTrue(result.error ?: "transition should be accepted", result.accepted)
        return result.state
    }

    @Test fun normalLifecycle_reachesFilled() {
        var s = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        s = apply(s, event("e2", OrderEventType.EXCHANGE_ACKNOWLEDGED))
        s = apply(s, event("e3", OrderEventType.FILL_RECEIVED, "f1", "4"))
        assertEquals(OrderStatus.PARTIALLY_FILLED, s.status)
        s = apply(s, event("e4", OrderEventType.FILL_RECEIVED, "f2", "6"))
        assertEquals(OrderStatus.FILLED, s.status)
        assertEquals(0, s.cumulativeFilledQuantity.compareTo(q("10")))
    }

    @Test fun sameEventIdSamePayload_isIdempotent() {
        val s = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        val first = lifecycle.apply(s, event("e2", OrderEventType.FILL_RECEIVED, "f1", "2"))
        val replay = lifecycle.apply(first.state, event("e2", OrderEventType.FILL_RECEIVED, "f1", "2"))
        assertTrue(replay.accepted); assertTrue(replay.duplicate)
        assertEquals(0, replay.state.cumulativeFilledQuantity.compareTo(q("2")))
    }

    @Test fun sameEventIdDifferentPayload_isRejected() {
        val s = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        val first = lifecycle.apply(s, event("e2", OrderEventType.FILL_RECEIVED, "f1", "2"))
        val conflict = lifecycle.apply(first.state, event("e2", OrderEventType.FILL_RECEIVED, "f2", "3"))
        assertFalse(conflict.accepted); assertEquals(first.state, conflict.state)
    }

    @Test fun sameFillIdDifferentQuantity_isRejected() {
        val s = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        val first = apply(s, event("e2", OrderEventType.FILL_RECEIVED, "f1", "2"))
        val conflict = lifecycle.apply(first, event("e3", OrderEventType.FILL_RECEIVED, "f1", "3"))
        assertFalse(conflict.accepted); assertEquals(first, conflict.state)
    }

    @Test fun duplicateFillIdSameQuantity_doesNotDoubleCount() {
        val s = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        val first = apply(s, event("e2", OrderEventType.FILL_RECEIVED, "f1", "2"))
        val replay = lifecycle.apply(first, event("e3", OrderEventType.FILL_RECEIVED, "f1", "2"))
        assertTrue(replay.accepted); assertTrue(replay.duplicate)
        assertEquals(0, replay.state.cumulativeFilledQuantity.compareTo(q("2")))
    }

    @Test fun reconciliationThenLateFill_doesNotDoubleCount() {
        var s = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        s = apply(s, event("e2", OrderEventType.TRANSPORT_OUTCOME_UNKNOWN))
        s = apply(s, event("e3", OrderEventType.RECONCILIATION_RESULT, status = OrderStatus.PARTIALLY_FILLED, cumulative = "1"))
        val lateAlreadyIncluded = lifecycle.apply(s, event("e4", OrderEventType.FILL_RECEIVED, "f1", "1", cumulative = "1"))
        assertTrue(lateAlreadyIncluded.accepted)
        assertEquals(0, lateAlreadyIncluded.state.cumulativeFilledQuantity.compareTo(q("1")))
        val nextFill = lifecycle.apply(lateAlreadyIncluded.state, event("e5", OrderEventType.FILL_RECEIVED, "f2", "2", cumulative = "3"))
        assertTrue(nextFill.accepted)
        assertEquals(0, nextFill.state.cumulativeFilledQuantity.compareTo(q("3")))
    }

    @Test fun postReconciliationFillWithoutCumulative_isRejected() {
        var s = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        s = apply(s, event("e2", OrderEventType.RECONCILIATION_RESULT, status = OrderStatus.ACKNOWLEDGED, cumulative = "0"))
        val result = lifecycle.apply(s, event("e3", OrderEventType.FILL_RECEIVED, "f1", "1"))
        assertFalse(result.accepted)
    }

    @Test fun cancelRequestIsNotConfirmation_andPartialFillIsPreserved() {
        var s = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        s = apply(s, event("e2", OrderEventType.CANCEL_REQUESTED))
        s = apply(s, event("e3", OrderEventType.FILL_RECEIVED, "f1", "2.5"))
        assertEquals(OrderStatus.PENDING_CANCEL, s.status)
        s = apply(s, event("e4", OrderEventType.CANCEL_CONFIRMED))
        assertEquals(OrderStatus.CANCELED, s.status)
        assertEquals(0, s.cumulativeFilledQuantity.compareTo(q("2.5")))
    }

    @Test fun fullFillWhileCancelPending_finishesFilled() {
        var s = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        s = apply(s, event("e2", OrderEventType.CANCEL_REQUESTED))
        s = apply(s, event("e3", OrderEventType.FILL_RECEIVED, "f1", "10"))
        assertEquals(OrderStatus.FILLED, s.status)
    }

    @Test fun ambiguousSubmit_requiresReconciliation_notResubmit() {
        var s = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        s = apply(s, event("e2", OrderEventType.TRANSPORT_OUTCOME_UNKNOWN))
        assertFalse(lifecycle.apply(s, event("e3", OrderEventType.SUBMIT_SENT)).accepted)
        s = apply(s, event("e4", OrderEventType.RECONCILIATION_RESULT, status = OrderStatus.PARTIALLY_FILLED, cumulative = "1"))
        assertEquals(OrderStatus.PARTIALLY_FILLED, s.status)
    }

    @Test fun invalidQuantitiesAndEmptyIdentifiers_areRejected() {
        val badIds = lifecycle.apply(initial(), event(" ", OrderEventType.SUBMIT_SENT))
        assertFalse(badIds.accepted)
        val badOrder = lifecycle.apply(initial(), OrderEvent("e1", "wrong", OrderEventType.SUBMIT_SENT, 1L))
        assertFalse(badOrder.accepted)
        val badQuantity = lifecycle.apply(OrderState("o", "c", q("0")), OrderEvent("e2", "o", OrderEventType.SUBMIT_SENT, 2L))
        assertFalse(badQuantity.accepted)
    }

    @Test fun overfillAndMalformedFills_areRejectedWithoutMutation() {
        val s = apply(initial("5"), event("e1", OrderEventType.SUBMIT_SENT))
        for (qty in listOf("0", "-1")) {
            val bad = lifecycle.apply(s, event("e2$qty", OrderEventType.FILL_RECEIVED, "f$qty", qty))
            assertFalse(bad.accepted); assertEquals(s, bad.state)
        }
        val over = lifecycle.apply(s, event("e3", OrderEventType.FILL_RECEIVED, "f3", "6"))
        assertFalse(over.accepted); assertEquals(s, over.state)
    }

    @Test fun reconciliationRejectsDecreasingAndInconsistentSnapshots() {
        var s = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        s = apply(s, event("e2", OrderEventType.FILL_RECEIVED, "f1", "3"))
        assertFalse(lifecycle.apply(s, event("e3", OrderEventType.RECONCILIATION_RESULT, status = OrderStatus.PARTIALLY_FILLED, cumulative = "2")).accepted)
        assertFalse(lifecycle.apply(s, event("e4", OrderEventType.RECONCILIATION_RESULT, status = OrderStatus.REJECTED, cumulative = "3")).accepted)
        assertFalse(lifecycle.apply(s, event("e5", OrderEventType.RECONCILIATION_RESULT, status = OrderStatus.FILLED, cumulative = "11")).accepted)
    }

    @Test fun canceledAndExpiredSnapshotsPreservePartialFill() {
        var s = apply(initial(), event("e1", OrderEventType.SUBMIT_SENT))
        s = apply(s, event("e2", OrderEventType.RECONCILIATION_RESULT, status = OrderStatus.CANCELED, cumulative = "2"))
        assertEquals(OrderStatus.CANCELED, s.status)
        assertEquals(0, s.cumulativeFilledQuantity.compareTo(q("2")))
    }

    @Test fun decimalQuantitiesAreExact_andCumulativeDeltaMustMatch() {
        var s = apply(initial("1"), event("e1", OrderEventType.SUBMIT_SENT))
        s = apply(s, event("e2", OrderEventType.FILL_RECEIVED, "f1", "0.1"))
        s = apply(s, event("e3", OrderEventType.FILL_RECEIVED, "f2", "0.2"))
        assertEquals(0, s.cumulativeFilledQuantity.compareTo(q("0.3")))

        // A monotonic exchange snapshot may legitimately reveal an additional fill.
        s = apply(s, event("e4", OrderEventType.RECONCILIATION_RESULT,
            status = OrderStatus.PARTIALLY_FILLED, cumulative = "0.4"))
        assertEquals(0, s.cumulativeFilledQuantity.compareTo(q("0.4")))

        // After reconciliation, event quantity must equal the cumulative delta.
        val badDelta = lifecycle.apply(s, event("e5", OrderEventType.FILL_RECEIVED,
            "f3", "0.2", cumulative = "0.5"))
        assertFalse(badDelta.accepted)
        assertEquals(0, badDelta.state.cumulativeFilledQuantity.compareTo(q("0.4")))
    }
}
