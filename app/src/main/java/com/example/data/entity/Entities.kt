package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,
    val role: String, // e.g. "AUDITOR", "SYSTEM", "DEVELOPER"
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

@Entity(tableName = "system_state")
data class SystemStateEntity(
    @PrimaryKey
    val stateKey: String,
    val value: String,
    val stage: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "experiments")
data class ExperimentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String,
    val status: String, // "QUEUED", "RUNNING", "COMPLETED", "NOT_IMPLEMENTED"
    val configJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

@Entity(tableName = "test_runs")
data class TestRunEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val suiteName: String,
    val status: String, // "PASSED", "FAILED", "RUNNING"
    val passedCount: Int,
    val failedCount: Int,
    val totalCount: Int,
    val startedAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val executedBy: String = "AUTOMATED_HARNESS"
)

@Entity(tableName = "test_results")
data class TestResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val runId: Long,
    val testName: String,
    val category: String, // "UNIT", "INTEGRATION", "VALIDATION", "FUTURE_STUB"
    val status: String, // "PASSED", "FAILED", "SKIPPED", "NOT_IMPLEMENTED"
    val errorMessage: String? = null,
    val executionTimeMs: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val level: String, // "INFO", "WARN", "ERROR", "SECURITY"
    val category: String, // "BUILD", "DATABASE", "TEST", "API", "SYSTEM"
    val message: String,
    val detailsJson: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "model_versions")
data class ModelVersionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val modelName: String,
    val versionTag: String,
    val architecture: String,
    val status: String, // "INITIALIZED", "NOT_IMPLEMENTED", "READY"
    val weightsHash: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "memory_versions")
data class MemoryVersionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val memoryKey: String,
    val version: Int,
    val schemaVersion: String,
    val recordCount: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "market_concepts")
data class MarketConceptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conceptCode: String,
    val title: String,
    val category: String, // "MARKET_STRUCTURE", "ORDER_BOOK", "LIQUIDITY", "VOLATILITY", "RISK_CONTROL"
    val description: String,
    val difficultyLevel: Int = 1, // 1: Beginner, 2: Intermediate, 3: Advanced
    val deterministicRulesJson: String,
    val isVerified: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "risk_rules")
data class RiskRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ruleCode: String,
    val name: String,
    val category: String, // "POSITION_SIZING", "DRAWDOWN_LIMIT", "LEVERAGE_CAP", "EXPOSURE"
    val formulaOrLogic: String,
    val maxAllowedRiskPct: Double,
    val isMandatory: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "education_progress")
data class EducationProgressEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val conceptCode: String,
    val isCompleted: Boolean = false,
    val scorePct: Double = 0.0,
    val lastEvaluatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "market_assets")
data class MarketAssetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val symbol: String, // e.g. "BTC/USDT"
    val name: String,
    val marketType: String = "SPOT", // "SPOT", "PERPETUAL", "FUTURES"
    val exchange: String = "PRIMARY_AGGREGATOR",
    val marketCapRank: Int = 1, // Supports 1 to 1200+
    val genesisTimestamp: Long? = null, // Real start date, never backfilled with fake data
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis(),
    val dataAvailabilityPct: Double = 100.0,
    val supportedTimeframes: String = "1m,5m,15m,30m,1h,4h,1d,1w",
    val status: String = "ACTIVE", // "ACTIVE", "DELISTED", "UNINITIALIZED", "DISCONTINUED"
    val schemaVersion: Int = 1,
    val sourceMetadataJson: String = "{}"
)

@Entity(
    tableName = "historical_candles",
    indices = [
        Index(value = ["symbol", "timeframe", "openTime"], unique = true)
    ]
)
data class HistoricalCandleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val symbol: String,
    val timeframe: String, // "1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w"
    val openTime: Long,
    val closeTime: Long,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val closePrice: Double,
    val volume: Double,
    val quoteVolume: Double = 0.0,
    val tradesCount: Long = 0,
    val isClosed: Boolean = true,
    val integrityChecked: Boolean = true,
    val source: String = "HISTORICAL_ARCHIVE"
)

@Entity(
    tableName = "experience_memories",
    indices = [
        Index(value = ["assetSymbol", "timeframe", "timestamp"])
    ]
)
data class ExperienceMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val experienceId: String,
    val assetSymbol: String,
    val timeframe: String,
    val timestamp: Long,
    val marketState: String, // "BULLISH_TREND", "BEARISH_TREND", "RANGE_BOUND", "HIGH_VOLATILITY", "ACCUMULATION", "DISTRIBUTION"
    val detectedPattern: String, // "BREAKOUT", "SUPPORT_BOUNCE", "RESISTANCE_REJECTION", "FALSE_BREAKOUT", "ORDERBOOK_ABSORPTION"
    val conceptCode: String,
    val ruleUsed: String,
    val expectedOutcome: String,
    val actualOutcome: String? = null,
    val errorMagnitude: Double? = null,
    val lessonLearned: String,
    val confidence: Double = 1.0,
    val isWalkForwardVerified: Boolean = true,
    val memoryVersion: Int = 1,
    val crossAssetCorrelatedCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cross_asset_insights")
data class CrossAssetInsightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val insightCode: String,
    val patternOrConcept: String,
    val primaryAsset: String,
    val correlatedAssetsJson: String,
    val sampleSize: Int,
    val statisticalConfidence: Double,
    val consistencyScore: Double,
    val findingsSummary: String,
    val evidenceHash: String,
    val isVerified: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "data_integrity_anomalies")
data class DataIntegrityAnomalyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val symbol: String,
    val timeframe: String,
    val anomalyType: String, // "MISSING_DATA", "DUPLICATE_DATA", "TIMESTAMP_ERROR", "OUT_OF_ORDER", "IMPOSSIBLE_PRICE", "ABNORMAL_GAP", "DELISTED_GAP", "INSUFFICIENT_HISTORY"
    val severity: String, // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    val targetTimestamp: Long,
    val details: String,
    val isResolved: Boolean = false,
    val detectedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "historical_events",
    indices = [
        Index(value = ["eventId"], unique = true),
        Index(value = ["eventTimestamp"])
    ]
)
data class HistoricalEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventId: String,
    val eventTimestamp: Long,
    val source: String,
    val title: String,
    val eventType: String, // "EXCHANGE_LISTING", "DELISTING", "HACK_EXPLOIT", "BANKRUPTCY", "PROTOCOL_UPGRADE", "ETF_DECISION", "CPI_RELEASE", "RATE_DECISION", "HALVING", "REGULATORY"
    val affectedAssetsJson: String,
    val sourceUrl: String = "",
    val confidence: Double = 1.0,
    val marketImpactStatus: String = "PENDING", // "ANALYZED", "DATA_UNAVAILABLE", "PENDING"
    val details: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "event_impacts",
    indices = [
        Index(value = ["eventId", "assetSymbol", "horizon"], unique = true)
    ]
)
data class EventImpactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventId: String,
    val assetSymbol: String,
    val horizon: String, // "1m", "5m", "15m", "30m", "1h", "4h", "24h"
    val priceBefore: Double,
    val priceAtEvent: Double,
    val priceAfter: Double,
    val pctChange: Double,
    val volumeChangePct: Double,
    val volatilityChangePct: Double,
    val trendChange: String, // "BULLISH_CONTINUATION", "BEARISH_CONTINUATION", "BULLISH_REVERSAL", "BEARISH_REVERSAL", "NEUTRAL"
    val btcCorrelation: Double,
    val isBtcDriven: Boolean,
    val calculatedAt: Long = System.currentTimeMillis(),
    val status: String = "VALID" // "VALID", "DATA_UNAVAILABLE"
)

@Entity(
    tableName = "indicator_snapshots",
    indices = [
        Index(value = ["symbol", "timeframe", "timestamp"], unique = true)
    ]
)
data class HistoricalIndicatorSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val symbol: String,
    val timeframe: String,
    val timestamp: Long,
    val sma20: Double? = null,
    val ema20: Double? = null,
    val wma20: Double? = null,
    val rsi14: Double? = null,
    val macdLine: Double? = null,
    val macdSignal: Double? = null,
    val macdHist: Double? = null,
    val bbUpper: Double? = null,
    val bbMiddle: Double? = null,
    val bbLower: Double? = null,
    val atr14: Double? = null,
    val adx14: Double? = null,
    val stochK: Double? = null,
    val stochD: Double? = null,
    val cci20: Double? = null,
    val roc12: Double? = null,
    val vwap: Double? = null,
    val obv: Double? = null,
    val volatility: Double? = null,
    val momentum: Double? = null,
    val trendStrength: Double? = null,
    val supportLevel: Double? = null,
    val resistanceLevel: Double? = null,
    val calculatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "batch_processing_checkpoints")
data class BatchProcessingCheckpointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pipelineName: String,
    val lastProcessedAssetIndex: Int,
    val lastProcessedSymbol: String,
    val lastProcessedTimestamp: Long,
    val totalAssetsCount: Int,
    val processedRecordsCount: Long,
    val batchSize: Int = 50,
    val status: String = "COMPLETED", // "IN_PROGRESS", "PAUSED", "COMPLETED", "FAILED"
    val lastCheckpointTime: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)


