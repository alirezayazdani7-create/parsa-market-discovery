package com.example.data.events

import com.example.data.AppDatabase
import com.example.data.entity.HistoricalCandleEntity
import com.example.data.entity.HistoricalEventEntity
import com.example.data.entity.HistoricalSetupEntity
import com.example.data.indicators.HistoricalIndicatorEngine
import java.util.UUID
import kotlin.math.abs

class EventConditionAnalyzer(private val db: AppDatabase) {

    private val HORIZON_MS = mapOf(
        "1m" to 60_000L,
        "5m" to 300_000L,
        "15m" to 900_000L,
        "30m" to 1_800_000L,
        "1h" to 3_600_000L,
        "4h" to 14_400_000L,
        "24h" to 86_400_000L
    )

    /**
     * Evaluates a historical setup combining Event + Technical Conditions at the event timestamp.
     * Zero Future Leakage Guarantee:
     * - Market state, trend, indicators, market structure, and prediction are generated ONLY from [pastCandles] (openTime <= eventTimestamp).
     * - [futureCandles] are used strictly to evaluate actual future outcome afterward.
     */
    suspend fun analyzeSetup(
        event: HistoricalEventEntity,
        assetSymbol: String,
        pastCandles: List<HistoricalCandleEntity>,
        futureCandles: List<HistoricalCandleEntity>? = null,
        horizon: String = "1h"
    ): HistoricalSetupEntity {
        val eventTime = event.eventTimestamp
        val filteredPast = pastCandles.filter { it.openTime <= eventTime }.sortedBy { it.openTime }
        require(filteredPast.isNotEmpty()) { "Cannot evaluate market condition without historical candles prior to event" }

        val closes = filteredPast.map { it.closePrice }
        val highs = filteredPast.map { it.highPrice }
        val lows = filteredPast.map { it.lowPrice }
        val volumes = filteredPast.map { it.volume }

        val sma20 = HistoricalIndicatorEngine.calculateSMA(closes, 20)
        val ema20 = HistoricalIndicatorEngine.calculateEMA(closes, 20)
        val rsi14 = HistoricalIndicatorEngine.calculateRSI(closes, 14) ?: 50.0
        val (support, resistance) = HistoricalIndicatorEngine.calculateSupportResistance(highs, lows, 20)
        val (volSma, rvol) = HistoricalIndicatorEngine.calculateVolumeMetrics(volumes, 20)
        val marketStructure = HistoricalIndicatorEngine.calculateMarketStructure(highs, lows, 10)
        val trend = HistoricalIndicatorEngine.calculateTrendState(closes, sma20, ema20, rsi14)
        val volatilityPct = HistoricalIndicatorEngine.calculateVolatility(closes, 20) ?: 1.0

        val volumeState = when {
            (rvol ?: 1.0) >= 1.5 -> "HIGH_VOLUME_SURGE"
            (rvol ?: 1.0) <= 0.6 -> "LOW_VOLUME_COMPRESSION"
            else -> "NORMAL_VOLUME"
        }

        val volatilityState = when {
            volatilityPct > 3.5 -> "EXPANDED_VOLATILITY"
            volatilityPct < 1.0 -> "SQUEEZED_VOLATILITY"
            else -> "MODERATE_VOLATILITY"
        }

        val marketRegime = when {
            trend.contains("UPTREND") && marketStructure == "BULLISH_STRUCTURE" -> "BULLISH_EXPANSION"
            trend.contains("DOWNTREND") && marketStructure == "BEARISH_STRUCTURE" -> "BEARISH_TREND"
            volatilityState == "SQUEEZED_VOLATILITY" -> "RANGE_ACCUMULATION"
            else -> "HIGH_VOLATILITY_CHOP"
        }

        // Deterministic Historical Prediction based purely on pre-event technicals + event category
        val historicalPrediction = when {
            event.eventType in listOf("ETF_DECISION", "HALVING", "NETWORK_LAUNCH") && trend.contains("UPTREND") -> "STRONG_BULLISH_CONTINUATION"
            event.eventType in listOf("BANKRUPTCY", "PROTOCOL_FAIL", "MARKET_CRASH") -> "STRONG_BEARISH_CASCADE"
            event.category == "REGULATORY" && marketRegime == "BULLISH_EXPANSION" -> "INITIAL_DIP_THEN_RECOVERY"
            marketStructure == "BULLISH_STRUCTURE" && volumeState == "HIGH_VOLUME_SURGE" -> "BULLISH_BREAKOUT"
            marketStructure == "BEARISH_STRUCTURE" -> "BEARISH_CONTINUATION"
            else -> "NEUTRAL_CONSOLIDATION"
        }

        val indicatorStatesJson = """{"rsi14":$rsi14,"sma20":$sma20,"ema20":$ema20,"volatilityPct":$volatilityPct,"rvol":$rvol,"support":$support,"resistance":$resistance}"""
        val eventCharacteristicsJson = """{"category":"${event.category}","severity":"${event.severity}","source":"${event.source}","confidence":${event.confidence}}"""

        // Evaluate actual future outcome if future candles provided
        val horizonOffset = HORIZON_MS[horizon] ?: 3_600_000L
        val targetTime = eventTime + horizonOffset
        val horizonFutureCandles = futureCandles?.filter { it.openTime in (eventTime + 1)..targetTime }?.sortedBy { it.openTime }

        var actualOutcome: String? = null
        var predictionError: Double? = null

        if (horizonFutureCandles != null && horizonFutureCandles.isNotEmpty()) {
            val basePrice = closes.last()
            val outcomePrice = horizonFutureCandles.last().closePrice
            val actualPctChange = ((outcomePrice - basePrice) / basePrice) * 100.0

            actualOutcome = when {
                actualPctChange > 2.0 -> "BULLISH_EXPANSION"
                actualPctChange < -2.0 -> "BEARISH_CASCADE"
                actualPctChange > 0.5 -> "MILD_UPWARD"
                actualPctChange < -0.5 -> "MILD_DOWNWARD"
                else -> "SIDEWAYS_CONSOLIDATION"
            }

            val predictedBullish = historicalPrediction.contains("BULLISH")
            val actualBullish = actualPctChange > 0.5
            val predictedBearish = historicalPrediction.contains("BEARISH")
            val actualBearish = actualPctChange < -0.5

            predictionError = when {
                (predictedBullish && actualBullish) || (predictedBearish && actualBearish) -> 0.0
                (predictedBullish && actualBearish) || (predictedBearish && actualBullish) -> 1.0 // Directional Miss
                else -> 0.5 // Magnitude/Timing Miss
            }
        }

        val setup = HistoricalSetupEntity(
            setupId = UUID.randomUUID().toString(),
            eventId = event.eventId,
            assetSymbol = assetSymbol,
            timestamp = eventTime,
            marketRegime = marketRegime,
            trend = trend,
            indicatorStatesJson = indicatorStatesJson,
            volumeState = volumeState,
            volatilityState = volatilityState,
            marketStructure = marketStructure,
            eventCharacteristicsJson = eventCharacteristicsJson,
            historicalPrediction = historicalPrediction,
            actualFutureOutcome = actualOutcome,
            predictionError = predictionError,
            confidence = event.confidence,
            evaluationHorizon = horizon
        )

        db.historicalSetupDao().insertSetup(setup)
        return setup
    }

    suspend fun getSetupsForEvent(eventId: String): List<HistoricalSetupEntity> =
        db.historicalSetupDao().getSetupsByEvent(eventId)

    suspend fun getSetupsForAsset(symbol: String): List<HistoricalSetupEntity> =
        db.historicalSetupDao().getSetupsByAsset(symbol)

    suspend fun answerHistoricalQuestion(condition: String, eventType: String): List<HistoricalSetupEntity> {
        val all = db.historicalSetupDao().getSetupsList(200)
        return all.filter {
            (it.marketRegime.contains(condition, ignoreCase = true) || it.trend.contains(condition, ignoreCase = true))
        }
    }
}
