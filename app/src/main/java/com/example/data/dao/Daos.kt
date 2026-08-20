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
