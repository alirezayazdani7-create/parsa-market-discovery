package com.example.trading.orders

import org.junit.Assert.*
import org.junit.Test

class ContinuousOpportunityHandoffTest {
    private class FakeScanner : ContinuousOpportunityScanner {
        var starts = 0
        var stops = 0
        private var callback: ((TradeOpportunity) -> Unit)? = null
        override fun start(onOpportunity: (TradeOpportunity) -> Unit) {
            starts++
            callback = onOpportunity
        }
        override fun stop() {
            stops++
            callback = null
        }
        fun emit(value: TradeOpportunity) {
            checkNotNull(callback) { "scanner is not running" }.invoke(value)
        }
    }

    private class FakeHunters : HunterReviewGateway {
        val longReviews = mutableListOf<TradeOpportunity>()
        val shortReviews = mutableListOf<TradeOpportunity>()
        override fun reviewLong(opportunity: TradeOpportunity): HunterReviewResult {
            longReviews += opportunity
            return HunterReviewResult(opportunity.opportunityId, opportunity.direction, true, "fresh review passed")
        }
        override fun reviewShort(opportunity: TradeOpportunity): HunterReviewResult {
            shortReviews += opportunity
            return HunterReviewResult(opportunity.opportunityId, opportunity.direction, false, "fresh review rejected")
        }
    }

    private fun candidate(
        id: String,
        symbol: String = "BTC/USDT",
        direction: OpportunityDirection = OpportunityDirection.LONG,
        expires: Long = 10_000L
    ) = TradeOpportunity(id, symbol, direction, 8, 100L, expires)

    @Test fun scannerKeepsEmittingWhileSameSymbolOrderIsOpenAndHandoffWaitsForClose() {
        val scanner = FakeScanner()
        val hunters = FakeHunters()
        val handoffs = mutableListOf<HandoffResult>()
        val coordinator = ContinuousOpportunityHandoff(scanner, hunters, nowMillis = { 200L }, onHandoff = handoffs::add)

        coordinator.start()
        coordinator.onOrderOpened("order-1", "BTC/USDT")
        scanner.emit(candidate("opp-1"))
        scanner.emit(candidate("opp-2", direction = OpportunityDirection.SHORT))

        assertTrue(coordinator.isRunning())
        assertEquals(0, handoffs.size)
        assertEquals(2, coordinator.snapshot().size)

        coordinator.onOrderClosed("order-1", "BTC/USDT", 300L)

        assertEquals(1, hunters.longReviews.size)
        assertEquals(1, hunters.shortReviews.size)
        assertEquals(2, handoffs.size)
        assertTrue(handoffs.all { it.review.opportunityId == it.opportunity.opportunityId })
    }

    @Test fun finalOpenOrderMustCloseBeforeAnyHunterReceivesCandidate() {
        val scanner = FakeScanner()
        val hunters = FakeHunters()
        val coordinator = ContinuousOpportunityHandoff(scanner, hunters, nowMillis = { 200L })
        coordinator.start()
        coordinator.onOrderOpened("one", "ETH/USDT")
        coordinator.onOrderOpened("two", "ETH/USDT")
        scanner.emit(candidate("eth-opp", "ETH/USDT"))

        coordinator.onOrderClosed("one", "ETH/USDT", 300L)
        assertTrue(hunters.longReviews.isEmpty())

        coordinator.onOrderClosed("two", "ETH/USDT", 400L)
        assertEquals(listOf("eth-opp"), hunters.longReviews.map { it.opportunityId })
    }

    @Test fun unrelatedSymbolIsHandedOffImmediately() {
        val scanner = FakeScanner()
        val hunters = FakeHunters()
        val coordinator = ContinuousOpportunityHandoff(scanner, hunters, nowMillis = { 200L })
        coordinator.start()
        coordinator.onOrderOpened("btc-order", "BTC/USDT")
        scanner.emit(candidate("sol-opp", "SOL/USDT"))

        assertEquals(listOf("sol-opp"), hunters.longReviews.map { it.opportunityId })
        assertEquals(OpportunityStatus.WAITING_FOR_ORDER_CLOSE,
            coordinator.snapshot().first { it.opportunity.opportunityId == "sol-opp" }.status.let { null }
                ?: OpportunityStatus.CLAIMED)
    }

    @Test fun expiredCandidateIsNotSentToHunterAfterOrderClose() {
        val scanner = FakeScanner()
        val hunters = FakeHunters()
        val coordinator = ContinuousOpportunityHandoff(scanner, hunters, nowMillis = { 200L })
        coordinator.start()
        coordinator.onOrderOpened("order-1", "BTC/USDT")
        scanner.emit(candidate("expired", expires = 250L))
        coordinator.onOrderClosed("order-1", "BTC/USDT", 250L)

        assertTrue(hunters.longReviews.isEmpty())
        assertEquals(OpportunityStatus.EXPIRED, coordinator.snapshot().single().status)
    }

    @Test fun startAndStopAreIdempotent() {
        val scanner = FakeScanner()
        val coordinator = ContinuousOpportunityHandoff(scanner, FakeHunters())
        coordinator.start()
        coordinator.start()
        assertEquals(1, scanner.starts)
        coordinator.stop()
        coordinator.stop()
        assertEquals(1, scanner.stops)
        assertFalse(coordinator.isRunning())
    }

    @Test fun hunterExceptionIsReportedAsReviewFailureAndNotRetried() {
        val scanner = FakeScanner()
        val brokenHunters = object : HunterReviewGateway {
            override fun reviewLong(opportunity: TradeOpportunity): HunterReviewResult =
                error("market data unavailable")
            override fun reviewShort(opportunity: TradeOpportunity): HunterReviewResult =
                error("market data unavailable")
        }
        val handoffs = mutableListOf<HandoffResult>()
        val coordinator = ContinuousOpportunityHandoff(scanner, brokenHunters, nowMillis = { 200L }, onHandoff = handoffs::add)
        coordinator.start()
        scanner.emit(candidate("failure"))

        assertEquals(1, handoffs.size)
        assertFalse(handoffs.single().review.acceptedForFurtherAnalysis)
        assertTrue(handoffs.single().review.reason!!.contains("market data unavailable"))
        assertTrue(coordinator.snapshot().single().status == OpportunityStatus.CLAIMED)
    }
}
