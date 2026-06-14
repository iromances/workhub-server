# WorkHub MCP 能力清单

本文档维护 WorkHub 当前暴露给 AI 客户端的 MCP 只读能力。新增、修改或删除 MCP 工具时，应同步更新本文档。MCP 安全要求见 `docs/规则与约定/研发要求与约定.md`。

## 安全原则

见 `docs/规则与约定/研发要求与约定.md` 的“MCP 和生产资源安全要求”。

## 工具

### `list_mcp_targets`

返回业务线、环境、数据库目标、服务器目标、知识库配置摘要和 GitLab 摘要。

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
- GitLab group 和本地代码缓存路径规则
- 知识库根地址和业务线目录约定
- 已绑定数据库目标
- 已绑定服务器目标

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
- `localRoot`：本地缓存根目录
- `repositoryCount`
- `repositories`：仓库 URL 和本地路径列表

安全边界：

- GitLab Access Token 只在 WorkHub 服务端内部使用，不通过 MCP catalog 或工具结果返回。
- 工具只执行受控的 `git clone`、`git fetch`、`remote set-url`、`remote set-head`、`reset --hard origin/HEAD` 和 `clean -fdx`，缓存目录由 WorkHub 按业务线 group 生成。
- 工具用于代码只读分析前的本地缓存同步，不用于提交、推送、改分支或修改远端仓库。

### `search_intakes`

按审批编号和/或需求名称搜索需求管理记录，返回匹配摘要。

必填规则：

- `approvalCode` 和 `requirementName` 至少填写一个。

可选参数：

- `approvalCode`
- `requirementName`
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

### `run_readonly_query`

执行受控只读 SQL 查询。

必填参数：

- `targetKey`
- `profileKey`
- `sql`

### `get_service_status`

读取白名单服务状态。

必填参数：

- `targetKey`
- `profileKey`
- `service`

### `read_service_logs`

读取白名单服务日志或白名单日志路径。

必填参数：

- `targetKey`
- `profileKey`

可选参数：

- `service`
- `logPath`
- `lines`

## 资源来源

- 业务线：`pm_business_line`
- 业务线系统清单：`pm_project_involved_system` 中 `system_scope = BUSINESS_LINE`
- 全局中台系统清单：`pm_project_involved_system` 中 `system_scope = MIDDLE_PLATFORM`
- 数据库和服务器目标：`mcp_resource_config` 与 `mcp_resource_business_line`
- 需求管理记录：`pm_intake_record` 及需求详情服务聚合的附件、待办、历史、研发评估和关联工作项
- 知识库路径：系统配置 `knowledge.project.vaultPath`
- GitLab 摘要：系统配置 `gitlab.global.webApiUrl`、`gitlab.global.sshHost`、`gitlab.global.accessToken`；`accessToken` 只用于服务端内部加载，MCP 只返回 `accessTokenConfigured`
- GitLab 代码缓存：业务线 `gitlabGroupName`、系统配置 `gitlab.global.webApiUrl` 和 `gitlab.global.accessToken`
