# WorkHub MCP First Version Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone first-version MCP stdio server for read-only database and server diagnostics.

**Architecture:** Add `workhub-mcp-server` as a Maven module with a direct stdio JSON-RPC MCP implementation. Load a JSON resource catalog, enforce SQL and command policies before execution, and write JSONL audit logs.

**Tech Stack:** Java 25, Maven, Jackson, JUnit 5, JDBC, local `ssh` command.

---

### Task 1: Module and Guard Tests

**Files:**
- Modify: `pom.xml`
- Create: `workhub-mcp-server/pom.xml`
- Create: `workhub-mcp-server/src/test/java/cn/aslight/workhub/mcp/security/SqlPolicyGuardTest.java`
- Create: `workhub-mcp-server/src/test/java/cn/aslight/workhub/mcp/security/CommandPolicyGuardTest.java`

- [ ] Add the new Maven module and test dependencies.
- [ ] Write failing SQL guard tests for allowed select, auto limit, DML rejection, multi-statement rejection, and comment rejection.
- [ ] Write failing command guard tests for whitelisted services/logs and arbitrary command rejection.
- [ ] Run `mvn -pl workhub-mcp-server test` and verify tests fail because production classes do not exist.

### Task 2: Core Catalog and Policies

**Files:**
- Create: `workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/config/McpResourceCatalog.java`
- Create: `workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/security/SqlPolicyGuard.java`
- Create: `workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/security/CommandPolicyGuard.java`

- [ ] Implement immutable catalog records for business lines, environments, database targets, server targets, and profiles.
- [ ] Implement SQL normalization and read-only validation.
- [ ] Implement command whitelist validation.
- [ ] Run `mvn -pl workhub-mcp-server test` and verify the guard tests pass.

### Task 3: MCP Protocol and Tool Registry

**Files:**
- Create: `workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/WorkhubMcpServerApplication.java`
- Create: `workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/protocol/McpStdioServer.java`
- Create: `workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/tool/McpToolRegistry.java`

- [ ] Implement newline-delimited JSON-RPC handling for `initialize`, `tools/list`, `tools/call`, and notifications.
- [ ] Register first-version tool schemas.
- [ ] Return MCP text content for all tool results.
- [ ] Add a small protocol test if needed after implementation.

### Task 4: Database and Server Executors

**Files:**
- Create: `workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/db/DatabaseDiagnosticService.java`
- Create: `workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/server/ServerDiagnosticService.java`
- Create: `workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/ssh/SshTunnel.java`
- Create: `workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/audit/McpAuditLogger.java`

- [ ] Implement database metadata, explain, and limited query execution with JDBC.
- [ ] Implement optional SSH local port forwarding for database targets.
- [ ] Implement server SSH command execution using named command templates.
- [ ] Implement JSONL audit logging for success and failure.

### Task 5: Sample Configuration and Documentation

**Files:**
- Create: `workhub-mcp-server/src/main/resources/mcp-resource-catalog.example.json`
- Modify: `README.md`
- Modify: `docs/backend-architecture.md`
- Modify: `docs/implementation-roadmap.md`

- [ ] Add a sample multi-business-line, multi-environment catalog.
- [ ] Document how to start the MCP server and point an AI client at it.
- [ ] Document first-version safety boundaries.
- [ ] Run `mvn -q -DskipTests compile`.
