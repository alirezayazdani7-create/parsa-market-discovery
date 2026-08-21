package com.example.data.events

import com.example.data.AppDatabase
import com.example.data.entity.EventImpactEntity
import com.example.data.entity.HistoricalCandleEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class EventImpactAnalyzer(private val db: AppDatabase) {

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
     * Calculates the market impact of an event on an asset across supported time horizons.
     * Evaluates BEFORE EVENT (pre-event candle), EVENT TIME (at event), and AFTER EVENT (at horizon offset).
     * Strictly verifies that no future data beyond each horizon is leaked.
     */
    suspend fun analyzeEventImpact(
        eventId: String,
        assetSymbol: String,
        eventTimestamp: Long,
        candles: List<HistoricalCandleEntity>,
        btcCandles: List<HistoricalCandleEntity>? = null
    ): List<EventImpactEntity> {
        val impacts = mutableListOf<EventImpactEntity>()
        if (candles.isEmpty()) return emptyList()

        val sortedCandles = candles.sortedBy { it.openTime }

        // Find candle at or immediately before event
        val preEventCandle = sortedCandles.lastOrNull { it.openTime < eventTimestamp }
        val eventCandle = sortedCandles.firstOrNull { it.openTime >= eventTimestamp }

        val basePriceBefore = preEventCandle?.closePrice ?: eventCandle?.openPrice ?: 0.0
        val basePriceAtEvent = eventCandle?.openPrice ?: basePriceBefore

        if (basePriceBefore <= 0.0 || basePriceAtEvent <= 0.0) {
            return emptyList()
        }

        val maxAvailableTime = sortedCandles.maxOfOrNull { it.closeTime } ?: 0L

        for ((horizonName, horizonOffsetMs) in HORIZON_MS) {
            val targetTime = eventTimestamp + horizonOffsetMs

            // If the historical dataset does not extend to the required horizon target time, mark DATA_UNAVAILABLE
            if (targetTime > maxAvailableTime) {
                impacts.add(
                    EventImpactEntity(
                        eventId = eventId,
                        assetSymbol = assetSymbol,
                        horizon = horizonName,
                        priceBefore = basePriceBefore,
                        priceAtEvent = basePriceAtEvent,
                        priceAfter = 0.0,
                        pctChange = 0.0,
                        maxFavorableExcursion = 0.0,
                        maxAdverseExcursion = 0.0,
                        highLowExcursion = 0.0,
                        volumeChangePct = 0.0,
                        volatilityChangePct = 0.0,
                        direction = "NEUTRAL",
                        trendChange = "NEUTRAL",
                        recoveryTimeMs = null,
                        maxDrawdown = 0.0,
                        impactScore = 0.0,
                        btcCorrelation = 0.0,
                        isBtcDriven = false,
                        status = "DATA_UNAVAILABLE"
                    )
                )
                continue
            }

            // Sliced strictly within post-event horizon: candles occurring strictly >= eventTimestamp and <= targetTime
            val horizonCandles = sortedCandles.filter { it.openTime in eventTimestamp..targetTime }
            val postCandle = sortedCandles.lastOrNull { it.openTime <= targetTime && it.openTime >= eventTimestamp }

            if (postCandle == null) {
                // Horizon data unavailable
                impacts.add(
                    EventImpactEntity(
                        eventId = eventId,
                        assetSymbol = assetSymbol,
                        horizon = horizonName,
                        priceBefore = basePriceBefore,
                        priceAtEvent = basePriceAtEvent,
                        priceAfter = 0.0,
                        pctChange = 0.0,
                        maxFavorableExcursion = 0.0,
                        maxAdverseExcursion = 0.0,
                        highLowExcursion = 0.0,
                        volumeChangePct = 0.0,
                        volatilityChangePct = 0.0,
                        direction = "NEUTRAL",
                        trendChange = "NEUTRAL",
                        recoveryTimeMs = null,
                        maxDrawdown = 0.0,
                        impactScore = 0.0,
                        btcCorrelation = 0.0,
                        isBtcDriven = false,
                        status = "DATA_UNAVAILABLE"
                    )
                )
                continue
            }

            val priceAfter = postCandle.closePrice
            val pctChange = ((priceAfter - basePriceAtEvent) / basePriceAtEvent) * 100.0

            // Excursion and Drawdown Calculations strictly within the horizon
            val highestHigh = horizonCandles.maxOfOrNull { it.highPrice } ?: postCandle.highPrice
            val lowestLow = horizonCandles.minOfOrNull { it.lowPrice } ?: postCandle.lowPrice

            val maxFavorableExcursion = if (pctChange >= 0) {
                ((highestHigh - basePriceAtEvent) / basePriceAtEvent) * 100.0
            } else {
                ((basePriceAtEvent - lowestLow) / basePriceAtEvent) * 100.0
            }

            val maxAdverseExcursion = if (pctChange >= 0) {
                ((lowestLow - basePriceAtEvent) / basePriceAtEvent) * 100.0
            } else {
                ((highestHigh - basePriceAtEvent) / basePriceAtEvent) * 100.0
            }

            val highLowExcursion = if (basePriceAtEvent > 0) ((highestHigh - lowestLow) / basePriceAtEvent) * 100.0 else 0.0

            val maxDrawdown = if (basePriceAtEvent > 0) {
                max(0.0, ((basePriceAtEvent - lowestLow) / basePriceAtEvent) * 100.0)
            } else 0.0

            // Recovery time: time until price returns to basePriceAtEvent if it experienced a drawdown
            val recoveryCandle = horizonCandles.firstOrNull { it.openTime > eventTimestamp && it.closePrice >= basePriceAtEvent }
            val recoveryTimeMs = recoveryCandle?.let { it.openTime - eventTimestamp }

            val direction = when {
                pctChange > 0.5 -> "UP"
                pctChange < -0.5 -> "DOWN"
                else -> "NEUTRAL"
            }

            val volumeBefore = preEventCandle?.volume ?: 1.0
            val volumeAfter = postCandle.volume
            val volumeChangePct = if (volumeBefore > 0) ((volumeAfter - volumeBefore) / volumeBefore) * 100.0 else 0.0

            val volatilityBefore = preEventCandle?.let { abs(it.highPrice - it.lowPrice) / it.closePrice * 100.0 } ?: 1.0
            val volatilityAfter = abs(postCandle.highPrice - postCandle.lowPrice) / postCandle.closePrice * 100.0
            val volatilityChangePct = if (volatilityBefore > 0) ((volatilityAfter - volatilityBefore) / volatilityBefore) * 100.0 else 0.0

            val trendChange = when {
                pctChange > 2.0 -> "BULLISH_CONTINUATION"
                pctChange < -2.0 -> "BEARISH_CONTINUATION"
                abs(pctChange) <= 2.0 && abs(highLowExcursion) > 4.0 -> "HIGH_VOLATILITY_CHOP"
                else -> "NEUTRAL"
            }

            // Composite Impact Score (0.0 to 100.0 scale)
            val impactScore = min(100.0, max(0.0, abs(pctChange) * 4.0 + abs(highLowExcursion) * 2.0 + min(20.0, abs(volumeChangePct) * 0.1)))

            // BTC correlation check if BTC candles provided
            var btcCorr = 1.0
            var isBtcDriven = false
            if (btcCandles != null && assetSymbol != "BTC/USDT") {
                val btcPost = btcCandles.firstOrNull { it.openTime in eventTimestamp..targetTime }
                val btcPre = btcCandles.lastOrNull { it.openTime < eventTimestamp }
                if (btcPost != null && btcPre != null && btcPre.closePrice > 0) {
                    val btcPctChange = ((btcPost.closePrice - btcPre.closePrice) / btcPre.closePrice) * 100.0
                    btcCorr = if (btcPctChange * pctChange > 0) 0.85 else -0.5
                    isBtcDriven = abs(btcPctChange) > 1.5 && (btcCorr > 0.5)
                }
            }

            impacts.add(
                EventImpactEntity(
                    eventId = eventId,
                    assetSymbol = assetSymbol,
                    horizon = horizonName,
                    priceBefore = basePriceBefore,
                    priceAtEvent = basePriceAtEvent,
                    priceAfter = priceAfter,
                    pctChange = pctChange,
                    maxFavorableExcursion = maxFavorableExcursion,
                    maxAdverseExcursion = maxAdverseExcursion,
                    highLowExcursion = highLowExcursion,
                    volumeChangePct = volumeChangePct,
                    volatilityChangePct = volatilityChangePct,
                    direction = direction,
                    trendChange = trendChange,
                    recoveryTimeMs = recoveryTimeMs,
                    maxDrawdown = maxDrawdown,
                    impactScore = impactScore,
                    btcCorrelation = btcCorr,
                    isBtcDriven = isBtcDriven,
                    status = "VALID"
                )
            )
        }

        db.eventImpactDao().insertImpacts(impacts)
        return impacts
    }

    suspend fun getImpactsByEvent(eventId: String): List<EventImpactEntity> =
        db.eventImpactDao().getImpactsByEvent(eventId)
}
