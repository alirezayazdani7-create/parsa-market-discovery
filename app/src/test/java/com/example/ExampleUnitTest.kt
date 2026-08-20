package com.example

import com.example.data.audit.ApiResponse
import com.example.data.audit.BuildAuditDto
import com.example.data.audit.ProjectStageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun project_stage_initialization_isCorrect() {
    val stageDto = ProjectStageDto(
      stage = "PROJECT_INITIALIZATION",
      stageNumber = 1,
      description = "Infrastructure and audit access setup",
      status = "CONFIGURED",
      completedChecklist = listOf("Database", "Audit API", "Tests"),
      blockedFutureStages = listOf("Stage 2"),
      knownIssues = emptyList()
    )

    assertEquals("PROJECT_INITIALIZATION", stageDto.stage)
    assertEquals(1, stageDto.stageNumber)
    assertEquals(3, stageDto.completedChecklist.size)
  }

  @Test
  fun build_audit_metrics_isCorrect() {
    val buildDto = BuildAuditDto(
      buildStatus = "PASSED",
      applicationId = "com.aistudio.parsa.audit",
      versionName = "1.0",
      versionCode = 1,
      targetSdk = 36,
      minSdk = 24,
      composeEnabled = true,
      kspEnabled = true,
      secretsPluginActive = true,
      lastBuildTime = System.currentTimeMillis()
    )

    assertEquals("PASSED", buildDto.buildStatus)
    assertEquals("com.aistudio.parsa.audit", buildDto.applicationId)
    assertTrue(buildDto.targetSdk >= 34)
  }

  @Test
  fun full_state_audit_structure_isCorrect() {
    val fullState = com.example.data.audit.FullStateAuditDto(
      project_version = "1.0.0-INIT",
      current_stage = "PROJECT_INITIALIZATION",
      github_status = "REQUIRES_USER_ACTION",
      web_status = "CONNECTED",
      backend_status = "CONNECTED",
      database_status = "CONNECTED",
      build_status = "PASSED",
      tests = null,
      known_issues = listOf("Remote GitHub sync requires user action"),
      experiments = emptyList(),
      memory_status = "CONFIGURED",
      last_commit = "861c763",
      last_test_run = null
    )

    assertEquals("PROJECT_INITIALIZATION", fullState.current_stage)
    assertEquals("CONNECTED", fullState.database_status)
    assertEquals("REQUIRES_USER_ACTION", fullState.github_status)
  }

  @Test
  fun api_response_structure_isCorrect() {
    val response = ApiResponse(
      success = true,
      path = "/api/audit/status",
      data = "CONNECTED",
      status = "CONNECTED"
    )

    assertTrue(response.success)
    assertEquals("/api/audit/status", response.path)
    assertEquals("CONNECTED", response.status)
  }
}

