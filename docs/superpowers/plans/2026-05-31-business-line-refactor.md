# Business Line Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the GitLab-bound grouping concept from "project group" to "business line" and remove duplicated business-line fields from projects.

**Architecture:** `pm_project_group` becomes `pm_business_line`; `group_name` becomes `business_line_name`; `gitlab_group_name` remains the GitLab namespace and also acts as business-line code. Projects keep a single `business_line` reference. Existing project-group APIs and models are renamed to business-line APIs and models.

**Tech Stack:** Java 25, Spring Boot 4, MyBatis annotations, MySQL schema SQL, JUnit/Maven compile verification.

---

### Task 1: Lock New Project Contract

**Files:**
- Modify: `workhub-model/src/main/java/cn/aslight/workhub/model/project/ProjectSaveRequest.java`
- Modify: `workhub-model/src/main/java/cn/aslight/workhub/model/project/ProjectSummaryResponse.java`
- Modify: `workhub-model/src/main/java/cn/aslight/workhub/model/project/ProjectDetailResponse.java`

- [ ] Remove `businessLineCode` and `businessLineName` from project save and response models.
- [ ] Keep one `businessLine` field on project models as the foreign-name reference to business-line master data.

### Task 2: Rename Project Group Domain To Business Line

**Files:**
- Rename/create: business-line model classes under `workhub-model/src/main/java/cn/aslight/workhub/model/project/`
- Rename/create: business-line mapper under `workhub-dao/src/main/java/cn/aslight/workhub/dao/project/`
- Modify: `workhub-service/src/main/java/cn/aslight/workhub/service/project/ProjectService.java`
- Modify: `workhub-controller/src/main/java/cn/aslight/workhub/controller/project/ProjectController.java`

- [ ] Replace project-group API and service methods with business-line equivalents.
- [ ] Preserve `gitlabGroupName` as the GitLab namespace and business-line code source.

### Task 3: Update SQL And Runtime Patches

**Files:**
- Modify: `workhub-bootstrap/src/main/resources/db/schema/mysql/V1__init.sql`
- Modify: `workhub-bootstrap/src/main/java/cn/aslight/workhub/config/ProjectSchemaPatchRunner.java`
- Modify: affected MyBatis SQL strings.

- [ ] Replace `pm_business_line` with `pm_business_line`.
- [ ] Replace `pm_project.business_line` with `pm_project.business_line`.
- [ ] Remove `pm_project.business_line_code` and `pm_project.business_line_name`.
- [ ] Replace project-group member/system columns with `business_line`.

### Task 4: Propagate Business-Line Naming Through Development Intake

**Files:**
- Modify intake analysis, clarification, user option, and knowledge-base services/mappers.

- [ ] Rename database references and method names from project group to business line where they refer to the GitLab/code-analysis boundary.
- [ ] Keep structured incoming demand `businessLine` extraction intact.

### Task 5: Update Docs And Verify

**Files:**
- Modify: `README.md`
- Modify: `docs/project-charter.md`
- Modify: `docs/backend-architecture.md`
- Modify: `docs/implementation-roadmap.md`
- Modify: `docs/controller-api.md`

- [ ] Update business wording from project group to business line.
- [ ] Run `mvn -q -DskipTests compile`.
- [ ] Run focused tests when compile errors indicate contract drift.
