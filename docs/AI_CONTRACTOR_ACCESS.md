# PARSA PROJECT — AI CONTRACTOR & AUDITOR ACCESS PROTOCOL

**Document Version:** 1.1.0  
**Project Stage:** `PROJECT_INITIALIZATION`  
**Target Environment:** AI Studio Android / Web Preview  
**Last Updated:** 2026-08-20  

---

## 1. Overview & Contractor Identity
This document establishes the official technical audit access protocol for any independent AI Contractor or Auditor (such as ChatGPT, Claude, or an automated evaluator) reviewing the **PARSA** project. It details the exact paths, mechanisms, limitations, security boundaries, and step-by-step procedures for accessing the project.

> **CRITICAL DIRECT ACCESS STATUS NOTICE:**  
> **Direct AI Contractor access is not currently available through this environment without manual user interaction.**  
> Standard external LLMs (e.g. standard ChatGPT chat sessions) cannot directly reach private internal URLs or execute commands inside this isolated container without one of the following two user-enabled conduits:
> 1. The user connects the project to a private/public **GitHub Repository** via the AI Studio UI, allowing external tools to read the repository via GitHub API or GitHub Connectors.
> 2. The user shares the public Web Preview URL / API endpoint with the contractor, or exports the project archive.

---

## 2. Source Code & Repository Access

### 2.1 Directory Structure
- **Root Directory (`/`):** Contains build configuration, manifests, docs, and app modules.
  - **Android & Core Logic:** `/app/src/main/java/com/example/`
    - `data/AppDatabase.kt`: Room database definition.
    - `data/entity/Entities.kt`: 8 core schema entities.
    - `data/dao/Daos.kt`: DAO interfaces for database transactions.
    - `data/audit/AuditApiService.kt`: REST controller and route dispatcher.
    - `data/audit/AuditModels.kt`: DTO definitions and machine-readable data models.
    - `data/repository/AuditRepository.kt`: Repository layer.
    - `data/testing/AutomatedTestEngine.kt`: Test execution harness and stage gate stubs.
    - `ui/audit/`: Jetpack Compose audit dashboard and live REST explorer.
  - **Automated Tests:** `/app/src/test/java/com/example/`
    - `ExampleUnitTest.kt`: Unit tests for models and schemas.
    - `ExampleRobolectricTest.kt`: Database & DAO transaction integration tests.
    - `GreetingScreenshotTest.kt`: Screenshot visual regression tests.
  - **Configuration & Build:** `/build.gradle.kts`, `/app/build.gradle.kts`, `/settings.gradle.kts`, `/gradle/libs.versions.toml`
  - **Platform Metadata:** `/metadata.json`

### 2.2 GitHub Connection & How External AI Reads Source
- **Local Git Status:** Initialized on `main` branch with verified commits.
- **Remote Origin:** None currently configured (`REQUIRES_USER_ACTION`).
- **How to Connect GitHub (Required User Action):**
  1. Open the AI Studio project dashboard.
  2. Click on the **Settings** menu (gear icon in the top right).
  3. Select **Push to GitHub** or **Export to GitHub**.
  4. Authorize your GitHub account and choose target repository name (`PARSA` or similar).
- **How AI Contractor reads GitHub once pushed:**
  - Using ChatGPT's GitHub App / Action / Connector, or via `api.github.com/repos/{owner}/{repo}` with a Personal Access Token (PAT) with `repo:read` permission.

---

## 3. Web Application & Preview Access

### 3.1 Web & Public Deployment Details
- **Architecture:** Android Kotlin/Compose with web-streaming preview container and embedded API dispatcher.
- **Development App URL:** `https://ais-dev-llc5kxgndpp6f7ffbjp6qa-125971980492.europe-west2.run.app`
- **Shared App URL:** `https://ais-pre-llc5kxgndpp6f7ffbjp6qa-125971980492.europe-west2.run.app`
- **How External AI Reviews Web:**
  - An AI Contractor with web browsing or HTTP fetch capabilities can access the Shared App URL to inspect the live interface, status badges, and interactive audit tabs.

---

## 4. Audit REST API Specification

All endpoints return structured JSON with timestamps, success flags, and machine-readable DTOs.

### 4.1 `GET /api/audit/full-state`
- **URL:** `/api/audit/full-state`
- **Method:** `GET`
- **Authentication:** Read-Only (Public in Preview / Container)
- **Purpose:** Complete single-payload snapshot of system health, database, tests, and stage gates.
- **Example Request:**
  ```http
  GET /api/audit/full-state HTTP/1.1
  Host: ais-pre-llc5kxgndpp6f7ffbjp6qa-125971980492.europe-west2.run.app
  Accept: application/json
  ```
- **Example Response:**
  ```json
  {
    "success": true,
    "path": "/api/audit/full-state",
    "timestamp": 1724181500000,
    "data": {
      "project_version": "1.0.0-INIT",
      "current_stage": "PROJECT_INITIALIZATION",
      "github_status": "REQUIRES_USER_ACTION",
      "web_status": "CONNECTED",
      "backend_status": "CONNECTED",
      "database_status": "CONNECTED",
      "build_status": "PASSED",
      "tests": {
        "runId": 1,
        "suiteName": "Automated Core & Initialization Suite",
        "status": "PASSED",
        "totalCount": 10,
        "passedCount": 4,
        "failedCount": 0,
        "durationMs": 420,
        "timestamp": 1724181400000
      },
      "known_issues": [
        "Remote GitHub repository synchronization requires user authorization in AI Studio settings"
      ],
      "experiments": [],
      "memory_status": "CONFIGURED",
      "last_commit": "861c763 feat(init): PARSA Project Initialization & Audit System Setup",
      "last_test_run": 1724181400000
    },
    "status": "CONNECTED"
  }
  ```
- **Current Status:** `VERIFIED`

---

### 4.2 `GET /api/audit/status`
- **URL:** `/api/audit/status`
- **Method:** `GET`
- **Authentication:** None (Read-Only)
- **Purpose:** System operational health, environment info, and subcomponent statuses.
- **Current Status:** `VERIFIED`

---

### 4.3 `GET /api/audit/build`
- **URL:** `/api/audit/build`
- **Method:** `GET`
- **Authentication:** None (Read-Only)
- **Purpose:** Compile-time metadata, target SDK 36, Kotlin/Compose compiler flags, KSP status.
- **Current Status:** `VERIFIED`

---

### 4.4 `GET /api/audit/project-stage`
- **URL:** `/api/audit/project-stage`
- **Method:** `GET`
- **Authentication:** None (Read-Only)
- **Purpose:** Current stage (`PROJECT_INITIALIZATION`), completed checklists, and blocked future milestones.
- **Current Status:** `VERIFIED`

---

### 4.5 `GET /api/audit/tests`
- **URL:** `/api/audit/tests`
- **Method:** `GET`
- **Authentication:** None (Read-Only)
- **Purpose:** Historical test runs and test suite execution summary.
- **Current Status:** `VERIFIED`

---

### 4.6 `GET /api/audit/tests/{id}`
- **URL:** `/api/audit/tests/{id}` (e.g. `/api/audit/tests/1`)
- **Method:** `GET`
- **Authentication:** None (Read-Only)
- **Purpose:** Detailed assertion-level report for a specific test run.
- **Current Status:** `VERIFIED`

---

### 4.7 `POST /api/audit/tests/run`
- **URL:** `/api/audit/tests/run`
- **Method:** `POST`
- **Authentication:** Test Execution Permission
- **Purpose:** Triggers on-demand execution of the test harness.
- **Current Status:** `VERIFIED`

---

### 4.8 `GET /api/audit/logs`
- **URL:** `/api/audit/logs`
- **Method:** `GET`
- **Authentication:** None (Read-Only)
- **Purpose:** Retrieves immutable audit trail logs from Room DB (`audit_logs` table).
- **Current Status:** `VERIFIED`

---

### 4.9 `GET /api/audit/experiments`
- **URL:** `/api/audit/experiments`
- **Method:** `GET`
- **Authentication:** None (Read-Only)
- **Purpose:** Lists registered experimentation runs and status.
- **Current Status:** `VERIFIED`

---

### 4.10 `POST /api/audit/experiments/run`
- **URL:** `/api/audit/experiments/run`
- **Method:** `POST`
- **Authentication:** Execution Gate
- **Purpose:** Attempt to execute an experiment.
- **Response in Current Stage:** HTTP 200 with `success: false`, `status: "NOT_IMPLEMENTED"`, and error indicating Stage Gate lock.
- **Current Status:** `VERIFIED` (Strict Stage Gate Enforcement)

---

### 4.11 `GET /api/audit/memory`
- **URL:** `/api/audit/memory`
- **Method:** `GET`
- **Authentication:** None (Read-Only)
- **Purpose:** Inspect active memory versions and schemas in the database. Returns `NOT_IMPLEMENTED` for pattern caches.
- **Current Status:** `VERIFIED`

---

### 4.12 `GET /api/audit/education/concepts`
- **URL:** `/api/audit/education/concepts`
- **Method:** `GET`
- **Authentication:** None (Read-Only)
- **Purpose:** Inspect deterministic market concepts (Order Book dynamics, Slippage, Risk capping).
- **Current Status:** `VERIFIED`

---

### 4.13 `GET /api/audit/risk/rules`
- **URL:** `/api/audit/risk/rules`
- **Method:** `GET`
- **Authentication:** None (Read-Only)
- **Purpose:** Inspect mathematical risk capping formulas and circuit breaker invariants.
- **Current Status:** `VERIFIED`

---

## 5. Test Execution Protocol for AI Auditor

1. **How AI Contractor triggers a test run:**
   - Call `POST /api/audit/tests/run` via API Explorer or HTTP client, OR
   - Run the local Gradle command: `gradle :app:testDebugUnitTest`.
2. **How AI Contractor reads test results:**
   - Inspect the returned `runId` via `GET /api/audit/tests/{runId}`, or review test logs in the in-app Audit UI Test tab.

---

## 6. Security Boundaries & Deliberately Restricted Resources

To ensure complete safety and isolation, the following items are **strictly inaccessible and prohibited**:

1. **Production & Wallet Secrets:** No private keys, mnemonic seed phrases, exchange API keys, or trading account passwords are stored in the codebase or accessible to any API.
2. **Trading & Prediction Engines:** Machine learning inference, backtesting with real capital, live order dispatching, and pattern discovery routines are stubbed with `NOT_IMPLEMENTED`.
3. **Write Access to System State:** The AI Contractor has read-only access to state and schemas; direct modification of `system_state` is guarded.
4. **Synthetic Data Prohibition:** No artificial or fake market quotes are injected into the database. All data structures reflect real system telemetry.
