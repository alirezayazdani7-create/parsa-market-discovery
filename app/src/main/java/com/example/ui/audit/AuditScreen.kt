package com.example.ui.audit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.audit.TestDetailDto
import com.example.data.entity.AuditLogEntity
import com.example.ui.theme.ParsaAmber
import com.example.ui.theme.ParsaCyan
import com.example.ui.theme.ParsaCyanLight
import com.example.ui.theme.ParsaEmerald
import com.example.ui.theme.ParsaNavyCard
import com.example.ui.theme.ParsaNavyDark
import com.example.ui.theme.ParsaNavySurface
import com.example.ui.theme.ParsaRed
import com.example.ui.theme.ParsaSlate
import com.example.ui.theme.ParsaTextPrimary
import com.example.ui.theme.ParsaTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(
    viewModel: AuditViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ParsaCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "PARSA Shield",
                                tint = ParsaCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "PARSA SYSTEM AUDIT",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = ParsaTextPrimary
                            )
                            Text(
                                text = "STAGE: PROJECT_INITIALIZATION • v1.0.0-INIT",
                                style = MaterialTheme.typography.labelSmall,
                                color = ParsaCyanLight
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.initializeAndLoad() },
                        modifier = Modifier.testTag("refresh_audit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Audit",
                            tint = ParsaCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ParsaNavyDark,
                    titleContentColor = ParsaTextPrimary
                )
            )
        },
        containerColor = ParsaNavyDark
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ParsaCyan)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Tab Navigation
                val tabTitles = listOf("Overview & Git", "Test Harness", "Audit API Explorer", "Logs & DB", "AI Auditor Access")
                val tabIcons = listOf(
                    Icons.Default.Assessment,
                    Icons.Default.CheckCircle,
                    Icons.Default.Api,
                    Icons.Default.Storage,
                    Icons.Default.Security
                )

                PrimaryScrollableTabRow(
                    selectedTabIndex = uiState.selectedTab,
                    containerColor = ParsaNavySurface,
                    contentColor = ParsaCyan,
                    edgePadding = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = uiState.selectedTab == index,
                            onClick = { viewModel.selectTab(index) },
                            text = { Text(title, style = MaterialTheme.typography.labelMedium) },
                            icon = {
                                Icon(
                                    imageVector = tabIcons[index],
                                    contentDescription = title,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            selectedContentColor = ParsaCyan,
                            unselectedContentColor = ParsaTextSecondary,
                            modifier = Modifier.testTag("tab_$index")
                        )
                    }
                }

                // Tab Content
                when (uiState.selectedTab) {
                    0 -> OverviewAndGitTab(uiState, viewModel)
                    1 -> TestHarnessTab(uiState, onRunTests = { viewModel.runTests() })
                    2 -> ApiExplorerTab(uiState, onTestEndpoint = { method, path -> viewModel.testApiEndpoint(method, path) })
                    3 -> LogsAndDatabaseTab(uiState)
                    4 -> AiAuditorProtocolTab(uiState)
                }
            }
        }
    }
}

@Composable
fun OverviewAndGitTab(uiState: AuditUiState, viewModel: AuditViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Stage Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = ParsaNavyCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ParsaCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .testTag("stage_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CURRENT STAGE",
                            style = MaterialTheme.typography.labelSmall,
                            color = ParsaTextSecondary
                        )
                        StatusBadge(status = "PROJECT_INITIALIZATION", color = ParsaCyan)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Stage 1: System Infrastructure & Access Initialization",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ParsaTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Access protocols, Room audit database, automated test harness, and AI Contractor audit APIs are online. No market signals, prediction models, or trading logic are active per Stage 1 isolation constraints.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ParsaTextSecondary
                    )
                }
            }
        }

        item {
            // GitHub Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = ParsaNavyCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ParsaAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .testTag("github_status_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Git",
                                tint = ParsaAmber
                            )
                            Text(
                                text = "GITHUB CONNECTION",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ParsaTextPrimary
                            )
                        }
                        StatusBadge(status = "REQUIRES_USER_ACTION", color = ParsaAmber)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Repository initialized locally on branch 'main'. To link and push to your private remote GitHub repository:",
                        style = MaterialTheme.typography.bodySmall,
                        color = ParsaTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ParsaNavyDark, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "1. Open the AI Studio project settings menu (top-right gear icon).",
                            style = MaterialTheme.typography.bodySmall,
                            color = ParsaTextPrimary
                        )
                        Text(
                            text = "2. Select 'Push to GitHub' or 'Connect GitHub Account'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ParsaTextPrimary
                        )
                        Text(
                            text = "3. Authorize private repository access for 'PARSA'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ParsaTextPrimary
                        )
                    }
                }
            }
        }

        item {
            // Environment & Subsystems Metrics Grid
            Text(
                text = "SUBSYSTEM HEALTH & INTEGRATION STATUS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = ParsaCyanLight
            )
            Spacer(modifier = Modifier.height(8.dp))

            val components = listOf(
                Triple("ANDROID APPLICATION", "CONNECTED", ParsaEmerald),
                Triple("WEB STREAMING PREVIEW", "CONNECTED", ParsaEmerald),
                Triple("LOCAL ROOM DATABASE", "CONNECTED", ParsaEmerald),
                Triple("AUDIT REST API", "CONNECTED", ParsaEmerald),
                Triple("AUTOMATED TEST HARNESS", "TESTED", ParsaEmerald),
                Triple("AI CONTRACTOR ACCESS", "CONFIGURED", ParsaCyan),
                Triple("ZERO-SECRET SECURITY", "CONFIGURED", ParsaCyan),
                Triple("MARKET SIGNAL ENGINE", "NOT_IMPLEMENTED", ParsaSlate)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                components.forEach { (name, status, color) ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ParsaNavySurface),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = ParsaTextPrimary
                            )
                            StatusBadge(status = status, color = color)
                        }
                    }
                }
            }
        }

        item {
            // Web Preview Link Information
            Card(
                colors = CardDefaults.cardColors(containerColor = ParsaNavyCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Web Preview",
                            tint = ParsaEmerald
                        )
                        Text(
                            text = "WEB & PREVIEW ENVIRONMENT",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ParsaTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Development URL: https://ais-dev-llc5kxgndpp6f7ffbjp6qa-125971980492.europe-west2.run.app",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = ParsaCyanLight
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Shared Preview: https://ais-pre-llc5kxgndpp6f7ffbjp6qa-125971980492.europe-west2.run.app",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = ParsaTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun TestHarnessTab(uiState: AuditUiState, onRunTests: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Run Tests Action Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = ParsaNavyCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "AUTOMATED TEST HARNESS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ParsaTextPrimary
                            )
                            Text(
                                text = "Unit, Integration & Stage Gate Isolation Tests",
                                style = MaterialTheme.typography.bodySmall,
                                color = ParsaTextSecondary
                            )
                        }
                        Button(
                            onClick = onRunTests,
                            enabled = !uiState.isRunningTests,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ParsaCyan,
                                contentColor = ParsaNavyDark
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("run_tests_button")
                        ) {
                            if (uiState.isRunningTests) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = ParsaNavyDark,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Run",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("RUN ALL TESTS", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Metrics counters
                    val summary = uiState.latestTestSummary
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            label = "PASSED",
                            value = "${summary?.passedCount ?: 0}",
                            color = ParsaEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "FAILED",
                            value = "${summary?.failedCount ?: 0}",
                            color = if ((summary?.failedCount ?: 0) > 0) ParsaRed else ParsaSlate,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "STAGE GATES",
                            value = "7",
                            color = ParsaCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "DETAILED TEST SUITE EXECUTION REPORT",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = ParsaCyanLight
            )
        }

        val results = uiState.latestTestReport?.results ?: emptyList()
        items(results) { test ->
            TestItemRow(test)
        }
    }
}

@Composable
fun TestItemRow(test: TestDetailDto) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ParsaNavySurface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("test_item_${test.testName.replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = test.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = ParsaCyanLight
                    )
                    if (test.executionTimeMs > 0) {
                        Text(
                            text = "• ${test.executionTimeMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = ParsaTextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = test.testName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ParsaTextPrimary
                )
                if (!test.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = test.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (test.status == "FAILED") ParsaRed else ParsaTextSecondary
                    )
                }
            }

            val badgeColor = when (test.status) {
                "PASSED" -> ParsaEmerald
                "FAILED" -> ParsaRed
                "NOT_IMPLEMENTED" -> ParsaSlate
                else -> ParsaAmber
            }
            StatusBadge(status = test.status, color = badgeColor)
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(ParsaNavySurface, RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = ParsaTextSecondary
            )
        }
    }
}

@Composable
fun ApiExplorerTab(
    uiState: AuditUiState,
    onTestEndpoint: (String, String) -> Unit
) {
    val endpoints = listOf(
        Pair("GET", "/api/audit/full-state"),
        Pair("GET", "/api/audit/universe"),
        Pair("GET", "/api/audit/learning/experiences"),
        Pair("GET", "/api/audit/learning/insights"),
        Pair("GET", "/api/audit/integrity/anomalies"),
        Pair("GET", "/api/audit/education/concepts"),
        Pair("GET", "/api/audit/risk/rules"),
        Pair("GET", "/api/audit/status"),
        Pair("GET", "/api/audit/build"),
        Pair("GET", "/api/audit/project-stage"),
        Pair("GET", "/api/audit/tests"),
        Pair("GET", "/api/audit/tests/1"),
        Pair("POST", "/api/audit/tests/run"),
        Pair("GET", "/api/audit/logs"),
        Pair("GET", "/api/audit/experiments"),
        Pair("POST", "/api/audit/experiments/run"),
        Pair("GET", "/api/audit/memory")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ParsaNavyCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AUDIT REST API ENDPOINTS",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ParsaTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select any standardized audit route to dispatch and inspect live responses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ParsaTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        endpoints.forEach { (method, path) ->
                            val isSelected = uiState.apiExplorerRoute == path && uiState.apiExplorerMethod == method
                            FilterChip(
                                selected = isSelected,
                                onClick = { onTestEndpoint(method, path) },
                                label = {
                                    Text(
                                        text = "$method $path",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ParsaCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = ParsaCyan,
                                    containerColor = ParsaNavySurface,
                                    labelColor = ParsaTextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    selectedBorderColor = ParsaCyan,
                                    borderColor = ParsaSlate.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.testTag("api_chip_${path.replace("/", "_")}")
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ParsaNavySurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ParsaCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${uiState.apiExplorerMethod} ${uiState.apiExplorerRoute}",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Bold,
                            color = ParsaCyan
                        )
                        OutlinedButton(
                            onClick = { onTestEndpoint(uiState.apiExplorerMethod, uiState.apiExplorerRoute) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("DISPATCH", fontSize = 11.sp, color = ParsaCyan)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ParsaNavyDark, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = uiState.apiExplorerResponse,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            ),
                            color = ParsaTextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogsAndDatabaseTab(uiState: AuditUiState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ParsaNavyCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ROOM DATABASE SCHEMA OVERVIEW",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ParsaTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "8 Core Schema Tables active with zero synthetic market data:",
                        style = MaterialTheme.typography.bodySmall,
                        color = ParsaTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val tables = listOf(
                        "users (id, username, role, createdAt, isActive)",
                        "system_state (stateKey, value, stage, updatedAt)",
                        "experiments (id, name, type, status, configJson, createdAt)",
                        "test_runs (id, suiteName, status, passed, failed, total, startedAt)",
                        "test_results (id, runId, testName, category, status, error)",
                        "audit_logs (id, level, category, message, detailsJson, timestamp)",
                        "model_versions (id, modelName, versionTag, architecture, status)",
                        "memory_versions (id, memoryKey, version, schemaVersion, recordCount)"
                    )

                    tables.forEach { table ->
                        Text(
                            text = "• $table",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = ParsaCyanLight,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "PERSISTENT AUDIT TRAIL LOGS (${uiState.auditLogs.size})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = ParsaCyanLight
            )
        }

        items(uiState.auditLogs) { log ->
            AuditLogRow(log)
        }
    }
}

@Composable
fun AuditLogRow(log: AuditLogEntity) {
    val dateStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
    val levelColor = when (log.level) {
        "ERROR" -> ParsaRed
        "WARN" -> ParsaAmber
        "SECURITY" -> ParsaCyan
        else -> ParsaEmerald
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ParsaNavySurface),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(levelColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = log.level,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = levelColor
                        )
                    }
                    Text(
                        text = "[${log.category}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = ParsaTextSecondary
                    )
                }
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = ParsaTextSecondary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall,
                color = ParsaTextPrimary
            )
        }
    }
}

@Composable
fun AiAuditorProtocolTab(uiState: AuditUiState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ParsaNavyCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "AI Auditor Info",
                            tint = ParsaCyan
                        )
                        Text(
                            text = "AI CONTRACTOR / AUDITOR ACCESS SPECIFICATION",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ParsaTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Document location: /docs/AI_CONTRACTOR_ACCESS.md",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = ParsaCyanLight
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val points = listOf(
                        "1. Source Code Inspection: Direct root repository access via AI Studio workspace tree and GitHub.",
                        "2. Build Verification: Check Gradle build files, compile_applet logs, and GET /api/audit/build.",
                        "3. Test Harness: Execute via POST /api/audit/tests/run or inspect reports via GET /api/audit/tests.",
                        "4. System Logs: Query Room audit_logs via GET /api/audit/logs.",
                        "5. Memory State: Access schema state versions via GET /api/audit/memory.",
                        "6. Experiments: Verify experimental registry and isolation status via GET /api/audit/experiments.",
                        "7. Project Stage: Check milestone progression via GET /api/audit/project-stage."
                    )

                    points.forEach { pt ->
                        Text(
                            text = pt,
                            style = MaterialTheme.typography.bodySmall,
                            color = ParsaTextSecondary,
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ParsaNavySurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SECURITY & LEAST PRIVILEGE COMPLIANCE",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ParsaTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Zero passwords, private keys, wallet seeds, or production secrets stored in source code.\n• Least privilege model enforced across all database tables and API handlers.\n• All operations are logged to the append-only audit trail.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ParsaTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
