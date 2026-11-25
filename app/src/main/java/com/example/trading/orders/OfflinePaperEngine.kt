package com.example.trading.orders

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Deterministic in-memory Paper Trading harness.
 *
 * Deliberately has no network, exchange, credential, wallet, or order-execution dependency.
 * It is an offline accounting gate; it does not establish strategy profitability.
 */
enum class PaperSide { LONG, SHORT }

data class PaperSignal(
    val signalId: String,
    val symbol: String,
    val side: PaperSide,
    val score: Int,
    val generatedAtMillis: Long,
    val expiresAtMillis: Long,
    val referenceEntryPrice: BigDecimal,
    val quantity: BigDecimal,
    val leverage: Int
)

data class PaperEngineConfig(
    /** 0.009% = 0.00009 as a decimal rate. */
    val entryFeeRate: BigDecimal = BigDecimal("0.00009"),
    val exitFeeRate: BigDecimal = BigDecimal("0.00009"),
    val slippageBpsPerFill: BigDecimal = BigDecimal.ZERO,
    /** Full quoted spread in bps; half is charged adversely on each fill. */
    val spreadBps: BigDecimal = BigDecimal.ZERO,
    val minimumScore: Int = 7
)

enum class PaperEntryStatus { ACCEPTED, DUPLICATE, REJECTED, CONFLICT }

data class PaperPosition(
    val positionId: String,
    val symbol: String,
    val side: PaperSide,
    val quantity: BigDecimal,
    val leverage: Int,
    val referenceEntryPrice: BigDecimal,
    val entryFillPrice: BigDecimal,
    val entryNotional: BigDecimal,
    val entryFee: BigDecimal,
    val requiredMargin: BigDecimal,
    val openedAtMillis: Long
)

data class PaperEntryResult(
    val status: PaperEntryStatus,
    val position: PaperPosition? = null,
    val reason: String? = null
)

data class PaperClosedTrade(
    val positionId: String,
    val symbol: String,
    val side: PaperSide,
    val quantity: BigDecimal,
    val referenceEntryPrice: BigDecimal,
    val entryFillPrice: BigDecimal,
    val referenceExitPrice: BigDecimal,
    val exitFillPrice: BigDecimal,
    val rawPricePnl: BigDecimal,
    /** PnL after adverse entry/exit spread and slippage, before fees/funding. */
    val grossPnlAfterExecution: BigDecimal,
    val executionCost: BigDecimal,
    val entryFee: BigDecimal,
    val exitFee: BigDecimal,
    val totalFees: BigDecimal,
    val fundingCost: BigDecimal,
    val netPnl: BigDecimal,
    val requiredMargin: BigDecimal,
    val returnOnMarginPct: BigDecimal,
    val closedAtMillis: Long
)

enum class PaperCloseStatus { CLOSED, DUPLICATE, REJECTED, CONFLICT }

data class PaperCloseResult(
    val status: PaperCloseStatus,
    val trade: PaperClosedTrade? = null,
    val reason: String? = null
)

class OfflinePaperEngine(private val config: PaperEngineConfig = PaperEngineConfig()) {
    private val openPositions = linkedMapOf<String, PaperPosition>()
    private val openSymbolToPosition = linkedMapOf<String, String>()
    private val acceptedSignalFingerprints = linkedMapOf<String, String>()
    private val closedTrades = linkedMapOf<String, PaperClosedTrade>()
    private val closeFingerprints = linkedMapOf<String, String>()

    init {
        require(config.entryFeeRate >= BigDecimal.ZERO && config.exitFeeRate >= BigDecimal.ZERO)
        require(config.slippageBpsPerFill >= BigDecimal.ZERO && config.spreadBps >= BigDecimal.ZERO)
        require(config.minimumScore in 1..10)
    }

    @Synchronized
    fun submit(signal: PaperSignal, nowMillis: Long): PaperEntryResult {
        if (signal.signalId.isBlank() || signal.symbol.isBlank()) return rejected("EMPTY_ID")
        if (signal.score !in 1..10 || signal.score < config.minimumScore) return rejected("SCORE_BELOW_GATE")
        if (signal.generatedAtMillis > nowMillis || signal.expiresAtMillis < nowMillis ||
            signal.expiresAtMillis < signal.generatedAtMillis) return rejected("STALE_OR_INVALID_SIGNAL")
        if (signal.referenceEntryPrice <= BigDecimal.ZERO || signal.quantity <= BigDecimal.ZERO ||
            signal.leverage !in 1..100) return rejected("INVALID_TRADE_PARAMETERS")

        val fingerprint = signalFingerprint(signal)
        val prior = acceptedSignalFingerprints[signal.signalId]
        if (prior != null) {
            return if (prior == fingerprint) {
                PaperEntryResult(PaperEntryStatus.DUPLICATE, openPositions[signal.signalId] ?: closedTrades[signal.signalId]?.let { null }, "IDEMPOTENT_SIGNAL")
            } else PaperEntryResult(PaperEntryStatus.CONFLICT, reason = "SIGNAL_ID_PAYLOAD_CONFLICT")
        }
        if (openSymbolToPosition.containsKey(signal.symbol)) return rejected("SYMBOL_ALREADY_OPEN")

        val adverseBps = config.slippageBpsPerFill.add(config.spreadBps.divide(TWO, MC))
        val signedBps = if (signal.side == PaperSide.LONG) adverseBps else adverseBps.negate()
        val entryFill = signal.referenceEntryPrice.multiply(ONE.add(signedBps.divide(BPS, MC)), MC).money()
        if (entryFill <= BigDecimal.ZERO) return rejected("NON_POSITIVE_ENTRY_FILL")
        val entryNotional = entryFill.multiply(signal.quantity, MC).money()
        val entryFee = entryNotional.multiply(config.entryFeeRate, MC).money()
        val margin = entryNotional.divide(BigDecimal(signal.leverage), MC).money()
        val position = PaperPosition(
            positionId = signal.signalId,
            symbol = signal.symbol,
            side = signal.side,
            quantity = signal.quantity.money(),
            leverage = signal.leverage,
            referenceEntryPrice = signal.referenceEntryPrice.money(),
            entryFillPrice = entryFill,
            entryNotional = entryNotional,
            entryFee = entryFee,
            requiredMargin = margin,
            openedAtMillis = nowMillis
        )
        acceptedSignalFingerprints[signal.signalId] = fingerprint
        openPositions[position.positionId] = position
        openSymbolToPosition[position.symbol] = position.positionId
        return PaperEntryResult(PaperEntryStatus.ACCEPTED, position)
    }

    @Synchronized
    fun close(positionId: String, referenceExitPrice: BigDecimal, closedAtMillis: Long,
              fundingCost: BigDecimal = BigDecimal.ZERO): PaperCloseResult {
        if (referenceExitPrice <= BigDecimal.ZERO || fundingCost == BigDecimal.ZERO.negate().subtract(BigDecimal("1E100"))) {
            return closeRejected("INVALID_EXIT_OR_FUNDING")
        }
        val fingerprint = listOf(referenceExitPrice.money(), closedAtMillis, fundingCost.money()).joinToString("|")
        val prior = closedTrades[positionId]
        if (prior != null) {
            return if (closeFingerprints[positionId] == fingerprint) {
                PaperCloseResult(PaperCloseStatus.DUPLICATE, prior, "IDEMPOTENT_CLOSE")
            } else PaperCloseResult(PaperCloseStatus.CONFLICT, reason = "CLOSE_PAYLOAD_CONFLICT")
        }
        val position = openPositions[positionId] ?: return closeRejected("POSITION_NOT_OPEN")
        if (closedAtMillis < position.openedAtMillis || fundingCost.scale() > 1000) return closeRejected("INVALID_CLOSE_TIME_OR_COST")

        val adverseBps = config.slippageBpsPerFill.add(config.spreadBps.divide(TWO, MC))
        val signedExitBps = if (position.side == PaperSide.LONG) adverseBps.negate() else adverseBps
        val exitFill = referenceExitPrice.multiply(ONE.add(signedExitBps.divide(BPS, MC)), MC).money()
        if (exitFill <= BigDecimal.ZERO) return closeRejected("NON_POSITIVE_EXIT_FILL")
        val rawPnl = pnl(position.side, position.referenceEntryPrice, referenceExitPrice, position.quantity)
        val grossAfterExecution = pnl(position.side, position.entryFillPrice, exitFill, position.quantity)
        val executionCost = rawPnl.subtract(grossAfterExecution).money()
        val exitNotional = exitFill.multiply(position.quantity, MC).money()
        val exitFee = exitNotional.multiply(config.exitFeeRate, MC).money()
        val totalFees = position.entryFee.add(exitFee).money()
        val net = grossAfterExecution.subtract(totalFees).subtract(fundingCost).money()
        val marginReturnPct = net.divide(position.requiredMargin, MC).multiply(HUNDRED, MC).money()
        val trade = PaperClosedTrade(
            positionId, position.symbol, position.side, position.quantity,
            position.referenceEntryPrice, position.entryFillPrice,
            referenceExitPrice.money(), exitFill, rawPnl, grossAfterExecution,
            executionCost, position.entryFee, exitFee, totalFees, fundingCost.money(),
            net, position.requiredMargin, marginReturnPct, closedAtMillis
        )
        openPositions.remove(positionId)
        openSymbolToPosition.remove(position.symbol)
        closedTrades[positionId] = trade
        closeFingerprints[positionId] = fingerprint
        return PaperCloseResult(PaperCloseStatus.CLOSED, trade)
    }

    @Synchronized fun openPositionCount(): Int = openPositions.size
    @Synchronized fun closedTradeCount(): Int = closedTrades.size

    private fun signalFingerprint(s: PaperSignal): String = listOf(
        s.symbol, s.side.name, s.score, s.generatedAtMillis, s.expiresAtMillis,
        s.referenceEntryPrice.money(), s.quantity.money(), s.leverage
    ).joinToString("|")

    private fun pnl(side: PaperSide, entry: BigDecimal, exit: BigDecimal, quantity: BigDecimal): BigDecimal =
        (if (side == PaperSide.LONG) exit.subtract(entry) else entry.subtract(exit))
            .multiply(quantity, MC).money()

    private fun rejected(reason: String) = PaperEntryResult(PaperEntryStatus.REJECTED, reason = reason)
    private fun closeRejected(reason: String) = PaperCloseResult(PaperCloseStatus.REJECTED, reason = reason)

    private fun BigDecimal.money(): BigDecimal = setScale(12, RoundingMode.HALF_UP).stripTrailingZeros().let {
        if (it.scale() < 0) it.setScale(0) else it
    }

    companion object {
        private val ONE = BigDecimal.ONE
        private val TWO = BigDecimal("2")
        private val BPS = BigDecimal("10000")
        private val HUNDRED = BigDecimal("100")
        private val MC = MathContext(24, RoundingMode.HALF_UP)
    }
}
