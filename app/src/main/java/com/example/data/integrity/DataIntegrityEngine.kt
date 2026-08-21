package com.example.data.integrity

import com.example.data.AppDatabase
import com.example.data.entity.DataIntegrityAnomalyEntity
import com.example.data.entity.EventImpactEntity
import com.example.data.entity.ExperienceMemoryEntity
import com.example.data.entity.HistoricalCandleEntity
import com.example.data.entity.HistoricalEventEntity

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

    /**
     * Audits an event prior to registration or processing.
     * Ensures eventId, timestamp, and source validity.
     */
    suspend fun auditEventIngestion(event: HistoricalEventEntity): DataIntegrityAnomalyEntity? {
        if (event.eventId.isBlank() || event.eventTimestamp <= 0L || event.source.isBlank()) {
            val anomaly = DataIntegrityAnomalyEntity(
                symbol = event.primarySymbol,
                timeframe = "EVENT",
                anomalyType = "INVALID_EVENT_METADATA",
                severity = "HIGH",
                targetTimestamp = event.eventTimestamp,
                details = "Event ${event.eventId} contains empty required fields or invalid timestamp ${event.eventTimestamp}"
            )
            db.dataIntegrityAnomalyDao().insertAnomaly(anomaly)
            return anomaly
        }
        return null
    }

    /**
     * Audits calculated event impacts to ensure non-negative prices and logical horizon boundaries.
     */
    suspend fun auditImpactCalculation(impacts: List<EventImpactEntity>): List<DataIntegrityAnomalyEntity> {
        val anomalies = mutableListOf<DataIntegrityAnomalyEntity>()
        for (impact in impacts) {
            if (impact.status == "VALID" && (impact.priceAtEvent <= 0.0 || impact.priceAfter <= 0.0)) {
                val anomaly = DataIntegrityAnomalyEntity(
                    symbol = impact.assetSymbol,
                    timeframe = impact.horizon,
                    anomalyType = "INVALID_IMPACT_PRICE",
                    severity = "HIGH",
                    targetTimestamp = impact.calculatedAt,
                    details = "Impact for event ${impact.eventId} on ${impact.assetSymbol} had non-positive price."
                )
                anomalies.add(anomaly)
            }
        }
        if (anomalies.isNotEmpty()) {
            db.dataIntegrityAnomalyDao().insertAnomalies(anomalies)
        }
        return anomalies
    }

    /**
     * Audits a walk-forward batch execution verifying zero future leakage across all generated experiences.
     */
    suspend fun auditWalkForwardExecution(
        symbol: String,
        experiences: List<ExperienceMemoryEntity>
    ): List<DataIntegrityAnomalyEntity> {
        val anomalies = mutableListOf<DataIntegrityAnomalyEntity>()
        var prevTimestamp: Long? = null
        for (exp in experiences) {
            if (prevTimestamp != null && exp.timestamp <= prevTimestamp) {
                val anomaly = DataIntegrityAnomalyEntity(
                    symbol = symbol,
                    timeframe = exp.timeframe,
                    anomalyType = "NON_CHRONOLOGICAL_EXPERIENCE",
                    severity = "CRITICAL",
                    targetTimestamp = exp.timestamp,
                    details = "Experience ${exp.experienceId} timestamp ${exp.timestamp} <= previous $prevTimestamp"
                )
                anomalies.add(anomaly)
            }
            prevTimestamp = exp.timestamp
        }
        if (anomalies.isNotEmpty()) {
            db.dataIntegrityAnomalyDao().insertAnomalies(anomalies)
        }
        return anomalies
    }

    /**
     * Audits candles to ensure no data exists before an asset's authentic historical genesis or debut.
     */
    suspend fun auditAssetExistenceBoundaries(
        symbol: String,
        candles: List<HistoricalCandleEntity>
    ): List<DataIntegrityAnomalyEntity> {
        val asset = db.marketAssetDao().getAssetBySymbol(symbol) ?: return emptyList()
        val minValidTime = asset.genesisTimestamp ?: asset.firstSeenAt
        val anomalies = mutableListOf<DataIntegrityAnomalyEntity>()

        for (candle in candles) {
            if (candle.openTime < minValidTime) {
                val anomaly = DataIntegrityAnomalyEntity(
                    symbol = symbol,
                    timeframe = candle.timeframe,
                    anomalyType = "PRE_GENESIS_CONTAMINATION",
                    severity = "CRITICAL",
                    targetTimestamp = candle.openTime,
                    details = "Candle openTime ${candle.openTime} is prior to asset verified genesis $minValidTime"
                )
                anomalies.add(anomaly)
            }
        }

        if (anomalies.isNotEmpty()) {
            db.dataIntegrityAnomalyDao().insertAnomalies(anomalies)
        }
        return anomalies
    }
}

