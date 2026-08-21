package com.example.data.learning

import com.example.data.entity.HistoricalCandleEntity
import kotlin.math.abs
import kotlin.math.sqrt

data class BtcRegimeContext(
    val btcTrend: String, // "BULLISH", "BEARISH", "SIDEWAYS"
    val btcVolatility: String, // "HIGH", "NORMAL", "LOW"
    val btcMomentum: String, // "POSITIVE", "NEGATIVE", "FLAT"
    val btcVolumeRegime: String, // "EXPANSION", "NORMAL", "CONTRACTION"
    val btcReturnPct: Double,
    val correlationWithTarget: Double,
    val isTargetMovementBtcDriven: Boolean
)

object BtcMarketRegimeEngine {

    fun analyzeRegime(
        btcCandles: List<HistoricalCandleEntity>,
        targetCandles: List<HistoricalCandleEntity>? = null,
        asOfTime: Long
    ): BtcRegimeContext {
        val filteredBtc = btcCandles.filter { it.openTime <= asOfTime }.sortedBy { it.openTime }
        if (filteredBtc.size < 3) {
            return BtcRegimeContext(
                btcTrend = "UNKNOWN",
                btcVolatility = "NORMAL",
                btcMomentum = "FLAT",
                btcVolumeRegime = "NORMAL",
                btcReturnPct = 0.0,
                correlationWithTarget = 1.0,
                isTargetMovementBtcDriven = true
            )
        }

        val lastBtc = filteredBtc.last()
        val firstBtc = filteredBtc.first()
        val btcReturn = if (firstBtc.closePrice > 0) ((lastBtc.closePrice - firstBtc.closePrice) / firstBtc.closePrice) * 100.0 else 0.0

        val btcTrend = when {
            btcReturn > 2.0 -> "BULLISH"
            btcReturn < -2.0 -> "BEARISH"
            else -> "SIDEWAYS"
        }

        val avgVolume = filteredBtc.map { it.volume }.average()
        val btcVolumeRegime = when {
            lastBtc.volume > avgVolume * 1.3 -> "EXPANSION"
            lastBtc.volume < avgVolume * 0.7 -> "CONTRACTION"
            else -> "NORMAL"
        }

        val priceRangePct = (lastBtc.highPrice - lastBtc.lowPrice) / (if (lastBtc.closePrice > 0) lastBtc.closePrice else 1.0) * 100.0
        val btcVolatility = if (priceRangePct > 4.0) "HIGH" else if (priceRangePct < 1.0) "LOW" else "NORMAL"
        val btcMomentum = if (btcReturn > 0.5) "POSITIVE" else if (btcReturn < -0.5) "NEGATIVE" else "FLAT"

        var correlation = 1.0
        var isBtcDriven = true

        if (targetCandles != null && targetCandles.isNotEmpty()) {
            val filteredTarget = targetCandles.filter { it.openTime <= asOfTime }.sortedBy { it.openTime }
            if (filteredTarget.size >= 3) {
                correlation = calculateCorrelation(filteredBtc, filteredTarget)
                val targetReturn = if (filteredTarget.first().closePrice > 0)
                    ((filteredTarget.last().closePrice - filteredTarget.first().closePrice) / filteredTarget.first().closePrice) * 100.0
                else 0.0

                isBtcDriven = correlation > 0.6 && (abs(btcReturn) > 1.0) && (btcReturn * targetReturn > 0)
            }
        }

        return BtcRegimeContext(
            btcTrend = btcTrend,
            btcVolatility = btcVolatility,
            btcMomentum = btcMomentum,
            btcVolumeRegime = btcVolumeRegime,
            btcReturnPct = btcReturn,
            correlationWithTarget = correlation,
            isTargetMovementBtcDriven = isBtcDriven
        )
    }

    private fun calculateCorrelation(seriesA: List<HistoricalCandleEntity>, seriesB: List<HistoricalCandleEntity>): Double {
        val minSize = minOf(seriesA.size, seriesB.size)
        if (minSize < 2) return 1.0

        val returnsA = mutableListOf<Double>()
        val returnsB = mutableListOf<Double>()

        for (i in 1 until minSize) {
            val prevA = seriesA[i - 1].closePrice
            val currA = seriesA[i].closePrice
            val prevB = seriesB[i - 1].closePrice
            val currB = seriesB[i].closePrice

            if (prevA > 0 && prevB > 0) {
                returnsA.add((currA - prevA) / prevA)
                returnsB.add((currB - prevB) / prevB)
            }
        }

        if (returnsA.size < 2) return 1.0

        val meanA = returnsA.average()
        val meanB = returnsB.average()

        var num = 0.0
        var denA = 0.0
        var denB = 0.0

        for (i in returnsA.indices) {
            val diffA = returnsA[i] - meanA
            val diffB = returnsB[i] - meanB
            num += diffA * diffB
            denA += diffA * diffA
            denB += diffB * diffB
        }

        val den = sqrt(denA * denB)
        if (den == 0.0) return 1.0
        return (num / den).coerceIn(-1.0, 1.0)
    }
}
