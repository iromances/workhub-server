# WorkHub MCP First Version Design

## Goal

Build the first WorkHub MCP server for safe AI-assisted operations across two read-only scenarios:

- Database diagnostics through bastion access.
- Server diagnostics and log inspection through bastion access.

The first version supports multiple business lines, environments, database instances, server targets, and permission profiles. It does not provide arbitrary shell access, unrestricted SQL execution, production write access, service restarts, or configuration changes.

## Scope

In scope:

- A standalone `workhub-mcp-server` Maven module.
- MCP stdio transport for local AI clients.
- Resource catalog loaded from a local JSON file.
- Business line, environment, database target, server target, and permission profile modeling.
- Database tools:
  - `list_mcp_targets`
  - `describe_database`
  - `explain_query`
  - `run_readonly_query`
- Server tools:
  - `list_mcp_targets`
  - `get_service_status`
  - `read_service_logs`
  - `run_diagnostic_command`
- SQL read-only guardrails.
- Server command whitelist guardrails.
- JSONL audit log.
- Optional SSH local port forwarding by spawning the local `ssh` command.

Out of scope:

- Write SQL execution.
- Data export workflows.
- Service restart, deploy, rollback, or file edits.
- Approval workflow.
- Web UI management for MCP resources.
- Direct exposure of bastion credentials to AI clients.

## Architecture

The MCP server runs as an isolated Java process over stdio. It does not reuse the normal WorkHub HTTP API process because MCP stdio requires clean stdout and should not be mixed with web application logs.

```text
AI Client
  -> workhub-mcp-server stdio
      -> MCP tool registry
      -> resource catalog
      -> policy guards
      -> audit logger
      -> database client / SSH command runner
          -> bastion / MySQL / target server
```

The first version intentionally avoids Spring AI starter dependencies because the current Maven mirror may lag latest Spring AI milestone artifacts. The stdio MCP protocol is implemented directly for the core `initialize`, `tools/list`, and `tools/call` methods. The implementation remains replaceable if the project later standardizes on Spring AI MCP.

## Resource Model

Resources are selected by:

```text
businessLineCode + environmentCode + targetKey + profileKey
```

Database target:

- Business line and environment.
- JDBC host, port, schema, username, password reference.
- Optional SSH tunnel configuration.
- Profiles such as `metadata`, `readonly`, and later `export`.

Server target:

- Business line and environment.
- SSH host, port, username, identity file.
- Whitelisted services, log files, and diagnostic command keys.
- Profiles such as `status`, `logs`, and `diagnostic`.

Secrets in the JSON catalog can be literal values only for local development, but production use should reference environment variables such as `${PAYMENT_PROD_DB_PASSWORD}`.

## Database Safety

The first version only allows read-only SQL:

- `SELECT`, `WITH`, `SHOW`, `DESC`, `DESCRIBE`, and `EXPLAIN`.
- Single statement only.
- Comments are rejected.
- Dangerous keywords are rejected with word-boundary matching.
- `SELECT` and `WITH` queries must have a `LIMIT`; the server appends one when missing.
- Result rows and output bytes are capped by profile.
- Query timeout is capped by profile.

Different profiles should use different MySQL accounts. The SQL guard is a second layer, not the only security control.

## Server Safety

The first version does not accept arbitrary shell commands. The AI calls named commands only:

- `uptime`
- `disk_usage`
- `memory`
- `listening_ports`
- `service_status`
- `service_logs`
- `tail_log_file`

Service names and log paths must be in the target whitelist. Command output is truncated by profile. Errors and stderr are summarized and audited.

## Audit

Every tool call writes one JSONL entry:

- Timestamp.
- Tool name.
- Business line.
- Environment.
- Target key.
- Profile key.
- SQL fingerprint or command key.
- Status.
- Row count when applicable.
- Duration.
- Error summary when applicable.

Audit logs must not include secrets. SQL text is stored in normalized and truncated form, with a SHA-256 fingerprint.

## First Version Acceptance

- The module compiles with the root Maven build.
- Core SQL and command guards have unit tests.
- MCP `initialize`, `tools/list`, and `tools/call` work over newline-delimited JSON-RPC.
- Database tools can run against a configured MySQL target.
- Server tools can run whitelisted commands against a configured SSH target.
- All calls write audit entries.
