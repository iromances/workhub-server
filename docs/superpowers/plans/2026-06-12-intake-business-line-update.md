# Intake Business Line Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dedicated, audited action for correcting the business line on historical intake requirements.

**Architecture:** The backend exposes a new explicit intake action endpoint that validates the target business line against `pm_business_line`, updates only `structured_data_json.businessLine` and `projectHint`, and records intake history. The frontend adds a small edit dialog in the existing intake detail modal and refreshes current detail/list data after save.

**Tech Stack:** Java 25, Spring Boot 4, MyBatis annotation mappers, JUnit 5, Mockito, Vue 3, TypeScript, Element Plus.

---

### Task 1: Backend Service Action

**Files:**
- Create: `workhub-model/src/main/java/cn/aslight/workhub/model/intake/IntakeBusinessLineUpdateRequest.java`
- Modify: `workhub-service/src/main/java/cn/aslight/workhub/service/intake/IntakeService.java`
- Modify: `workhub-service/src/test/java/cn/aslight/workhub/domain/intake/service/IntakeServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Add tests asserting that a completed intake can change business line, preserves existing structured fields, synchronizes `projectHint`, and records history; add another test rejecting disabled or missing business lines.

- [ ] **Step 2: Run test to verify failure**

Run: `mvn -pl workhub-service -Dtest=cn.aslight.workhub.service.intake.IntakeServiceTest#updateBusinessLine_shouldPersistBusinessLineForCompletedHistoricalDemand test`

Expected: compilation fails because `IntakeBusinessLineUpdateRequest` and `updateBusinessLine` do not exist.

- [ ] **Step 3: Implement request model and service action**

Create `IntakeBusinessLineUpdateRequest` with `@NotBlank businessLine`. Inject `BusinessLineMapper` into `IntakeService`. Implement `updateBusinessLine(Long id, IntakeBusinessLineUpdateRequest request, String operatorUserName)`.

- [ ] **Step 4: Run service tests**

Run: `mvn -pl workhub-service -Dtest=cn.aslight.workhub.service.intake.IntakeServiceTest test`

Expected: PASS.

### Task 2: Backend Controller Endpoint

**Files:**
- Modify: `workhub-controller/src/main/java/cn/aslight/workhub/controller/intake/IntakeController.java`
- Modify: `workhub-controller/src/test/java/cn/aslight/workhub/controller/intake/IntakeControllerTest.java`
- Modify: `docs/controller-api.md`

- [ ] **Step 1: Write failing controller test**

Add a MockMvc test for `POST /api/intake/{id}/business-line` that verifies the controller returns updated detail and calls `intakeService.updateBusinessLine(id, request, operator)`.

- [ ] **Step 2: Run test to verify failure**

Run: `mvn -pl workhub-controller -Dtest=cn.aslight.workhub.controller.intake.IntakeControllerTest#updateBusinessLine_shouldExposeBusinessLineApi test`

Expected: FAIL with 404 or compilation failure before controller method exists.

- [ ] **Step 3: Implement endpoint and API docs**

Add `@PostMapping("/{id}/business-line")` to `IntakeController`; update `docs/controller-api.md` with request and behavior.

- [ ] **Step 4: Run controller tests**

Run: `mvn -pl workhub-controller -Dtest=cn.aslight.workhub.controller.intake.IntakeControllerTest test`

Expected: PASS.

### Task 3: Frontend Detail Editor

**Files:**
- Modify: `../workhub-web/src/api/intake.ts`
- Modify: `../workhub-web/src/types/work-item.ts`
- Modify: `../workhub-web/src/views/intake/IntakeListView.vue`

- [ ] **Step 1: Add API wrapper and type**

Add `updateIntakeBusinessLine(id, businessLine)` and `IntakeBusinessLineUpdateRequest`.

- [ ] **Step 2: Add detail dialog edit flow**

Add edit button beside the business line description item. The dialog uses enabled business line options, validates selection, calls the new API, updates `selectedDetail`, calls `syncDevelopmentForms(detail)`, and reloads the list.

- [ ] **Step 3: Build frontend**

Run from `../workhub-web`: `npm run build`

Expected: PASS.

### Task 4: Verification And Documentation

**Files:**
- Create: `docs/superpowers/test-cases/2026-06-12-intake-business-line-update-test-cases.md`

- [ ] **Step 1: Write test case document**

Record planned cases and final execution results for service, controller, compile, and frontend build.

- [ ] **Step 2: Run backend compile**

Run: `mvn -q -DskipTests compile`

Expected: PASS.

- [ ] **Step 3: Self-review changed files**

Run: `git diff -- workhub-model/src/main/java/cn/aslight/workhub/model/intake/IntakeBusinessLineUpdateRequest.java workhub-service/src/main/java/cn/aslight/workhub/service/intake/IntakeService.java workhub-controller/src/main/java/cn/aslight/workhub/controller/intake/IntakeController.java docs/controller-api.md`

Expected: diff only contains intake business line changes.
