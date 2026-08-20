package com.example.data.integrity

import com.example.data.AppDatabase
import com.example.data.entity.DataIntegrityAnomalyEntity
import com.example.data.entity.HistoricalCandleEntity

class DataIntegrityEngine(private val db: AppDatabase) {

    /**
     * Verifies candle sequence integrity for a given dataset.
     * Identifies Impossible Prices, Timestamp Inversions, Duplicate Timestamps,
     * Missing Data Gaps, and Insufficient History without modifying or fabricating data.
     */
    suspend fun auditCandleStream(
        symbol: String,
        timeframe: String,
        candles: List<HistoricalCandleEntity>,
        expectedIntervalMs: Long
    ): List<DataIntegrityAnomalyEntity> {
        val anomalies = mutableListOf<DataIntegrityAnomalyEntity>()

        if (candles.isEmpty()) {
            val anomaly = DataIntegrityAnomalyEntity(
                symbol = symbol,
                timeframe = timeframe,
                anomalyType = "INSUFFICIENT_HISTORY",
                severity = "MEDIUM",
                targetTimestamp = System.currentTimeMillis(),
                details = "No historical candle records found for symbol $symbol in timeframe $timeframe."
            )
            anomalies.add(anomaly)
            db.dataIntegrityAnomalyDao().insertAnomaly(anomaly)
            return anomalies
        }

        var previousTime: Long? = null

        for (i in candles.indices) {
            val c = candles[i]

            // 1. Impossible Price Check
            if (c.openPrice <= 0.0 || c.closePrice <= 0.0 || c.highPrice <= 0.0 || c.lowPrice <= 0.0) {
                anomalies.add(
                    DataIntegrityAnomalyEntity(
                        symbol = symbol,
                        timeframe = timeframe,
                        anomalyType = "IMPOSSIBLE_PRICE",
                        severity = "CRITICAL",
                        targetTimestamp = c.openTime,
                        details = "Zero or negative price detected: O=${c.openPrice}, H=${c.highPrice}, L=${c.lowPrice}, C=${c.closePrice}"
                    )
                )
            } else if (c.highPrice < c.lowPrice || c.highPrice < c.openPrice || c.highPrice < c.closePrice ||
                c.lowPrice > c.openPrice || c.lowPrice > c.closePrice
            ) {
                anomalies.add(
                    DataIntegrityAnomalyEntity(
                        symbol = symbol,
                        timeframe = timeframe,
                        anomalyType = "IMPOSSIBLE_PRICE",
                        severity = "CRITICAL",
                        targetTimestamp = c.openTime,
                        details = "Invalid OHLC relationship: High must be >= all and Low <= all."
                    )
                )
            }

            // 2. Chronological Order and Gaps
            if (previousTime != null) {
                if (c.openTime <= previousTime) {
                    val type = if (c.openTime == previousTime) "DUPLICATE_DATA" else "OUT_OF_ORDER"
                    anomalies.add(
                        DataIntegrityAnomalyEntity(
                            symbol = symbol,
                            timeframe = timeframe,
                            anomalyType = type,
                            severity = "HIGH",
                            targetTimestamp = c.openTime,
                            details = "Sequence anomaly at timestamp ${c.openTime}, previous was $previousTime"
                        )
                    )
                } else if (expectedIntervalMs > 0 && (c.openTime - previousTime) > (expectedIntervalMs * 1.5)) {
                    val missingCount = (c.openTime - previousTime) / expectedIntervalMs - 1
                    anomalies.add(
                        DataIntegrityAnomalyEntity(
                            symbol = symbol,
                            timeframe = timeframe,
                            anomalyType = "ABNORMAL_GAP",
                            severity = "MEDIUM",
                            targetTimestamp = previousTime,
                            details = "Gap of $missingCount expected intervals between $previousTime and ${c.openTime}"
                        )
                    )
                }
            }

            previousTime = c.openTime
        }

        if (anomalies.isNotEmpty()) {
            db.dataIntegrityAnomalyDao().insertAnomalies(anomalies)
        }

        return anomalies
    }

    suspend fun getAllAnomalies(): List<DataIntegrityAnomalyEntity> =
        db.dataIntegrityAnomalyDao().getAnomaliesList()
}
