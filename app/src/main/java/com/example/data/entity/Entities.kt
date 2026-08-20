package com.example.data.entity

import androidx.room.Entity
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
