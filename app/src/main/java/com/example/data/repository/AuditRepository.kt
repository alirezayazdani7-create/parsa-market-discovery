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

class AuditRepository(private val db: AppDatabase) {

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

            logAudit(
                level = "INFO",
                category = "SYSTEM",
                message = "PARSA System Environment and Audit Database initialized successfully in Stage PROJECT_INITIALIZATION"
            )
        }
    }
}
