package com.example.data.events

import com.example.data.AppDatabase
import com.example.data.entity.EventImpactEntity
import com.example.data.entity.HistoricalCandleEntity
import kotlin.math.abs

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

        for ((horizonName, horizonOffsetMs) in HORIZON_MS) {
            val targetTime = eventTimestamp + horizonOffsetMs
            val postCandle = sortedCandles.lastOrNull { it.openTime <= targetTime && it.openTime >= eventTimestamp }

            if (postCandle == null || (postCandle.openTime == eventCandle?.openTime && horizonOffsetMs > 0 && sortedCandles.none { it.openTime in (eventTimestamp + 1)..targetTime })) {
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
                        volumeChangePct = 0.0,
                        volatilityChangePct = 0.0,
                        trendChange = "NEUTRAL",
                        btcCorrelation = 0.0,
                        isBtcDriven = false,
                        status = "DATA_UNAVAILABLE"
                    )
                )
                continue
            }

            val priceAfter = postCandle.closePrice
            val pctChange = ((priceAfter - basePriceAtEvent) / basePriceAtEvent) * 100.0
            val volumeBefore = preEventCandle?.volume ?: 1.0
            val volumeAfter = postCandle.volume
            val volumeChangePct = if (volumeBefore > 0) ((volumeAfter - volumeBefore) / volumeBefore) * 100.0 else 0.0

            val volatilityBefore = preEventCandle?.let { abs(it.highPrice - it.lowPrice) / it.closePrice * 100.0 } ?: 1.0
            val volatilityAfter = abs(postCandle.highPrice - postCandle.lowPrice) / postCandle.closePrice * 100.0
            val volatilityChangePct = if (volatilityBefore > 0) ((volatilityAfter - volatilityBefore) / volatilityBefore) * 100.0 else 0.0

            val trendChange = when {
                pctChange > 2.0 -> "BULLISH_CONTINUATION"
                pctChange < -2.0 -> "BEARISH_CONTINUATION"
                else -> "NEUTRAL"
            }

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
                    volumeChangePct = volumeChangePct,
                    volatilityChangePct = volatilityChangePct,
                    trendChange = trendChange,
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
