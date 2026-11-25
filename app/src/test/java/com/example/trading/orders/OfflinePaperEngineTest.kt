package com.example.trading.orders

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePaperEngineTest {
    private fun d(value: String) = BigDecimal(value)
    private fun signal(
        id: String = "sig-1",
        symbol: String = "BTCUSDT",
        side: PaperSide = PaperSide.LONG,
        score: Int = 9,
        entry: String = "100",
        quantity: String = "2",
        leverage: Int = 5,
        created: Long = 100L,
        expiry: Long = 500L
    ) = PaperSignal(id, symbol, side, score, created, expiry, d(entry), d(quantity), leverage)

    private fun assertDecimal(expected: String, actual: BigDecimal) =
        assertEquals(0, d(expected).compareTo(actual))

    @Test fun longTrade_reconcilesGrossFeesFundingMarginAndNet() {
        val engine = OfflinePaperEngine()
        val opened = engine.submit(signal(), nowMillis = 150L)
        assertEquals(PaperEntryStatus.ACCEPTED, opened.status)
        assertDecimal("200", opened.position!!.entryNotional)
        assertDecimal("0.018", opened.position.entryFee)
        assertDecimal("40", opened.position.requiredMargin)

        val closed = engine.close("sig-1", d("110"), 200L, d("0.5"))
        assertEquals(PaperCloseStatus.CLOSED, closed.status)
        val trade = closed.trade!!
        assertDecimal("20", trade.rawPricePnl)
        assertDecimal("20", trade.grossPnlAfterExecution)
        assertDecimal("0", trade.executionCost)
        assertDecimal("0.0198", trade.exitFee)
        assertDecimal("0.0378", trade.totalFees)
        assertDecimal("0.5", trade.fundingCost)
        assertDecimal("19.4622", trade.netPnl)
        assertDecimal("48.6555", trade.returnOnMarginPct)
        assertEquals(0, engine.openPositionCount())
        assertEquals(1, engine.closedTradeCount())
    }

    @Test fun shortTrade_usesCorrectDirectionAndFeeBasis() {
        val engine = OfflinePaperEngine()
        assertEquals(PaperEntryStatus.ACCEPTED, engine.submit(signal(side = PaperSide.SHORT, quantity = "3"), 150L).status)
        val trade = engine.close("sig-1", d("90"), 200L).trade!!
        assertDecimal("30", trade.rawPricePnl)
        assertDecimal("0.027", trade.entryFee)
        assertDecimal("0.0243", trade.exitFee)
        assertDecimal("29.9487", trade.netPnl)
    }

    @Test fun adverseSpreadAndSlippage_areIncludedBeforeFees() {
        val engine = OfflinePaperEngine(PaperEngineConfig(
            entryFeeRate = d("0.00009"),
            exitFeeRate = d("0.00009"),
            slippageBpsPerFill = d("10"),
            spreadBps = d("4")
        ))
        val position = engine.submit(signal(), 150L).position!!
        assertDecimal("100.12", position.entryFillPrice)
        val trade = engine.close("sig-1", d("110"), 200L, d("0.5")).trade!!
        assertDecimal("109.868", trade.exitFillPrice)
        assertDecimal("20", trade.rawPricePnl)
        assertDecimal("19.496", trade.grossPnlAfterExecution)
        assertDecimal("0.504", trade.executionCost)
        assertDecimal("0.03779784", trade.totalFees)
        assertDecimal("18.95820216", trade.netPnl)
        assertDecimal("20", trade.rawPricePnl.subtract(trade.executionCost))
        assertDecimal("19.496", trade.grossPnlAfterExecution)
    }

    @Test fun duplicateSignalIsIdempotentAndCannotOpenSecondPosition() {
        val engine = OfflinePaperEngine()
        assertEquals(PaperEntryStatus.ACCEPTED, engine.submit(signal(), 150L).status)
        assertEquals(PaperEntryStatus.DUPLICATE, engine.submit(signal(), 150L).status)
        assertEquals(1, engine.openPositionCount())
        assertEquals("SYMBOL_ALREADY_OPEN", engine.submit(signal(id = "sig-2"), 150L).reason)
        assertEquals(1, engine.openPositionCount())
    }

    @Test fun duplicateCloseReturnsSameTradeAndConflictingReplayIsRejected() {
        val engine = OfflinePaperEngine()
        engine.submit(signal(), 150L)
        val first = engine.close("sig-1", d("110"), 200L, d("0.1"))
        val replay = engine.close("sig-1", d("110"), 200L, d("0.1"))
        val conflict = engine.close("sig-1", d("111"), 200L, d("0.1"))
        assertEquals(PaperCloseStatus.CLOSED, first.status)
        assertEquals(PaperCloseStatus.DUPLICATE, replay.status)
        assertEquals(first.trade, replay.trade)
        assertEquals(PaperCloseStatus.CONFLICT, conflict.status)
        assertEquals(1, engine.closedTradeCount())
    }

    @Test fun staleLowScoreAndInvalidLeverageAreRejectedWithoutPosition() {
        val engine = OfflinePaperEngine()
        assertEquals("SCORE_BELOW_GATE", engine.submit(signal(score = 6), 150L).reason)
        assertEquals("STALE_OR_INVALID_SIGNAL", engine.submit(signal(created = 10, expiry = 90), 150L).reason)
        assertEquals("INVALID_TRADE_PARAMETERS", engine.submit(signal(id = "bad-lev", leverage = 101), 150L).reason)
        assertEquals(0, engine.openPositionCount())
    }

    @Test fun cannotCloseUnknownPositionOrBeforeEntryTime() {
        val engine = OfflinePaperEngine()
        assertEquals(PaperCloseStatus.REJECTED, engine.close("missing", d("110"), 200L).status)
        engine.submit(signal(), 150L)
        assertEquals("INVALID_CLOSE_TIME", engine.close("sig-1", d("110"), 149L).reason)
        assertEquals(1, engine.openPositionCount())
    }

    @Test fun fundingCreditIsRepresentedAsNegativeCost() {
        val engine = OfflinePaperEngine()
        engine.submit(signal(quantity = "1"), 150L)
        val trade = engine.close("sig-1", d("101"), 200L, d("-0.25")).trade!!
        assertDecimal("-0.25", trade.fundingCost)
        assertDecimal("1.23191", trade.netPnl)
    }
}
