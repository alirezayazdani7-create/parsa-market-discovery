package com.example.data.audit

data class SystemStatusDto(
    val status: String, // "CONNECTED", "CONFIGURED", "RUNNING"
    val projectVersion: String,
    val currentStage: String,
    val timestamp: Long,
    val environment: EnvironmentInfoDto,
    val components: Map<String, String>,
    val requiresUserAction: List<String>
)

data class EnvironmentInfoDto(
    val platform: String,
    val runtime: String,
    val targetSdk: Int,
    val isEmulatorStreaming: Boolean,
    val previewUrl: String?
)

data class BuildAuditDto(
    val buildStatus: String, // "PASSED"
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val targetSdk: Int,
    val minSdk: Int,
    val composeEnabled: Boolean,
    val kspEnabled: Boolean,
    val secretsPluginActive: Boolean,
    val lastBuildTime: Long
)

data class ProjectStageDto(
    val stage: String, // "PROJECT_INITIALIZATION"
    val stageNumber: Int,
    val description: String,
    val status: String,
    val completedChecklist: List<String>,
    val blockedFutureStages: List<String>,
    val knownIssues: List<String>
)

data class TestSummaryDto(
    val runId: Long,
    val suiteName: String,
    val status: String,
    val totalCount: Int,
    val passedCount: Int,
    val failedCount: Int,
    val durationMs: Long,
    val timestamp: Long
)

data class TestDetailDto(
    val testId: Long,
    val testName: String,
    val category: String,
    val status: String,
    val executionTimeMs: Long,
    val errorMessage: String?
)

data class TestRunReportDto(
    val run: TestSummaryDto,
    val results: List<TestDetailDto>
)

data class AuditLogDto(
    val id: Long,
    val level: String,
    val category: String,
    val message: String,
    val detailsJson: String?,
    val timestamp: Long
)

data class ExperimentItemDto(
    val id: Long,
    val name: String,
    val type: String,
    val status: String,
    val configJson: String,
    val createdAt: Long
)

data class MemoryInspectionDto(
    val memoryStatus: String, // "CONFIGURED"
    val activeVersions: List<MemoryVersionItemDto>,
    val patternDiscoveryCache: String, // "NOT_IMPLEMENTED"
    val marketDataMemory: String // "NOT_IMPLEMENTED"
)

data class MemoryVersionItemDto(
    val memoryKey: String,
    val version: Int,
    val schemaVersion: String,
    val recordCount: Long,
    val updatedAt: Long
)

data class FullStateAuditDto(
    val project_version: String,
    val current_stage: String,
    val github_status: String,
    val web_status: String,
    val backend_status: String,
    val database_status: String,
    val build_status: String,
    val tests: TestSummaryDto?,
    val known_issues: List<String>,
    val experiments: List<ExperimentItemDto>,
    val memory_status: String,
    val last_commit: String,
    val last_test_run: Long?
)

data class ApiResponse<T>(
    val success: Boolean,
    val path: String,
    val timestamp: Long = System.currentTimeMillis(),
    val data: T? = null,
    val error: String? = null,
    val status: String? = null
)
