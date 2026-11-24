package com.example.trading.orders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpportunityQueueTest {
    private fun opportunity(
        id: String,
        symbol: String = "BTCUSDT",
        direction: OpportunityDirection = OpportunityDirection.LONG,
        score: Int = 8,
        detected: Long = 100L,
        expires: Long = 1_000L
    ) = TradeOpportunity(id, symbol, direction, score, detected, expires)

    @Test fun candidateFoundDuringOpenOrderWaitsUntilThatOrderCloses() {
        val queue = OpportunityQueue()
        queue.onOrderOpened("order-1", "BTCUSDT")
        val offered = queue.offer(opportunity("opp-1"), 200L)
        assertEquals(OpportunityStatus.WAITING_FOR_ORDER_CLOSE, offered.record?.status)
        assertNull(queue.claimNext(OpportunityDirection.LONG, 300L))

        val released = queue.onOrderClosed("order-1", "BTCUSDT", 400L)
        assertEquals(listOf("opp-1"), released.map { it.opportunityId })
        assertEquals("opp-1", queue.claimNext(OpportunityDirection.LONG, 401L)?.opportunityId)
        assertEquals(OpportunityStatus.CLAIMED, queue.get("opp-1")?.status)
    }

    @Test fun stillWaitsWhileAnotherOrderForSameSymbolRemainsOpen() {
        val queue = OpportunityQueue()
        queue.onOrderOpened("order-1", "BTCUSDT")
        queue.onOrderOpened("order-2", "btcusdt")
        queue.offer(opportunity("opp-1"), 200L)

        assertTrue(queue.onOrderClosed("order-1", "BTCUSDT", 300L).isEmpty())
        assertEquals(OpportunityStatus.WAITING_FOR_ORDER_CLOSE, queue.get("opp-1")?.status)
        assertEquals(1, queue.onOrderClosed("order-2", "BTCUSDT", 400L).size)
    }

    @Test fun unrelatedSymbolCanBeClaimedImmediately() {
        val queue = OpportunityQueue()
        queue.onOrderOpened("order-1", "BTCUSDT")
        val result = queue.offer(opportunity("eth-1", symbol = "ETHUSDT"), 200L)
        assertEquals(OpportunityStatus.READY_FOR_HUNTER, result.record?.status)
        assertEquals("eth-1", queue.claimNext(OpportunityDirection.LONG, 201L)?.opportunityId)
    }

    @Test fun expiredWhileWaitingIsNotReleasedAfterClose() {
        val queue = OpportunityQueue()
        queue.onOrderOpened("order-1", "BTCUSDT")
        queue.offer(opportunity("old", expires = 500L), 200L)
        assertTrue(queue.onOrderClosed("order-1", "BTCUSDT", 500L).isEmpty())
        assertEquals(OpportunityStatus.EXPIRED, queue.get("old")?.status)
    }

    @Test fun readyOpportunityExpiresBeforeClaim() {
        val queue = OpportunityQueue()
        queue.offer(opportunity("old", expires = 500L), 200L)
        assertNull(queue.claimNext(OpportunityDirection.LONG, 500L))
        assertEquals(OpportunityStatus.EXPIRED, queue.get("old")?.status)
    }

    @Test fun longAndShortQueuesAreClaimedIndependently() {
        val queue = OpportunityQueue()
        queue.offer(opportunity("long"), 200L)
        queue.offer(opportunity("short", direction = OpportunityDirection.SHORT), 200L)
        assertEquals("long", queue.claimNext(OpportunityDirection.LONG, 201L)?.opportunityId)
        assertEquals("short", queue.claimNext(OpportunityDirection.SHORT, 201L)?.opportunityId)
    }

    @Test fun highestScoreThenNewestCandidateWins() {
        val queue = OpportunityQueue()
        queue.offer(opportunity("a", score = 7, detected = 300L), 350L)
        queue.offer(opportunity("b", score = 9, detected = 200L), 350L)
        queue.offer(opportunity("c", score = 9, detected = 250L), 350L)
        assertEquals("c", queue.claimNext(OpportunityDirection.LONG, 400L)?.opportunityId)
    }

    @Test fun identicalOpportunityReplayIsIdempotent() {
        val queue = OpportunityQueue()
        val value = opportunity("same")
        assertFalse(queue.offer(value, 200L).duplicate)
        assertTrue(queue.offer(value, 250L).duplicate)
        assertEquals(1, queue.snapshot().size)
    }

    @Test fun reusedOpportunityIdWithDifferentPayloadIsRejected() {
        val queue = OpportunityQueue()
        queue.offer(opportunity("same"), 200L)
        val result = queue.offer(opportunity("same", score = 9), 200L)
        assertFalse(result.accepted)
        assertNotNull(result.error)
        assertEquals(8, queue.get("same")?.opportunity?.score)
    }

    @Test fun claimedOpportunityCannotBeClaimedAgain() {
        val queue = OpportunityQueue()
        queue.offer(opportunity("once"), 200L)
        assertNotNull(queue.claimNext(OpportunityDirection.LONG, 201L))
        assertNull(queue.claimNext(OpportunityDirection.LONG, 202L))
    }

    @Test fun invalidOpportunityIsRejected() {
        val queue = OpportunityQueue()
        assertFalse(queue.offer(opportunity("bad", score = 11), 200L).accepted)
        assertFalse(queue.offer(opportunity("bad-expiry", detected = 500L, expires = 500L), 200L).accepted)
        assertTrue(queue.snapshot().isEmpty())
    }

    @Test fun unknownOrAlreadyClosedOrderDoesNotReleaseCandidateTwice() {
        val queue = OpportunityQueue()
        queue.onOrderOpened("order-1", "BTCUSDT")
        queue.offer(opportunity("once"), 200L)
        assertEquals(1, queue.onOrderClosed("order-1", "BTCUSDT", 300L).size)
        assertTrue(queue.onOrderClosed("order-1", "BTCUSDT", 301L).isEmpty())
    }
}
