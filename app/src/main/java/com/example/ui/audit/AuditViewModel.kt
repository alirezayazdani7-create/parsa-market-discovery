package com.example.ui.audit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.audit.ApiResponse
import com.example.data.audit.AuditApiService
import com.example.data.audit.BuildAuditDto
import com.example.data.audit.MemoryInspectionDto
import com.example.data.audit.ProjectStageDto
import com.example.data.audit.SystemStatusDto
import com.example.data.audit.TestRunReportDto
import com.example.data.audit.TestSummaryDto
import com.example.data.entity.AuditLogEntity
import com.example.data.repository.AuditRepository
import com.example.data.testing.AutomatedTestEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuditUiState(
    val isLoading: Boolean = true,
    val isRunningTests: Boolean = false,
    val selectedTab: Int = 0,
    val systemStatus: SystemStatusDto? = null,
    val buildAudit: BuildAuditDto? = null,
    val projectStage: ProjectStageDto? = null,
    val latestTestSummary: TestSummaryDto? = null,
    val latestTestReport: TestRunReportDto? = null,
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val memoryInfo: MemoryInspectionDto? = null,
    val apiExplorerRoute: String = "/api/audit/status",
    val apiExplorerMethod: String = "GET",
    val apiExplorerResponse: String = "",
    val errorMessage: String? = null
)

class AuditViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = AuditRepository(database)
    private val testEngine = AutomatedTestEngine(repository)
    val apiService = AuditApiService(repository, testEngine)

    private val _uiState = MutableStateFlow(AuditUiState())
    val uiState: StateFlow<AuditUiState> = _uiState.asStateFlow()

    init {
        initializeAndLoad()
    }

    fun initializeAndLoad() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.initializeSystemStateIfNeeded()

                // Run initial tests if not present
                val latestRun = repository.getLatestTestRun()
                val runId = latestRun?.id ?: testEngine.runAllAutomatedTests()

                val statusRes = apiService.getStatus()
                val buildRes = apiService.getBuild()
                val stageRes = apiService.getProjectStage()
                val testRes = apiService.getTestById(runId)
                val memRes = apiService.getMemory()
                val logs = repository.getRecentLogs(50)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        systemStatus = statusRes.data,
                        buildAudit = buildRes.data,
                        projectStage = stageRes.data,
                        latestTestSummary = testRes.data?.run,
                        latestTestReport = testRes.data,
                        memoryInfo = memRes.data,
                        auditLogs = logs
                    )
                }

                // Initial API explorer load
                testApiEndpoint("GET", "/api/audit/status")
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Initialization error: ${e.message}"
                    )
                }
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    fun runTests() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRunningTests = true) }
            try {
                val runId = testEngine.runAllAutomatedTests()
                val testRes = apiService.getTestById(runId)
                val logs = repository.getRecentLogs(50)

                _uiState.update {
                    it.copy(
                        isRunningTests = false,
                        latestTestSummary = testRes.data?.run,
                        latestTestReport = testRes.data,
                        auditLogs = logs
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRunningTests = false,
                        errorMessage = "Test run failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun testApiEndpoint(method: String, path: String) {
        viewModelScope.launch {
            try {
                val response = apiService.dispatchRoute(method, path)
                val prettyJson = formatApiResponse(response)
                _uiState.update {
                    it.copy(
                        apiExplorerMethod = method,
                        apiExplorerRoute = path,
                        apiExplorerResponse = prettyJson
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        apiExplorerMethod = method,
                        apiExplorerRoute = path,
                        apiExplorerResponse = "Error invoking endpoint: ${e.message}"
                    )
                }
            }
        }
    }

    private fun formatApiResponse(res: ApiResponse<out Any>): String {
        return """
        {
          "success": ${res.success},
          "status": "${res.status ?: "UNKNOWN"}",
          "path": "${res.path}",
          "timestamp": ${res.timestamp},
          "data": ${res.data ?: "null"},
          "error": ${if (res.error != null) "\"${res.error}\"" else "null"}
        }
        """.trimIndent()
    }
}
