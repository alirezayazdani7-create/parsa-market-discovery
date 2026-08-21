package com.example.data.repository

import com.example.data.AppDatabase
import com.example.data.entity.AuditLogEntity
import com.example.data.entity.ExperimentEntity
import com.example.data.entity.MemoryVersionEntity
import com.example.data.entity.ModelVersionEntity
import com.example.data.entity.SystemStateEntity
import com.example.data.entity.TestResultEntity
import com.example.data.entity.TestRunEntity
import com.example.data.entity.UserEntity
import kotlinx.coroutines.flow.Flow

class AuditRepository(val db: AppDatabase) {
    val database: AppDatabase get() = db

    val allAuditLogs: Flow<List<AuditLogEntity>> = db.auditLogDao().getAllLogs()
    val allTestRuns: Flow<List<TestRunEntity>> = db.testRunDao().getAllTestRuns()
    val allSystemStates: Flow<List<SystemStateEntity>> = db.systemStateDao().getAllState()
    val allExperiments: Flow<List<ExperimentEntity>> = db.experimentDao().getAllExperiments()
    val allMemoryVersions: Flow<List<MemoryVersionEntity>> = db.memoryVersionDao().getAllMemoryVersions()

    suspend fun logAudit(level: String, category: String, message: String, detailsJson: String? = null): Long {
        return db.auditLogDao().insertLog(
            AuditLogEntity(
                level = level,
                category = category,
                message = message,
                detailsJson = detailsJson,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun getRecentLogs(limit: Int = 100): List<AuditLogEntity> {
        return db.auditLogDao().getRecentLogs(limit)
    }

    suspend fun recordTestRun(
        suiteName: String,
        status: String,
        passedCount: Int,
        failedCount: Int,
        totalCount: Int,
        durationMs: Long,
        results: List<TestResultEntity>
    ): Long {
        val runId = db.testRunDao().insertTestRun(
            TestRunEntity(
                suiteName = suiteName,
                status = status,
                passedCount = passedCount,
                failedCount = failedCount,
                totalCount = totalCount,
                durationMs = durationMs,
                startedAt = System.currentTimeMillis()
            )
        )
        val linkedResults = results.map { it.copy(runId = runId) }
        db.testResultDao().insertResults(linkedResults)
        return runId
    }

    suspend fun getLatestTestRun(): TestRunEntity? {
        return db.testRunDao().getLatestTestRun()
    }

    suspend fun getTestRunById(id: Long): Pair<TestRunEntity?, List<TestResultEntity>> {
        val run = db.testRunDao().getTestRunById(id)
        val results = if (run != null) db.testResultDao().getResultsForRun(id) else emptyList()
        return Pair(run, results)
    }

    suspend fun updateSystemState(key: String, value: String, stage: String) {
        db.systemStateDao().insertOrUpdateState(
            SystemStateEntity(
                stateKey = key,
                value = value,
                stage = stage,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getSystemState(key: String): SystemStateEntity? {
        return db.systemStateDao().getStateByKey(key)
    }

    suspend fun getExperimentsList(): List<ExperimentEntity> {
        return db.experimentDao().getExperimentsList()
    }

    suspend fun getMemoryVersionsList(): List<MemoryVersionEntity> {
        return db.memoryVersionDao().getMemoryVersionsList()
    }

    suspend fun getModelVersionsList(): List<ModelVersionEntity> {
        return db.modelVersionDao().getModelVersionsList()
    }

    suspend fun initializeSystemStateIfNeeded() {
        if (db.userDao().getUserCount() == 0) {
            db.userDao().insertUser(
                UserEntity(
                    username = "ai_contractor_auditor",
                    role = "AUDITOR",
                    isActive = true
                )
            )
            db.userDao().insertUser(
                UserEntity(
                    username = "parsa_system_admin",
                    role = "SYSTEM",
                    isActive = true
                )
            )

            updateSystemState("CURRENT_STAGE", "PROJECT_INITIALIZATION", "PROJECT_INITIALIZATION")
            updateSystemState("BUILD_STATUS", "PASSED", "PROJECT_INITIALIZATION")
            updateSystemState("GITHUB_INTEGRATION", "REQUIRES_USER_ACTION", "PROJECT_INITIALIZATION")
            updateSystemState("ENVIRONMENT", "ANDROID_COMPOSE_WEB_STREAMING", "PROJECT_INITIALIZATION")

            db.memoryVersionDao().insertMemoryVersion(
                MemoryVersionEntity(
                    memoryKey = "SYSTEM_CORE_MEMORY",
                    version = 1,
                    schemaVersion = "1.0.0",
                    recordCount = 2
                )
            )

            db.modelVersionDao().insertModelVersion(
                ModelVersionEntity(
                    modelName = "BASE_PREDICTION_ARCHITECTURE",
                    versionTag = "v0.0.0-UNINITIALIZED",
                    architecture = "DEEP_TEMPORAL_GRAPH",
                    status = "NOT_IMPLEMENTED"
                )
            )

            // Initialize Deterministic Market Education Foundation (Stage 2 foundation)
            val existingConcepts = db.marketConceptDao().getConceptsList()
            if (existingConcepts.isEmpty()) {
                db.marketConceptDao().insertConcepts(
                    listOf(
                        com.example.data.entity.MarketConceptEntity(
                            conceptCode = "ORDER_BOOK_DYNAMICS",
                            title = "Order Book Dynamics & Depth",
                            category = "ORDER_BOOK",
                            description = "Understanding bid/ask queues, liquidity aggregation, and market depth without synthetic assumptions.",
                            difficultyLevel = 1,
                            deterministicRulesJson = """{"type":"ORDER_BOOK","rules":["Bids < Asks","Spread = Min(Ask) - Max(Bid)"]}""",
                            isVerified = true
                        ),
                        com.example.data.entity.MarketConceptEntity(
                            conceptCode = "SLIPPAGE_AND_SPREAD",
                            title = "Slippage & Spread Impact",
                            category = "LIQUIDITY",
                            description = "Mathematical calculation of execution slippage under varying book depth constraints.",
                            difficultyLevel = 1,
                            deterministicRulesJson = """{"type":"SLIPPAGE","formula":"abs(ExecPrice - ExpectedPrice) / ExpectedPrice"}""",
                            isVerified = true
                        ),
                        com.example.data.entity.MarketConceptEntity(
                            conceptCode = "POSITION_RISK_LIMIT",
                            title = "Fixed Capital & Position Risk Capping",
                            category = "RISK_CONTROL",
                            description = "Deterministic mathematical limits strictly capping per-trade exposure to protect capital.",
                            difficultyLevel = 2,
                            deterministicRulesJson = """{"type":"RISK_CAP","maxPositionRiskPct":0.02,"maxDrawdownPct":0.05}""",
                            isVerified = true
                        )
                    )
                )
            }

            val existingRules = db.riskRuleDao().getRiskRulesList()
            if (existingRules.isEmpty()) {
                db.riskRuleDao().insertRiskRules(
                    listOf(
                        com.example.data.entity.RiskRuleEntity(
                            ruleCode = "MAX_PORTFOLIO_RISK",
                            name = "Maximum Single Trade Risk",
                            category = "POSITION_SIZING",
                            formulaOrLogic = "RiskAmount <= TotalEquity * 0.02",
                            maxAllowedRiskPct = 0.02,
                            isMandatory = true
                        ),
                        com.example.data.entity.RiskRuleEntity(
                            ruleCode = "MAX_DRAWDOWN_CIRCUIT_BREAKER",
                            name = "Systemic Drawdown Circuit Breaker",
                            category = "DRAWDOWN_LIMIT",
                            formulaOrLogic = "If CurrentDrawdown >= 0.05 Then FreezeAllOrders",
                            maxAllowedRiskPct = 0.05,
                            isMandatory = true
                        )
                    )
                )
            }

            logAudit(
                level = "INFO",
                category = "SYSTEM",
                message = "PARSA System Environment and Audit Database initialized successfully with Market Education Foundation."
            )

            // Initialize Market Universe Manager with Benchmark Genesis Points
            val universeManager = com.example.data.universe.MarketUniverseManager(db)
            universeManager.initializeUniverseIfEmpty()

            // Initialize Historical Event Engine with Verified Real Market Events
            val eventEngine = com.example.data.events.HistoricalEventEngine(db)
            eventEngine.initializeEventsIfEmpty()
        }
    }

    suspend fun getMarketConcepts(): List<com.example.data.entity.MarketConceptEntity> =
        db.marketConceptDao().getConceptsList()

    suspend fun getRiskRules(): List<com.example.data.entity.RiskRuleEntity> =
        db.riskRuleDao().getRiskRulesList()

    suspend fun getUniverseAssetsPaged(limit: Int, offset: Int): List<com.example.data.entity.MarketAssetEntity> =
        db.marketAssetDao().getAssetsPaged(limit, offset)

    suspend fun getUniverseCount(): Int =
        db.marketAssetDao().getAssetsCount()

    suspend fun getAssetBySymbol(symbol: String): com.example.data.entity.MarketAssetEntity? =
        db.marketAssetDao().getAssetBySymbol(symbol)

    suspend fun insertCandles(candles: List<com.example.data.entity.HistoricalCandleEntity>) =
        db.historicalCandleDao().insertCandles(candles)

    suspend fun getCandlesChronological(symbol: String, timeframe: String): List<com.example.data.entity.HistoricalCandleEntity> =
        db.historicalCandleDao().getCandlesChronological(symbol, timeframe)

    suspend fun getRecentExperiences(limit: Int = 100): List<com.example.data.entity.ExperienceMemoryEntity> =
        db.experienceMemoryDao().getExperiencesList(limit)

    suspend fun getCrossAssetInsights(): List<com.example.data.entity.CrossAssetInsightEntity> =
        db.crossAssetInsightDao().getInsightsList()

    suspend fun getIntegrityAnomalies(): List<com.example.data.entity.DataIntegrityAnomalyEntity> =
        db.dataIntegrityAnomalyDao().getAnomaliesList()

    suspend fun getHistoricalEvents(): List<com.example.data.entity.HistoricalEventEntity> =
        db.historicalEventDao().getEventsList()

    suspend fun getEventImpacts(): List<com.example.data.entity.EventImpactEntity> =
        db.eventImpactDao().getImpactsList()

    suspend fun getIndicatorSnapshots(symbol: String, timeframe: String): List<com.example.data.entity.HistoricalIndicatorSnapshotEntity> =
        db.historicalIndicatorDao().getSnapshots(symbol, timeframe)

    suspend fun getBatchCheckpoints(): List<com.example.data.entity.BatchProcessingCheckpointEntity> =
        db.batchProcessingCheckpointDao().getAllCheckpoints()

    suspend fun getLatestBatchCheckpoint(pipelineName: String = "HISTORICAL_RESEARCH_PIPELINE"): com.example.data.entity.BatchProcessingCheckpointEntity? =
        db.batchProcessingCheckpointDao().getLatestCheckpoint(pipelineName)

    suspend fun getHistoricalSetups(limit: Int = 100): List<com.example.data.entity.HistoricalSetupEntity> =
        db.historicalSetupDao().getSetupsList(limit)

    suspend fun getHistoricalSetupsByEvent(eventId: String): List<com.example.data.entity.HistoricalSetupEntity> =
        db.historicalSetupDao().getSetupsByEvent(eventId)

    suspend fun getDiscoveredPatterns(): List<com.example.data.entity.DiscoveredPatternEntity> =
        db.discoveredPatternDao().getPatternsList()

    suspend fun getDiscoveredPatternsByGrade(grade: String): List<com.example.data.entity.DiscoveredPatternEntity> =
        db.discoveredPatternDao().getPatternsByGrade(grade)

    suspend fun getDiscoveredPatternById(patternId: String): com.example.data.entity.DiscoveredPatternEntity? =
        db.discoveredPatternDao().getPatternById(patternId)
}


