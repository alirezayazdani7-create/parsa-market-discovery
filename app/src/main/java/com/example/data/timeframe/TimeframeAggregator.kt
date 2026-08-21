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
}
