package com.example.trading.orders

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class ReadOnlyMarketDiscoveryTest {
    private fun candles(
        start: Long = 0L,
        count: Int = 30,
        rising: Boolean = true,
        volumeBoost: Boolean = true
    ): List<MarketCandle> = (0 until count).map { index ->
        val price = if (rising) 100.0 + index * 0.15 else 100.0 - index * 0.15
        val volume = if (volumeBoost && index >= count - 5) "200" else "100"
        MarketCandle(
            start + index * 300_000L,
            start + index * 300_000L + 299_999L,
            (price - 0.05).toString(),
            (price + 0.1).toString(),
            (price - 0.1).toString(),
            price.toString(),
            volume
        )
    }

    private class FakeSource(var result: List<MarketCandle>) : ReadOnlyMarketDataSource {
        var calls = 0
        var fail = false
        override fun fetchClosedCandles(symbol: String, interval: String, limit: Int): List<MarketCandle> {
            calls++
            if (fail) throw IOException("offline")
            return result
        }
    }

    @Test fun risingVolumeConfirmedCandlesProduceLongAssessment() {
        val result = ReadOnlyOpportunityAnalyzer.assess(candles())
        assertEquals(OpportunityDirection.LONG, result.direction)
        assertTrue(result.score >= 7)
    }

    @Test fun fallingVolumeConfirmedCandlesProduceShortAssessment() {
        val result = ReadOnlyOpportunityAnalyzer.assess(candles(rising = false))
        assertEquals(OpportunityDirection.SHORT, result.direction)
    }

    @Test fun insufficientHistoryNeverProducesCandidate() {
        assertNull(ReadOnlyOpportunityAnalyzer.assess(candles(count = 10)).direction)
    }

    @Test fun scannerRejectsStaleOrOpenCandles() {
        val now = 30L * 300_000L + 300_000L
        val good = candles()
        ReadOnlyContinuousMarketScanner.validateFreshClosedCandles(good, now, "5m")
        assertThrows(IllegalArgumentException::class.java) {
            ReadOnlyContinuousMarketScanner.validateFreshClosedCandles(good, good.last().closeTimeMillis, "5m")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReadOnlyContinuousMarketScanner.validateFreshClosedCandles(good, now + 10_000_000L, "5m")
        }
    }

    @Test fun scannerEmitsStableIdAndNeverTurnsNetworkFailureIntoCandidate() {
        val source = FakeSource(candles())
        val scheduler = java.util.concurrent.ScheduledThreadPoolExecutor(1)
        val scanner = ReadOnlyContinuousMarketScanner(
            source, listOf("BTC/USDT"), pollEveryMillis = 60_000L,
            nowMillis = { 30L * 300_000L + 300_000L }, scheduler = scheduler
        )
        val emitted = mutableListOf<TradeOpportunity>()
        scanner.start(emitted::add)
        scanner.scanOnce()
        assertEquals(1, emitted.size)
        assertTrue(emitted.single().opportunityId.contains("BTC/USDT:5m:"))
        source.fail = true
        scanner.scanOnce()
        assertEquals(1, emitted.size)
        scanner.shutdown()
    }

    @Test fun freshHunterRequiresIndependentCurrentConfirmation() {
        val source = FakeSource(candles())
        val now = 30L * 300_000L + 300_000L
        val hunter = FreshDataHunterReviewGateway(source, { now })
        val opportunity = TradeOpportunity("candidate", "BTC/USDT", OpportunityDirection.LONG, 8, now - 1_000L, now + 600_000L)
        val accepted = hunter.reviewLong(opportunity)
        assertTrue(accepted.acceptedForFurtherAnalysis)
        assertEquals(1, source.calls)
        assertFalse(hunter.reviewShort(opportunity).acceptedForFurtherAnalysis)
    }

    @Test fun freshHunterRejectsExpiredOpportunityAndUnavailableFeed() {
        val source = FakeSource(candles())
        val now = 30L * 300_000L + 300_000L
        val hunter = FreshDataHunterReviewGateway(source, { now })
        val expired = TradeOpportunity("old", "BTC/USDT", OpportunityDirection.LONG, 8, 0L, now)
        assertEquals("OPPORTUNITY_EXPIRED", hunter.reviewLong(expired).reason)
        source.fail = true
        val fresh = expired.copy(opportunityId = "new", expiresAtMillis = now + 60_000L)
        assertFalse(hunter.reviewLong(fresh).acceptedForFurtherAnalysis)
    }
}
