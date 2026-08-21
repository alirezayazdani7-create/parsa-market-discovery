package com.example.data.provider

import com.example.data.entity.HistoricalCandleEntity

data class HistoricalDatasetMetadata(
    val symbol: String,
    val sourceTimeframe: String,
    val actualResolution: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val recordCount: Long,
    val isFundingRateAvailable: Boolean = false,
    val isOpenInterestAvailable: Boolean = false,
    val isLiquidationsAvailable: Boolean = false,
    val isOrderBookAvailable: Boolean = false,
    val qualityStatus: String = "AUTHENTICATED_REAL_DATA"
)

interface IHistoricalDataProvider {
    suspend fun fetchCandles(
        symbol: String,
        timeframe: String,
        fromTimestamp: Long,
        toTimestamp: Long
    ): List<HistoricalCandleEntity>

    suspend fun getDatasetMetadata(symbol: String, timeframe: String): HistoricalDatasetMetadata
}

class LocalArchiveHistoricalDataProvider : IHistoricalDataProvider {

    private val inMemoryArchives = mutableMapOf<String, List<HistoricalCandleEntity>>()

    fun registerArchive(symbol: String, timeframe: String, candles: List<HistoricalCandleEntity>) {
        val key = "${symbol}_$timeframe"
        inMemoryArchives[key] = candles.sortedBy { it.openTime }
    }

    override suspend fun fetchCandles(
        symbol: String,
        timeframe: String,
        fromTimestamp: Long,
        toTimestamp: Long
    ): List<HistoricalCandleEntity> {
        val key = "${symbol}_$timeframe"
        val candles = inMemoryArchives[key] ?: return emptyList()
        return candles.filter { it.openTime in fromTimestamp..toTimestamp }
    }

    override suspend fun getDatasetMetadata(symbol: String, timeframe: String): HistoricalDatasetMetadata {
        val key = "${symbol}_$timeframe"
        val candles = inMemoryArchives[key] ?: emptyList()

        return HistoricalDatasetMetadata(
            symbol = symbol,
            sourceTimeframe = timeframe,
            actualResolution = if (candles.isNotEmpty()) timeframe else "DATA_UNAVAILABLE",
            startTimestamp = candles.firstOrNull()?.openTime ?: 0L,
            endTimestamp = candles.lastOrNull()?.closeTime ?: 0L,
            recordCount = candles.size.toLong(),
            isFundingRateAvailable = false, // Marked explicitly as DATA_UNAVAILABLE unless provided
            isOpenInterestAvailable = false,
            isLiquidationsAvailable = false,
            isOrderBookAvailable = false,
            qualityStatus = if (candles.isNotEmpty()) "AUTHENTICATED_REAL_DATA" else "DATA_UNAVAILABLE"
        )
    }
}
