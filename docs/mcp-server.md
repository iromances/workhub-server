# WorkHub MCP 能力总览

WorkHub 通过 `workhub-mcp-server` 模块向 AI 客户端提供受控的运维与诊断能力：目标发现、业务线上下文、只读 SQL、服务状态与日志、服务器目录与文件读取，以及需求、GitLab 同步与系统集成（禅道/Jenkins 只读查询），另有数字员工企微群机器人 markdown 通知（`send_wecom_robot_markdown`，唯一外部消息出口）。所有工具调用经独立 Token 鉴权、策略守卫与审计；本文描述源码已实现的能力，运行环境是否加载以发布后的 `list_mcp_targets` 为准。

模块入口：

- `workhub-mcp-server`：[McpToolRegistry](../workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/tool/McpToolRegistry.java)（工具注册）、`security/`（SQL、命令与文件策略及脱敏）、`db/`（数据库诊断与连接复用）、`ssh/`（连接与堡垒机隧道）、`audit/`（审计）
- `workhub-controller`：[McpRuntimeController](../workhub-controller/src/main/java/cn/aslight/workhub/controller/mcp/McpRuntimeController.java)（运行时端点）、[McpResourceController](../workhub-controller/src/main/java/cn/aslight/workhub/controller/mcp/McpResourceController.java)（资源维护）、[McpAccessTokenAuthenticationFilter](../workhub-controller/src/main/java/cn/aslight/workhub/security/McpAccessTokenAuthenticationFilter.java)
- `workhub-service`：`McpRuntimeService`（协议处理）、`McpResourceService`（资源与审计查询）

## 接入方式

- 端点：`POST /api/mcp/runtime`，JSON 请求与响应；`GET` 返回 405；携带 `Origin` 头的请求直接 403，禁止浏览器内直连。
- 协议方法：`initialize`、`tools/list`、`tools/call`；其余方法返回不支持错误。
- 鉴权：独立 Bearer Token。`/api/mcp/runtime` 要求 `ROLE_MCP`，该角色仅由 `McpAccessTokenAuthenticationFilter` 按配置 Token（常量时间比较）授予；资源维护等其余 `/api/mcp/**` 接口走常规登录认证与 MCP 资源维护权限。
- 配置：`workhub.mcp.access-token`（环境变量 `WORKHUB_MCP_ACCESS_TOKEN` 优先）为运行时 Token；未配置时无客户端能获得 `ROLE_MCP`。
- 数字员工（2026-11-10 起）：CLI 渠道运行时也经此入口访问——`AgentNativeCliAdapter` 向 Codex/ZCode 注入本端点与同一 Token（地址默认 `http://127.0.0.1:{server.port}/api/mcp/runtime`，可用 `workhub.agent.cli-mcp-endpoint` 覆盖；未配置 Token 时 CLI 无 MCP 运行），AI 依据职责描述自行选择工具，ZCode 会话允许清单为 `McpRuntimeService.toolNames()` 全量；工具调用计入本文件所述审计与策略守卫。

## 工具清单

实际 `targetKey`、`profileKey` 以 `list_mcp_targets` 返回为准；可选数值参数按现有契约使用整数字符串，非法、零或负数拒绝，超出上限收敛。

| 工具 | 必填参数 | 可选参数 | 说明 |
| --- | --- | --- | --- |
| `list_mcp_targets` | 无 | 无 | 业务线、环境、数据库/服务器目标、知识库与 GitLab 摘要 |
| `get_business_line_context` | businessLine | 无 | 业务线的 GitLab 组、代码工作区根、知识库路径、访问地址（含已配置的登录凭据）与可见数据库/服务器目标 |
| `get_knowledge_context` | businessLine | 无 | 知识库位置与路径约定（业务线 wiki 根与原始材料根） |
| `run_readonly_query` | targetKey、profileKey、sql | 无 | 守卫的只读 SQL，边界见下文 |
| `get_service_status` | targetKey、profileKey、service | 无 | 服务白名单内服务的状态 |
| `read_service_logs` | targetKey、profileKey | service、logPath、lines | 按服务名（服务白名单）或按路径（目录白名单）尾读日志 |
| `search_service_logs` | targetKey、profileKey、logPath、keyword | maxMatches | 固定字符串检索，支持 ZIP 流式读取 |
| `list_server_directory` | targetKey、profileKey、path | maxDepth、maxEntries | 白名单目录内浏览，深度与条数受限 |
| `read_server_file` | targetKey、profileKey、path | lines、maxBytes | 白名单目录内读取 UTF-8 文本或启动脚本，内容脱敏 |
| `search_zentao_bugs` | 无 | productId、executionId、keyword、limit | 只读检索禅道缺陷；指定产品/迭代走作用域端点（缺失时回退全局列表按字段过滤），keyword 对标题/关键词/ID 包含匹配；默认 20 条、上限 50，最多扫描 5 页 |
| `get_zentao_bug` | bugId | 无 | 只读查询单个禅道缺陷详情 |
| `get_zentao_story` | storyId | 无 | 只读查询单个禅道需求详情 |
| `get_zentao_task` | taskId | 无 | 只读查询单个禅道任务详情 |
| `get_zentao_execution` | executionId | 无 | 只读查询单个禅道迭代详情（含所属项目/产品） |
| `list_jenkins_jobs` | 无 | instanceId、folder、limit | 只读列出 Jenkins 实例根目录或 folder 下 Job；instanceId 缺省取首个启用实例；默认 50、上限 200 |
| `get_jenkins_job` | jobPath | instanceId | 只读查询 Job 详情（可构建性、健康度、最近构建、参数清单）；jobPath 形如 `folder/job` |
| `get_jenkins_build` | jobPath、buildNumber | instanceId | 只读查询构建状态、参数与耗时 |
| `get_jenkins_build_log` | jobPath、buildNumber | instanceId、lines、maxBytes | 只读尾读构建控制台日志；默认 200 行/64 KiB，上限 2000 行/256 KiB |
| `send_wecom_robot_markdown` | content | 无 | 向系统配置的企微群机器人发送一条 markdown 消息；content 为 UTF-8 1–4096 字节；webhook 服务端配置，AI 不提供，边界见下文 |

目录与文件读取的详细边界见下文[文件与目录读取白名单](#文件与目录读取白名单)。

禅道/Jenkins 工具（REQ-0051c0a8）的地址与凭据取自系统集成配置（`zentao.base` 组与 `agent.jenkins/policy` 实例凭据组，加密存储），AI 不能提供地址、凭据或触发参数；全部工具只读，不创建禅道对象、不触发 Jenkins 构建——禅道建测试单仍走提测链路，Jenkins 构建仍走数字员工审批链路。查询结果经字段长度截断（单字段 4000 字符）与深度收敛；每次调用写入 MCP 审计（工具、参数摘要、结果状态、耗时），不记录返回数据与凭据。配置入口：系统集成页凭据表单（`GET/PUT /api/system/integrations/{id}/config`，凭据以"已配置"布尔表达，不回显明文；password/apiToken 留空表示不修改）。

企微机器人工具（REQ-47f4429c）是数字员工唯一的外部消息出口：webhook 地址存于加密系统配置 `wecom.agent.webhook`（SECRET 类型，管理员经系统配置维护，接口只回显掩码），AI 不能提供或修改 webhook；未配置或停用时工具返回明确错误不发送。单次调用发送一条 markdown 消息，content 以 UTF-8 计 1–4096 字节，空或超限拒绝；企微返回的 errcode/errmsg 原样透传，HTTP 失败与中断返回 `sent=false` 不抛异常；工具不重试，超出企微机器人频控（约 20 条/分钟）按企微 errmsg 返回。每次调用写入 MCP 审计（内容字节数、errcode、结果、耗时），不记录内容全文与 webhook。

## 资源与目标模型

- 资源分 DATABASE 与 SERVER 两类，均可绑定单个或多个业务线；`publicResource` 目标对所有业务线上下文可见。每个资源可配置多个 profile（连接方式），工具按 `targetKey` + `profileKey` 调用。
- SERVER 资源的 `allowedServices` 控制按服务名的状态与 journalctl 查询，`allowedLogPaths` 控制按路径的日志尾读、检索、目录浏览与文件读取；两份白名单相互独立。
- 环境编码统一大写；非公共资源的环境必须在所属业务线支持环境内（见[现状 · 环境编码](业务线/BL000012-工作台/现状.md)）。
- 连接复用：SSH 连接由 `SshConnectionManager` 管理并支持堡垒机隧道（`SshTunnel`）；数据库连接经 `JdbcConnectionLeaseCache` 复用，超时与上限取 profile 配置。

## 安全边界

- SQL：`SqlPolicyGuard` 只允许 `SELECT`/`WITH`/`SHOW`/`DESC`/`DESCRIBE`/`EXPLAIN` 开头；拒绝注释、多语句和高风险关键字（insert、update、delete、drop、alter、set、use 等）；`SELECT`/`WITH` 未带 `LIMIT` 时自动追加。应用层守卫只是防御纵深，数据库账号权限必须在 MySQL 侧收敛为只读。
- 命令：`CommandPolicyGuard` 仅放行白名单固定程序（状态查询、journalctl、tail、grep 等），SSH 不接受任意 shell；远端不安装脚本、不执行被读取的业务脚本。
- 文件：见下文[文件与目录读取白名单](#文件与目录读取白名单)——白名单目录、逐级 O_NOFOLLOW 打开、拒绝软链接与二进制、敏感内容脱敏。
- 凭据：数据库与服务器凭据以 `workhub.mcp.master-key` 加密存储，由 `SecretResolver` 解密；内容中的常见密码、token 与认证信息输出前脱敏。业务线访问地址可配置登录账号与密码（`pm_business_line_access_config.login_password_encrypted`，同一主密钥加密），管理端接口只返回 `loginPasswordConfigured` 布尔不回显明文；`get_business_line_context`、`get_business_line_test_context`、`get_requirement_test_context` 的 `accessEndpoints` 在已配置时输出解密后的 `loginUsername`/`loginPassword`，仅用于 AI 在浏览器自动测试中登录该地址，不参与查询/日志结果的脱敏豁免。
- 审计：`McpAuditLogger` 以 JSONL 追加记录每次工具调用的操作、目标、路径、限制与退出结果，不记录文件内容与查询数据；默认 `logs/mcp-audit.jsonl`，可用环境变量 `WORKHUB_MCP_AUDIT_LOG` 调整。页面 `GET /api/mcp/audits` 读取同一文件分页展示。

## 文件与目录读取白名单

对应 REQ-da3c89c7 v2（见文末相关需求）；本节配置与契约源码已实现，迁移和应用发布尚未执行。

### 一份配置

MCP 页面原“日志目录白名单”已改为“允许 AI 读取的白名单目录”，日志文件尾读、日志检索、目录浏览和文本/启动脚本读取共用。接口复用历史 `allowedLogPaths` 字段及 `allowed_log_paths_json` 列，不增加独立文件字段。

- 全部服务器当前统一默认 `/mnt`。服务器新建表单与后端创建默认一致；更新省略字段保留已有配置，显式 `[]` 禁止所有按路径读取。清空不再表示无限制。
- `/mnt` 范围外的其他目录必须先得到用户明确授权；AI 不得自行扩展或把报错路径加入白名单。页面对新增且不被原授权目录或 `/mnt` 覆盖的目录显示“明确授权并保存”，取消不发送保存请求。保存锁定已确认的请求范围并阻止重复提交。
- 修改白名单沿用现有 MCP 资源维护权限；MCP 读取工具不提供配置写入。通过资源保存 API 添加其他目录同样需要明确用户授权。
- 按服务名查询状态和 journalctl 仍由原服务白名单控制；本目录配置控制明确传入文件路径的读取。

```json
{"allowedLogPaths": ["/mnt"]}
```

页面可以直接维护这份配置；使用 `PUT /api/mcp/resources/{id}` 时应保留资源其他现有字段，不能只提交以上片段。

### 存量默认值与发布

迁移脚本：`workhub-bootstrap/src/main/resources/db/schema/mysql/V20261103_193242__unify_mcp_ai_read_paths_default_mnt.sql`。

脚本先将所有 SERVER 资源的 id、target_key、旧 allowed_log_paths_json 备份到 `bak_mcp_ai_read_paths_20261103_193242`，不复制凭据；随后把全部 SERVER 的目录统一为 `["/mnt"]`，数据库资源不变。旧的其他目录不会自动保留，后续确需读取时由用户明确授权后重新添加。此前尚未发布的独立列迁移已移除，本版不新增目录字段。

先执行该版本迁移并发布后端、前端，再刷新 MCP 工具列表，通过 `list_mcp_targets` 或业务线测试上下文确认目标环境、资源、profile 及 `allowedLogPaths`。本节不代表已修改运行环境。

### 工具与读取边界

| 工具 | 参数 | 输出及限制 |
| --- | --- | --- |
| `list_server_directory` | targetKey、profileKey、path；可选 maxDepth/maxEntries | 默认深度 1、最大 3；条数受 profile 限制、最大 1000；返回 entries（path/type/restricted/sizeBytes/modifiedAt）与 truncated |
| `read_server_file` | targetKey、profileKey、path；可选 lines/maxBytes | 行数取 profile、最大 2000；字节默认/最大 262144；返回 content、sizeBytes、truncated、dataMasked |
| `read_service_logs`（logPath） | 原有日志参数 | 同一目录白名单；默认行数和超时取 profile；按验证过的描述符尾读 |
| `search_service_logs` | 原有日志参数 | 固定字符串检索；同一目录白名单；ZIP 按描述符流式读取，条目最多 1000、单行最多 262144 字节，不解压写盘 |

可选数值按现有 MCP 契约使用整数字符串，非法、零或负数拒绝；超出上限收敛。目录截断或文件 truncated=true 时不能认定已读取全部内容。明文检索无匹配保留退出码 1 与空输出。

所有路径必须在已授权目录内。固定 Python 3 读取程序按目录描述符逐级 O_NOFOLLOW 打开，日志文件也不能通过软链接越界；软链接仅可在目录中展示元数据。拒绝特殊文件、常见敏感凭据目录、私钥和密钥库；文本接口只支持 UTF-8，拒绝二进制和控制字符。内容中的常见密码、token、认证信息及跨行敏感值脱敏，未知自定义格式仍需依赖最小目录授权。

目标服务器须已有 Python 3；尾读与明文检索使用已有 tail/grep，ZIP 使用 Python 标准库。不上传、不安装远端脚本，不执行被读取的业务脚本。SSH 只接收固定程序和经引用的参数，不接受任意 shell。复用 profile 超时，输出持续排空且最多保留 2 MiB。审计记录操作、资源、路径、限制和退出结果，不记录文件内容。

### 调用示例

实际 targetKey 以 `list_mcp_targets` 为准，文件路径以目录查询结果为准。

```json
{
  "name": "list_server_directory",
  "arguments": {
    "targetKey": "bl000006-test-server-10-10-116-19-22",
    "profileKey": "diagnostic",
    "path": "/mnt/etc-server",
    "maxDepth": "3",
    "maxEntries": "500"
  }
}
```

```json
{
  "name": "read_server_file",
  "arguments": {
    "targetKey": "bl000006-test-server-10-10-116-19-22",
    "profileKey": "diagnostic",
    "path": "/mnt/etc-server/<应用目录>/start.sh",
    "lines": "300",
    "maxBytes": "65536"
  }
}
```

`--spring.profiles.active=sit` 或 `-Dspring.profiles.active=sit` 只证明脚本内容，运行时实际 profile 仍需结合变量来源、脚本调用参数和启动日志确认。

### 关闭与回滚

保存 `allowedLogPaths=[]` 关闭按路径读取。回退应用时可保留 `/mnt` 配置；恢复备份中的其他目录属于扩权，必须逐项取得用户明确授权，不自动整表恢复。只读工具从不重启应用或执行启动脚本。

## 资源维护接口（页面用）

配置变更不通过 MCP 工具开放，仅由具备 MCP 资源维护权限的登录用户经页面接口操作：

- 堡垒机：`GET/POST /api/mcp/bastions`、`PUT /api/mcp/bastions/{id}`、`POST /api/mcp/bastions/test-connection`
- 资源：`GET/POST /api/mcp/resources`、`PUT/DELETE /api/mcp/resources/{id}`、`POST /api/mcp/resources/test-database-connection`、`POST /api/mcp/resources/test-server-connection`
- 目录与审计：`GET /api/mcp/catalog`、`GET /api/mcp/audits`

## 配置项

| 配置 | 说明 |
| --- | --- |
| `workhub.mcp.access-token`（env `WORKHUB_MCP_ACCESS_TOKEN`） | 运行时 Bearer Token；未配置时运行端点实际不可用 |
| `workhub.mcp.master-key`（env `WORKHUB_MCP_MASTER_KEY`） | 资源凭据加密主密钥 |
| `WORKHUB_MCP_AUDIT_LOG` | 审计文件路径，默认 `logs/mcp-audit.jsonl` |

## 相关需求

- [REQ-da3c89c7 · MCP服务器目录与文本文件受控读取](业务线/BL000012-工作台/需求迭代/REQ-da3c89c7-3acc-4a12-9140-b97359d2ea52-MCP服务器目录与文本文件受控读取/tasks.md)
- [REQ-718a4a66 · 堡垒机共享连接按配置隔离](业务线/BL000012-工作台/需求迭代/REQ-718a4a66-bdd6-492d-9fe5-c771ca94b2a3-堡垒机共享连接按配置隔离/proposal.md)
- [REQ-0051c0a8 · 禅道Jenkins连接配置与MCP按需查询](业务线/BL000012-工作台/需求迭代/REQ-0051c0a8-bfb5-4f4f-86b5-51e8ca12947e-禅道Jenkins连接配置与MCP按需查询/proposal.md)
- [REQ-47f4429c · 数字员工企微机器人巡检推送](业务线/BL000012-工作台/需求迭代/REQ-47f4429c-edfd-4572-86e9-cb98647c86d2-数字员工企微机器人巡检推送/proposal.md)
