# WorkHub MCP 能力与安全边界

本文档维护 WorkHub 当前暴露给 AI 客户端的 MCP 只读能力、资源来源和安全边界。新增、修改或删除 MCP 工具，或调整 MCP 生产资源访问边界时，应同步更新本文档。

## 安全边界

- MCP 不向 AI 返回数据库密码、GitLab Access Token、SSH 密码、私钥或堡垒机凭据。
- 需要凭据时，由 WorkHub 服务端从已加密配置中加载并在服务端内部使用。
- 数据库能力只允许受控只读查询，禁止写入、DDL、多语句、导出、高风险函数和无边界全表扫描。
- 数据库查询结果必须在 MCP 服务端出口统一脱敏，不依赖 AI 提示词或调用方自行处理；至少覆盖手机号、姓名、证件号、银行卡号、邮箱、地址、生日、IP、车牌、密码、密钥和 Token。脱敏识别同时使用查询结果列标签、数据库真实列名和高置信度值格式，返回结果、回显 SQL 和审计日志均不得包含已识别的敏感原文。
- 服务器能力只允许服务状态、服务日志和日志文件读取；日志路径白名单必须使用绝对日志目录，可配置多个目录并读取各目录下不同服务的具体日志文件；路径必须规范化并防止目录越界与 shell 注入，白名单为空表示对应维度不限制。
- 生产日志排查首选受控 Elasticsearch 查询，服务器 SSH 日志作为补充。Elasticsearch 工具不接受索引名、Query DSL、正则表达式或任意字段，只能使用已启用的“业务线 + 环境”日志范围。
- MCP runtime 使用独立 Bearer Token 鉴权，令牌通过 `WORKHUB_MCP_ACCESS_TOKEN` 或本机忽略提交的配置提供，不复用普通用户 JWT 或 MCP 凭据加密主密钥。
- MCP runtime 不暴露任意 SSH 命令。
- 需求管理 MCP 能力只允许读取和搜索，不提供写入、阶段推进、状态变更或附件文件正文读取。
- 知识库 MCP 定位能力只返回 MCP 项目配置中的知识库根地址 `projectVaultPath` 和路径结构；如新增知识库文件正文读取能力，必须限制在该根目录下，并限制文件类型、文件大小和路径穿越。
- 对生产问题，默认只做诊断和只读核查；任何修复、重启、数据修正、DDL/DML、配置变更都必须等用户明确确认。

## 运行时与鉴权

HTTP runtime 使用 Streamable HTTP MCP，端点为 `/api/mcp/runtime`，支持稳定协议版本 `2025-11-25`、`2025-06-18`、`2025-03-26`，并兼容 Codex 当前包含的 `2026-07-28` 预发布协议标识。访问端点必须携带独立 Bearer Token；服务端配置项为 `workhub.mcp.access-token`，生产和共享环境通过 `WORKHUB_MCP_ACCESS_TOKEN` 注入。

## 工具

### `list_mcp_targets`

返回业务线、环境、数据库目标、服务器目标、知识库配置摘要和 GitLab 摘要。数据库目标和服务器目标均包含 `publicResource`、`featureTags` 和 `systemName/systemNames`；公共资源不绑定业务线并可被所有业务线上下文识别，`featureTags` 描述“禅道、合同系统、支付”等用途特征。

业务线摘要包含：

- `code`
- `name`
- `gitlabGroupName`
- `involvedSystems`
- `globalSystems`
- `enabled`

GitLab 摘要只返回：

- `webApiUrl`
- `sshHost`
- `accessTokenConfigured`

不返回 Access Token 明文或密文。

### `get_business_line_context`

按业务线名称或编码返回完整定位上下文。

返回内容包括：

- 业务线配置
- 已启用的多环境业务访问入口，位于 `accessEndpoints`
- GitLab group 和本地代码缓存路径规则
- 知识库根地址和业务线目录约定
- 已绑定数据库目标（包含系统绑定）
- 已绑定服务器目标（包含系统绑定）

每个业务访问入口返回 `environmentCode`、`endpointType`、`endpointName`、`endpointUrl`、`pathPrefix` 和 `effectiveUrl`；其中网关 `effectiveUrl` 为地址与路径前缀的规范化组合。

### `get_knowledge_context`

按业务线返回项目知识库定位上下文。知识库根地址来自 MCP 项目配置中的 `projectVaultPath`。

返回内容包括：

- `projectVaultPath`：MCP 项目配置中的项目知识库根地址
- 需求原始材料归档路径约定
- 需求 wiki 归档路径约定
- 业务线 raw/wiki 根目录

当前能力只返回定位信息，不直接读取知识库 Markdown/TXT 文件正文。

### `sync_gitlab_group_repositories`

按业务线名称或编码拉取或刷新该业务线 GitLab group 及子组下全部可访问仓库到本地受控缓存。

必填参数：

- `businessLine`

返回内容包括：

- `gitlabGroupName`
- `localRoot`：本地缓存根目录，批量同步时为 `data/git-cache/{gitlabGroupName}`
- `repositoryCount`
- `repositories`：仓库 URL 和本地路径列表；仓库目录名沿用 GitLab project `path`，`path` 为空时从仓库 URL 末段推断并去掉 `.git`

安全边界：

- GitLab Access Token 只在 WorkHub 服务端内部使用，不通过 MCP catalog 或工具结果返回。
- 工具只执行受控的 `git clone`、`git fetch`、`remote set-url`、`remote set-head`、`reset --hard origin/HEAD` 和 `clean -fdx`，缓存目录由 WorkHub 按 `data/git-cache/{gitlabGroupName}` 生成。
- 工具用于代码只读分析前的本地缓存同步，不用于提交、推送、改分支或修改远端仓库。

### `search_intakes`

按审批编号、需求名称和/或摘要关键词搜索需求管理记录，返回匹配摘要。

必填规则：

- `approvalCode`、`requirementName` 和 `keyword` 至少填写一个。

可选参数：

- `approvalCode`
- `requirementName`
- `keyword`
- `limit`：返回条数，默认 10，允许范围 1-50。

返回内容包括：

- `count`
- `limit`
- `items`：需求摘要列表，包含 intakeId、审批编号、需求名称、需求类型、业务线、需求状态、提出人、研发负责人、涉及系统、接收时间等摘要字段。

### `get_intake_detail`

按 intakeId 读取单条需求管理详情。

必填参数：

- `intakeId`

返回内容包括：

- 需求基础信息和原始内容
- 结构化字段
- 需求状态、enrichment 状态和暂停信息
- 涉及系统、关联正式工作项、需求待办和修改历史
- 附件元信息和下载 URL

该工具不记录“查看需求详情”历史，不读取附件文件正文，不执行任何需求写入动作。

### `get_requirement_test_context`

按 `intakeId`、审批编号、需求名称或摘要关键词定位需求，并聚合返回需求关联业务线、研发分支、涉及系统、指定环境下已启用的业务访问入口、SERVER/DB 资源、GitLab 与知识库上下文。业务访问入口位于 `testContext.accessEndpoints`，返回运营端、客户端、网关或其他入口的 `endpointUrl/pathPrefix/effectiveUrl`；网关 `effectiveUrl` 为地址与路径前缀的规范化组合。未唯一定位需求时返回 `NOT_FOUND` 或 `AMBIGUOUS`，不猜测业务线。

### `get_business_line_test_context`

按业务线和可选的 `environmentCode/systemName` 返回测试所需的已启用业务访问入口、环境、SERVER/DB、GitLab、知识库和涉及系统上下文。指定 `environmentCode` 时，`testContext.accessEndpoints` 只返回该环境入口；调用方仍需在开始测试前确认环境与分支。

### `run_readonly_query`

执行受控只读 SQL 查询。

必填参数：

- `targetKey`
- `profileKey`
- `sql`

结果安全边界：

- 查询结果在 WorkHub 服务端出口统一脱敏，并返回 `dataMasked=true`。
- 脱敏同时使用结果列标签和数据库真实列名，避免通过 SQL 别名直接绕过字段识别。
- 对列名不可识别的结果，继续按手机号、身份证号、邮箱等高置信度值格式兜底脱敏。
- 返回结果中的 SQL 和 MCP 审计日志中的 SQL 同样执行脱敏；数据库实际执行仍使用原始参数，保证查询语义不变。

### `search_business_logs`

实时查询已配置的业务线 Elasticsearch 日志，是生产日志排查的首选工具；生产服务器必须经过堡垒机时，`read_service_logs` 和 `search_service_logs` 作为补充证据来源。

必填参数：

- `businessLine`：业务线编码、名称或 GitLab group 名称，由 WorkHub 解析为标准业务线编码。
- `environmentCode`：环境编码。

可选参数：

- `serviceNames`：逗号分隔服务名；`SELECTED` 关注模式只能选择已启用服务，不传时查询该范围全部启用服务。
- `levels`：逗号分隔的 `DEBUG/INFO/WARN/ERROR`；默认 `ERROR,WARN`。
- `from/to`：带时区 ISO-8601 时间；默认最近 15 分钟。包含 `INFO/DEBUG` 时跨度最多 24 小时，仅 `WARN/ERROR` 时最多 7 天。
- `phrase`：消息字段固定短语，最多 500 字符。
- `traceId/requestId`：按系统配置中的固定字段精确过滤；未配置字段时拒绝查询。
- `limit`：默认 50，允许范围 1-100。

安全边界：

- 索引来自启用的 `ops_system_alert_scope_index`，调用方不能提交索引或 DSL。
- 使用 PIT 与 `search_after` 查询，超出条数或总响应边界时返回 `truncated=true`。
- 日志正文、标题、堆栈、Trace ID 和请求 ID 在 MCP 出口统一截断并脱敏，覆盖手机号、身份证、邮箱、账号、密码、Token、Secret 和 IP 等高置信度内容；返回 `dataMasked=true`。
- 审计只记录业务线、环境、服务、级别、时间范围、数量、耗时和是否使用检索条件，不记录短语、ID 原文、日志正文、凭据或 ES 原始响应。

### `get_service_status`

读取服务状态。配置服务白名单时只允许读取白名单服务；服务白名单为空表示不限制服务名。

必填参数：

- `targetKey`
- `profileKey`
- `service`

### `read_service_logs`

读取服务日志或日志文件。服务白名单用于限制 `journalctl` 服务名；日志路径白名单配置一个或多个允许读取的绝对日志目录，`logPath` 可指向任一目录下的具体服务日志文件。路径会先规范化并校验目录边界，不允许通过 `..` 、相似目录前缀或 shell 特殊字符越界。对应白名单为空表示不限制该维度。

必填参数：

- `targetKey`
- `profileKey`

可选参数：

- `service`
- `logPath`
- `lines`

### `search_service_logs`

在单个日志文件中执行固定字符串检索。普通文本日志使用 `grep -F`；`.zip` 历史日志使用受限 `zipgrep`，关键词不得以 `-` 开头或包含正则元字符。文件必须位于 SERVER 资源允许的日志目录内。每次只允许一个明确的绝对文件路径，不支持正则表达式、通配符、目录扫描、解压写盘或任意 shell。命中数默认并最大受 SERVER profile 的 `maxOutputLines` 限制，审计记录中的检索关键词会被替换为 `[REDACTED]`。

必填参数：

- `targetKey`
- `profileKey`
- `logPath`
- `keyword`

可选参数：

- `maxMatches`

## 资源来源

- 业务线：`pm_business_line`
- 业务线系统清单：`pm_project_involved_system` 中 `system_scope = BUSINESS_LINE`
- 全局中台系统清单：`pm_project_involved_system` 中 `system_scope = MIDDLE_PLATFORM`
- 数据库和服务器目标：`mcp_resource_config` 与 `mcp_resource_business_line`
- 需求管理记录：`pm_intake_record` 及需求详情服务聚合的附件、待办、历史、研发评估和关联工作项
- 知识库路径：系统配置 `knowledge.project.vaultPath`
- GitLab 摘要：系统配置 `gitlab.global.webApiUrl`、`gitlab.global.sshHost`、`gitlab.global.accessToken`；`accessToken` 只用于服务端内部加载，MCP 只返回 `accessTokenConfigured`
- GitLab 代码缓存：业务线 `gitlabGroupName`、系统配置 `gitlab.global.webApiUrl` 和 `gitlab.global.accessToken`
- Elasticsearch 连接与字段：系统配置分组 `elk.alert`；业务线索引和服务范围：`ops_system_alert_scope`、`ops_system_alert_scope_service`、`ops_system_alert_scope_index`
