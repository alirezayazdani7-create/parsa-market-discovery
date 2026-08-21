package com.example.data.timeframe

import com.example.data.entity.HistoricalCandleEntity

object TimeframeAggregator {

    private val TIMEFRAME_MS_MAP = mapOf(
        "1m" to 60_000L,
        "3m" to 180_000L,
        "5m" to 300_000L,
        "15m" to 900_000L,
        "30m" to 1_800_000L,
        "1h" to 3_600_000L,
        "4h" to 14_400_000L,
        "1d" to 86_400_000L
    )

    fun getTimeframeIntervalMs(timeframe: String): Long? = TIMEFRAME_MS_MAP[timeframe]

    /**
     * Aggregates fine-grained genuine candles into a higher timeframe target.
     * Never interpolates or generates fake candles.
     */
    fun aggregateCandles(
        sourceCandles: List<HistoricalCandleEntity>,
        targetTimeframe: String
    ): List<HistoricalCandleEntity> {
        val intervalMs = getTimeframeIntervalMs(targetTimeframe)
            ?: throw IllegalArgumentException("Unsupported target timeframe: $targetTimeframe")

        if (sourceCandles.isEmpty()) return emptyList()

        val sorted = sourceCandles.sortedBy { it.openTime }
        val grouped = sorted.groupBy { candle ->
            (candle.openTime / intervalMs) * intervalMs
        }

        val aggregated = mutableListOf<HistoricalCandleEntity>()

        for ((bucketStart, bucketCandles) in grouped) {
            if (bucketCandles.isEmpty()) continue

            val open = bucketCandles.first().openPrice
            val close = bucketCandles.last().closePrice
            val high = bucketCandles.maxOf { it.highPrice }
            val low = bucketCandles.minOf { it.lowPrice }
            val volume = bucketCandles.sumOf { it.volume }
            val quoteVolume = bucketCandles.sumOf { it.quoteVolume }
            val tradesCount = bucketCandles.sumOf { it.tradesCount }
            val bucketEnd = bucketStart + intervalMs - 1

            aggregated.add(
                HistoricalCandleEntity(
                    symbol = bucketCandles.first().symbol,
                    timeframe = targetTimeframe,
                    openTime = bucketStart,
                    closeTime = bucketEnd,
                    openPrice = open,
                    highPrice = high,
                    lowPrice = low,
                    closePrice = close,
                    volume = volume,
                    quoteVolume = quoteVolume,
                    tradesCount = tradesCount,
                    isClosed = true,
                    integrityChecked = true,
                    source = "AGGREGATED_FROM_${bucketCandles.first().timeframe.uppercase()}"
                )
            )
        }

        return aggregated.sortedBy { it.openTime }
    }

    /**
     * Aligns historical candles across multiple timeframes strictly as of [asOfTime].
     * Higher-timeframe candles are ONLY returned if they were completely closed at or before [asOfTime]
     * (i.e. candle.closeTime <= asOfTime).
     * Guarantees zero future leakage from higher timeframe uncompleted candles.
     */
    fun alignClosedMultiTimeframe(
        source1mCandles: List<HistoricalCandleEntity>,
        timeframes: List<String> = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1d"),
        asOfTime: Long
    ): Map<String, HistoricalCandleEntity?> {
        val valid1m = source1mCandles.filter { it.openTime <= asOfTime && it.closeTime <= asOfTime }
        val result = mutableMapOf<String, HistoricalCandleEntity?>()

        for (tf in timeframes) {
            if (tf == "1m") {
                result[tf] = valid1m.maxByOrNull { it.openTime }
            } else {
                val intervalMs = getTimeframeIntervalMs(tf) ?: continue
                val aggregated = aggregateCandles(valid1m, tf)
                // Strictly require closeTime <= asOfTime for completed closed higher-timeframe candles
                val closedOnly = aggregated.filter { it.closeTime <= asOfTime }
                result[tf] = closedOnly.maxByOrNull { it.openTime }
            }
        }
        return result
    }
}
