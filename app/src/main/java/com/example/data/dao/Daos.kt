package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.AuditLogEntity
import com.example.data.entity.ExperimentEntity
import com.example.data.entity.MemoryVersionEntity
import com.example.data.entity.ModelVersionEntity
import com.example.data.entity.SystemStateEntity
import com.example.data.entity.TestResultEntity
import com.example.data.entity.TestRunEntity
import com.example.data.entity.UserEntity
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
    fun getAllConcepts(): Flow<List<com.example.data.entity.MarketConceptEntity>>

    @Query("SELECT * FROM market_concepts ORDER BY difficultyLevel ASC, id ASC")
    suspend fun getConceptsList(): List<com.example.data.entity.MarketConceptEntity>

    @Query("SELECT * FROM market_concepts WHERE conceptCode = :code LIMIT 1")
    suspend fun getConceptByCode(code: String): com.example.data.entity.MarketConceptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConcept(concept: com.example.data.entity.MarketConceptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConcepts(concepts: List<com.example.data.entity.MarketConceptEntity>)
}

@Dao
interface RiskRuleDao {
    @Query("SELECT * FROM risk_rules ORDER BY id ASC")
    fun getAllRiskRules(): Flow<List<com.example.data.entity.RiskRuleEntity>>

    @Query("SELECT * FROM risk_rules ORDER BY id ASC")
    suspend fun getRiskRulesList(): List<com.example.data.entity.RiskRuleEntity>

    @Query("SELECT * FROM risk_rules WHERE ruleCode = :code LIMIT 1")
    suspend fun getRuleByCode(code: String): com.example.data.entity.RiskRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRiskRule(rule: com.example.data.entity.RiskRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRiskRules(rules: List<com.example.data.entity.RiskRuleEntity>)
}

@Dao
interface EducationProgressDao {
    @Query("SELECT * FROM education_progress WHERE userId = :userId")
    fun getUserProgress(userId: Long): Flow<List<com.example.data.entity.EducationProgressEntity>>

    @Query("SELECT * FROM education_progress WHERE userId = :userId AND conceptCode = :conceptCode LIMIT 1")
    suspend fun getProgressForConcept(userId: Long, conceptCode: String): com.example.data.entity.EducationProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProgress(progress: com.example.data.entity.EducationProgressEntity): Long
}

@Dao
interface MarketAssetDao {
    @Query("SELECT * FROM market_assets ORDER BY marketCapRank ASC")
    fun getAllAssets(): Flow<List<com.example.data.entity.MarketAssetEntity>>

    @Query("SELECT * FROM market_assets ORDER BY marketCapRank ASC LIMIT :limit OFFSET :offset")
    suspend fun getAssetsPaged(limit: Int, offset: Int): List<com.example.data.entity.MarketAssetEntity>

    @Query("SELECT COUNT(*) FROM market_assets")
    suspend fun getAssetsCount(): Int

    @Query("SELECT * FROM market_assets WHERE symbol = :symbol LIMIT 1")
    suspend fun getAssetBySymbol(symbol: String): com.example.data.entity.MarketAssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: com.example.data.entity.MarketAssetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssets(assets: List<com.example.data.entity.MarketAssetEntity>)
}

@Dao
interface HistoricalCandleDao {
    @Query("SELECT * FROM historical_candles WHERE symbol = :symbol AND timeframe = :timeframe ORDER BY openTime ASC")
    suspend fun getCandlesChronological(symbol: String, timeframe: String): List<com.example.data.entity.HistoricalCandleEntity>

    @Query("SELECT * FROM historical_candles WHERE symbol = :symbol AND timeframe = :timeframe AND openTime <= :asOfTime ORDER BY openTime ASC")
    suspend fun getCandlesUpToTime(symbol: String, timeframe: String, asOfTime: Long): List<com.example.data.entity.HistoricalCandleEntity>

    @Query("SELECT * FROM historical_candles WHERE symbol = :symbol AND timeframe = :timeframe AND openTime > :asOfTime ORDER BY openTime ASC LIMIT :limit")
    suspend fun getForwardEvaluationCandles(symbol: String, timeframe: String, asOfTime: Long, limit: Int): List<com.example.data.entity.HistoricalCandleEntity>

    @Query("SELECT COUNT(*) FROM historical_candles WHERE symbol = :symbol")
    suspend fun getCandlesCountForSymbol(symbol: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandles(candles: List<com.example.data.entity.HistoricalCandleEntity>)
}

@Dao
interface ExperienceMemoryDao {
    @Query("SELECT * FROM experience_memories ORDER BY timestamp DESC")
    fun getAllExperiences(): Flow<List<com.example.data.entity.ExperienceMemoryEntity>>

    @Query("SELECT * FROM experience_memories ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getExperiencesList(limit: Int = 100): List<com.example.data.entity.ExperienceMemoryEntity>

    @Query("SELECT * FROM experience_memories WHERE assetSymbol = :symbol ORDER BY timestamp ASC")
    suspend fun getExperiencesForAsset(symbol: String): List<com.example.data.entity.ExperienceMemoryEntity>

    @Query("SELECT COUNT(*) FROM experience_memories")
    suspend fun getExperiencesCount(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExperience(experience: com.example.data.entity.ExperienceMemoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExperiences(experiences: List<com.example.data.entity.ExperienceMemoryEntity>)
}

@Dao
interface CrossAssetInsightDao {
    @Query("SELECT * FROM cross_asset_insights ORDER BY createdAt DESC")
    fun getAllInsights(): Flow<List<com.example.data.entity.CrossAssetInsightEntity>>

    @Query("SELECT * FROM cross_asset_insights ORDER BY createdAt DESC")
    suspend fun getInsightsList(): List<com.example.data.entity.CrossAssetInsightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInsight(insight: com.example.data.entity.CrossAssetInsightEntity): Long
}

@Dao
interface DataIntegrityAnomalyDao {
    @Query("SELECT * FROM data_integrity_anomalies ORDER BY detectedAt DESC")
    fun getAllAnomalies(): Flow<List<com.example.data.entity.DataIntegrityAnomalyEntity>>

    @Query("SELECT * FROM data_integrity_anomalies ORDER BY detectedAt DESC")
    suspend fun getAnomaliesList(): List<com.example.data.entity.DataIntegrityAnomalyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnomaly(anomaly: com.example.data.entity.DataIntegrityAnomalyEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnomalies(anomalies: List<com.example.data.entity.DataIntegrityAnomalyEntity>)
}

