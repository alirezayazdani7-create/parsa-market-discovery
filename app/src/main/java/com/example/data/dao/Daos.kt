package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY id ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int
}

@Dao
interface SystemStateDao {
    @Query("SELECT * FROM system_state")
    fun getAllState(): Flow<List<SystemStateEntity>>

    @Query("SELECT * FROM system_state WHERE stateKey = :key LIMIT 1")
    suspend fun getStateByKey(key: String): SystemStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateState(state: SystemStateEntity)
}

@Dao
interface ExperimentDao {
    @Query("SELECT * FROM experiments ORDER BY createdAt DESC")
    fun getAllExperiments(): Flow<List<ExperimentEntity>>

    @Query("SELECT * FROM experiments ORDER BY createdAt DESC")
    suspend fun getExperimentsList(): List<ExperimentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExperiment(experiment: ExperimentEntity): Long

    @Update
    suspend fun updateExperiment(experiment: ExperimentEntity)
}

@Dao
interface TestRunDao {
    @Query("SELECT * FROM test_runs ORDER BY startedAt DESC")
    fun getAllTestRuns(): Flow<List<TestRunEntity>>

    @Query("SELECT * FROM test_runs WHERE id = :id LIMIT 1")
    suspend fun getTestRunById(id: Long): TestRunEntity?

    @Query("SELECT * FROM test_runs ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestTestRun(): TestRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTestRun(run: TestRunEntity): Long

    @Update
    suspend fun updateTestRun(run: TestRunEntity)
}

@Dao
interface TestResultDao {
    @Query("SELECT * FROM test_results WHERE runId = :runId ORDER BY id ASC")
    suspend fun getResultsForRun(runId: Long): List<TestResultEntity>

    @Query("SELECT * FROM test_results ORDER BY timestamp DESC LIMIT 100")
    fun getRecentTestResults(): Flow<List<TestResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResults(results: List<TestResultEntity>)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 100): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLogEntity): Long
}

@Dao
interface ModelVersionDao {
    @Query("SELECT * FROM model_versions ORDER BY createdAt DESC")
    fun getAllModelVersions(): Flow<List<ModelVersionEntity>>

    @Query("SELECT * FROM model_versions ORDER BY createdAt DESC")
    suspend fun getModelVersionsList(): List<ModelVersionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModelVersion(model: ModelVersionEntity): Long
}

@Dao
interface MemoryVersionDao {
    @Query("SELECT * FROM memory_versions ORDER BY updatedAt DESC")
    fun getAllMemoryVersions(): Flow<List<MemoryVersionEntity>>

    @Query("SELECT * FROM memory_versions ORDER BY updatedAt DESC")
    suspend fun getMemoryVersionsList(): List<MemoryVersionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemoryVersion(memory: MemoryVersionEntity): Long
}

@Dao
interface MarketConceptDao {
    @Query("SELECT * FROM market_concepts ORDER BY difficultyLevel ASC, id ASC")
    fun getAllConcepts(): Flow<List<MarketConceptEntity>>

    @Query("SELECT * FROM market_concepts ORDER BY difficultyLevel ASC, id ASC")
    suspend fun getConceptsList(): List<MarketConceptEntity>

    @Query("SELECT * FROM market_concepts WHERE conceptCode = :code LIMIT 1")
    suspend fun getConceptByCode(code: String): MarketConceptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConcept(concept: MarketConceptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConcepts(concepts: List<MarketConceptEntity>)
}

@Dao
interface RiskRuleDao {
    @Query("SELECT * FROM risk_rules ORDER BY id ASC")
    fun getAllRiskRules(): Flow<List<RiskRuleEntity>>

    @Query("SELECT * FROM risk_rules ORDER BY id ASC")
    suspend fun getRiskRulesList(): List<RiskRuleEntity>

    @Query("SELECT * FROM risk_rules WHERE ruleCode = :code LIMIT 1")
    suspend fun getRuleByCode(code: String): RiskRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRiskRule(rule: RiskRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRiskRules(rules: List<RiskRuleEntity>)
}

@Dao
interface EducationProgressDao {
    @Query("SELECT * FROM education_progress WHERE userId = :userId")
    fun getUserProgress(userId: Long): Flow<List<EducationProgressEntity>>

    @Query("SELECT * FROM education_progress WHERE userId = :userId AND conceptCode = :conceptCode LIMIT 1")
    suspend fun getProgressForConcept(userId: Long, conceptCode: String): EducationProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProgress(progress: EducationProgressEntity): Long
}

@Dao
interface MarketAssetDao {
    @Query("SELECT * FROM market_assets ORDER BY marketCapRank ASC")
    fun getAllAssets(): Flow<List<MarketAssetEntity>>

    @Query("SELECT * FROM market_assets ORDER BY marketCapRank ASC LIMIT :limit OFFSET :offset")
    suspend fun getAssetsPaged(limit: Int, offset: Int): List<MarketAssetEntity>

    @Query("SELECT COUNT(*) FROM market_assets")
    suspend fun getAssetsCount(): Int

    @Query("SELECT * FROM market_assets WHERE symbol = :symbol LIMIT 1")
    suspend fun getAssetBySymbol(symbol: String): MarketAssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: MarketAssetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssets(assets: List<MarketAssetEntity>)
}

@Dao
interface HistoricalCandleDao {
    @Query("SELECT * FROM historical_candles WHERE symbol = :symbol AND timeframe = :timeframe ORDER BY openTime ASC")
    suspend fun getCandlesChronological(symbol: String, timeframe: String): List<HistoricalCandleEntity>

    @Query("SELECT * FROM historical_candles WHERE symbol = :symbol AND timeframe = :timeframe AND openTime <= :asOfTime ORDER BY openTime ASC")
    suspend fun getCandlesUpToTime(symbol: String, timeframe: String, asOfTime: Long): List<HistoricalCandleEntity>

    @Query("SELECT * FROM historical_candles WHERE symbol = :symbol AND timeframe = :timeframe AND openTime > :asOfTime ORDER BY openTime ASC LIMIT :limit")
    suspend fun getForwardEvaluationCandles(symbol: String, timeframe: String, asOfTime: Long, limit: Int): List<HistoricalCandleEntity>

    @Query("SELECT COUNT(*) FROM historical_candles WHERE symbol = :symbol")
    suspend fun getCandlesCountForSymbol(symbol: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandles(candles: List<HistoricalCandleEntity>)
}

@Dao
interface ExperienceMemoryDao {
    @Query("SELECT * FROM experience_memories ORDER BY timestamp DESC")
    fun getAllExperiences(): Flow<List<ExperienceMemoryEntity>>

    @Query("SELECT * FROM experience_memories ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getExperiencesList(limit: Int = 100): List<ExperienceMemoryEntity>

    @Query("SELECT * FROM experience_memories WHERE assetSymbol = :symbol ORDER BY timestamp ASC")
    suspend fun getExperiencesForAsset(symbol: String): List<ExperienceMemoryEntity>

    @Query("SELECT COUNT(*) FROM experience_memories")
    suspend fun getExperiencesCount(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExperience(experience: ExperienceMemoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExperiences(experiences: List<ExperienceMemoryEntity>)
}

@Dao
interface CrossAssetInsightDao {
    @Query("SELECT * FROM cross_asset_insights ORDER BY createdAt DESC")
    fun getAllInsights(): Flow<List<CrossAssetInsightEntity>>

    @Query("SELECT * FROM cross_asset_insights ORDER BY createdAt DESC")
    suspend fun getInsightsList(): List<CrossAssetInsightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInsight(insight: CrossAssetInsightEntity): Long
}

@Dao
interface DataIntegrityAnomalyDao {
    @Query("SELECT * FROM data_integrity_anomalies ORDER BY detectedAt DESC")
    fun getAllAnomalies(): Flow<List<DataIntegrityAnomalyEntity>>

    @Query("SELECT * FROM data_integrity_anomalies ORDER BY detectedAt DESC")
    suspend fun getAnomaliesList(): List<DataIntegrityAnomalyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnomaly(anomaly: DataIntegrityAnomalyEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnomalies(anomalies: List<DataIntegrityAnomalyEntity>)
}

@Dao
interface HistoricalEventDao {
    @Query("SELECT * FROM historical_events ORDER BY eventTimestamp DESC")
    fun getAllEvents(): Flow<List<HistoricalEventEntity>>

    @Query("SELECT * FROM historical_events ORDER BY eventTimestamp DESC")
    suspend fun getEventsList(): List<HistoricalEventEntity>

    @Query("SELECT * FROM historical_events WHERE eventId = :eventId LIMIT 1")
    suspend fun getEventById(eventId: String): HistoricalEventEntity?

    @Query("SELECT * FROM historical_events WHERE eventTimestamp BETWEEN :startTime AND :endTime ORDER BY eventTimestamp ASC")
    suspend fun getEventsInRange(startTime: Long, endTime: Long): List<HistoricalEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: HistoricalEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<HistoricalEventEntity>)
}

@Dao
interface EventImpactDao {
    @Query("SELECT * FROM event_impacts ORDER BY calculatedAt DESC")
    fun getAllImpacts(): Flow<List<EventImpactEntity>>

    @Query("SELECT * FROM event_impacts ORDER BY calculatedAt DESC")
    suspend fun getImpactsList(): List<EventImpactEntity>

    @Query("SELECT * FROM event_impacts WHERE eventId = :eventId")
    suspend fun getImpactsByEvent(eventId: String): List<EventImpactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImpact(impact: EventImpactEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImpacts(impacts: List<EventImpactEntity>)
}

@Dao
interface HistoricalIndicatorDao {
    @Query("SELECT * FROM indicator_snapshots WHERE symbol = :symbol AND timeframe = :timeframe ORDER BY timestamp ASC")
    suspend fun getSnapshots(symbol: String, timeframe: String): List<HistoricalIndicatorSnapshotEntity>

    @Query("SELECT * FROM indicator_snapshots WHERE symbol = :symbol AND timeframe = :timeframe AND timestamp <= :asOfTime ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSnapshotAsOf(symbol: String, timeframe: String, asOfTime: Long): HistoricalIndicatorSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: HistoricalIndicatorSnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshots(snapshots: List<HistoricalIndicatorSnapshotEntity>)
}

@Dao
interface BatchProcessingCheckpointDao {
    @Query("SELECT * FROM batch_processing_checkpoints WHERE pipelineName = :pipelineName ORDER BY lastCheckpointTime DESC LIMIT 1")
    suspend fun getLatestCheckpoint(pipelineName: String): BatchProcessingCheckpointEntity?

    @Query("SELECT * FROM batch_processing_checkpoints ORDER BY lastCheckpointTime DESC")
    suspend fun getAllCheckpoints(): List<BatchProcessingCheckpointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCheckpoint(checkpoint: BatchProcessingCheckpointEntity): Long
}


