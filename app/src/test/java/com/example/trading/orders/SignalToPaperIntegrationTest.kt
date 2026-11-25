package com.example.trading.orders

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deterministic analyzer -> PaperSignal -> accounting close; no HTTP or live execution. */
class SignalToPaperIntegrationTest {
    private fun d(value: String) = BigDecimal(value)

    @Test fun analyzerCandidateFeedsPaperPositionAndReconcilesNetAfterCosts() {
        val candles = (0 until 20).map { i ->
            val close = when {
                i < 15 -> 100.0 + i * 0.01
                else -> 100.15 + (i - 15) * 0.10
            }
            MarketCandle(
                openTimeMillis = i * 300_000L,
                closeTimeMillis = (i + 1) * 300_000L - 1L,
                open = close.toString(),
                high = (close + 0.05).toString(),
                low = (close - 0.05).toString(),
                close = close.toString(),
                volume = if (i >= 15) "15" else "10"
            )
        }

        val assessment = ReadOnlyOpportunityAnalyzer.assess(candles)
        assertEquals(OpportunityDirection.LONG, assessment.direction)
        assertTrue("Analyzer score must satisfy Paper entry gate", assessment.score >= 7)

        val engine = OfflinePaperEngine(PaperEngineConfig(
            entryFeeRate = d("0.00009"),
            exitFeeRate = d("0.00009"),
            slippageBpsPerFill = d("5"),
            spreadBps = d("4"),
            minimumScore = 7
        ))
        val signal = PaperSignal(
            signalId = "fixture-${assessment.direction}-${candles.last().closeTimeMillis}",
            symbol = "BTCUSDT",
            side = if (assessment.direction == OpportunityDirection.LONG) PaperSide.LONG else PaperSide.SHORT,
            score = assessment.score,
            generatedAtMillis = candles.last().closeTimeMillis,
            expiresAtMillis = candles.last().closeTimeMillis + 600_000L,
            referenceEntryPrice = d(candles.last().close),
            quantity = d("2"),
            leverage = 5
        )
        val entry = engine.submit(signal, nowMillis = signal.generatedAtMillis + 1L)
        assertEquals(PaperEntryStatus.ACCEPTED, entry.status)
        assertNotNull(entry.position)
        assertEquals(1, engine.openPositionCount())

        val close = engine.close(signal.signalId, d("101"), signal.generatedAtMillis + 60_000L, d("0.05"))
        assertEquals(PaperCloseStatus.CLOSED, close.status)
        val trade = close.trade!!
        assertEquals(0, trade.totalFees.compareTo(trade.entryFee.add(trade.exitFee)))
        assertEquals(0, trade.netPnl.compareTo(
            trade.grossPnlAfterExecution.subtract(trade.totalFees).subtract(trade.fundingCost)
        ))
        assertEquals(0, engine.openPositionCount())
        assertEquals(1, engine.closedTradeCount())

        val replay = engine.close(signal.signalId, d("101"), signal.generatedAtMillis + 60_000L, d("0.05"))
        assertEquals(PaperCloseStatus.DUPLICATE, replay.status)
        assertEquals(trade, replay.trade)
    }
}
