# Design：堡垒机共享连接按配置隔离

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-718a4a66-bdd6-492d-9fe5-c771ca94b2a3 |
| 对应规格 | [Proposal v1](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirements/workhub-server/REQ-718a4a66-bdd6-492d-9fe5-c771ca94b2a3-堡垒机共享连接按配置隔离/proposal.md>)、[Tasks v1](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirements/workhub-server/REQ-718a4a66-bdd6-492d-9fe5-c771ca94b2a3-堡垒机共享连接按配置隔离/tasks.md>)；唯一目录为当前目录 |
| 状态 | v1；2026-10-09 用户已统一确认，代码与自动化验证完成，运行验证待完成 |
| 核对上下文 | 2026-10-09；workhub-server/codex/dev-20260820；fa56c77e5c9ff407c61e48a636e9cf4ef6eee816；已有运行实例的只读证据见 Proposal |

## 1. 现状与证据

- `SshConnectionManager` 使用 `Map<Endpoint, Slot>`，Endpoint 只有 host、port、username。`acquire` 对凭据版本变化执行 `retire`；`invalidateShared` 也仅按 Endpoint 查找。
- `McpResourceService.sshTunnel` 可从资源响应得到已选 `bastionId`，但 catalog 中没有传入该 ID。
- `McpDatabaseConnectionTestService` 已取到所选 `McpBastionEntity`，传入 `McpDatabaseConnectionTester.Bastion` 时丢失 ID。
- `McpBastionService.update` 已在事务提交后使连接失效，当前传入修改前的 SSH 端点。
- 服务器连接测试及旧 standalone catalog 存在没有保存配置 ID 的内联隧道，需保持可用。
- 运行复现证明交替访问重建与同目标复用差异；云端断开原因没有完整日志，不能据此保证消除所有断连。

## 2. 连接隔离与生命周期

共享槽键改为“可空配置 ID + SSH Endpoint”。带 ID 的配置与无 ID 的内联输入自然分离；两条保存配置即使端点和凭据相同也不合并。

使用 ID 与 Endpoint 的组合，保留对同配置端点变化的区分，避免异步请求持有旧 catalog 时复用不同目标的连接。现有凭据版本比较仍在各自槽内进行。相同 ID、相同 Endpoint、相同凭据版本的目标共享主连接；目标转发继续以目标 host/port 缓存。

配置更新失效入口改为按配置 ID 处理其全部槽，在事务提交后执行。更新配置 1 不影响配置 2 或无 ID 输入。被失效的活跃 Entry 继续由原 Slot 的租约归还逻辑关闭；空闲 Entry 立即释放。失败抑制状态只清除被更新配置的槽。失效操作不得初始化管理器或主动认证。

连接获取、槽内同步、租约、四小时空闲回收、死连接重建及应用关闭沿用已有机制；此次不引入后台重连。

## 3. 契约与调用链

| 位置 | 变更 |
| --- | --- |
| `McpResourceCatalog.DatabaseTarget.SshTunnel` | 增加可空 `Long bastionId`；保留现有五参数构造方法并令 ID 为 null；旧 JSON 缺少字段时解析为 null |
| `McpResourceService.sshTunnel` | 生成引用保存配置的隧道时填入资源 `bastionId` |
| `McpDatabaseConnectionTester.Bastion` | 增加 ID 并保留现有构造调用的兼容入口；实际隧道构造传递 ID |
| `McpDatabaseConnectionTestService` | 使用所选实体 ID 构造测试输入，使保存配置的连接测试与正常查询共用该配置槽 |
| `SshConnectionManager` | 槽键加入 ID；失效接口以 ID 定向处理；连接密钥版本不输出、不持久化 |
| `McpBastionService` | afterCommit 回调传入配置 ID，保持回滚不失效、备注变化不失效 |

catalog 是已有受控内部资源契约；此次只增加可空字段，不增加用户输入项、数据库字段或公开 MCP 工具。SSH 执行命令仍使用原 Endpoint。没有配置 ID 的 legacy/内联调用保留原端点与凭据版本语义；两条 legacy 输入间的原有共享行为不在本期修复范围。

## 4. 安全、并发与容量

- 不将明文凭据、凭据摘要或进程原始输出加入日志；保留原有受控访问与审计机制。
- 同配置并发首次访问仍合并认证及转发；不同 ID 使用不同槽锁，故障抑制互不影响。
- 连接数量按实际使用的配置数量计算，即使它们使用同一端点也可同时存在。确认后运行验证须核实云端允许同用户并行连接；本次交替查询只读复现未验证云端并发会话限制。
- 更新时仅清理对应 ID 的连接，SSH 心跳不延长四小时业务空闲计时。

## 5. 验证、交付与回滚

自动化使用 FakeConnection、现有 Mockito 和事务同步回调验证，不启动 WorkHub 或真实 SSH/数据库。覆盖两配置交替访问、相同凭据不同 ID、同 ID 多目标、定向失效与活跃租约、失败隔离、凭据变化及旧 JSON 解析。

使用现有 Maven 离线编译和相关测试；测试环境问题、依赖缺失及运行实例未加载新代码须分别记录。若 Mockito 动态附加受限，使用已有 Mockito jar 的 javaagent，不安装依赖。

后端统一构建交付，无前端发布或数据库迁移。新版 catalog 与管理器一起生效后，由用户启动或明确授权重启应用，再经受控 MCP 验证两目标交替 `SELECT 1` 时各自保留连接。未完成该步骤前只能报告代码与自动化验证完成，不能报告生产保活已修复。

回滚：恢复相关代码并重新构建，由用户重启到原版本；没有数据回滚步骤。回滚会恢复端点共享行为。实施后将已核实代码事实补入 WorkHub 系统设计，迭代日志分别记录设计、自动化验证及运行验证。

## 6. 一致性与确认

Proposal 的 AC-01/02 由槽隔离承接，AC-03 由按 ID 失效承接，AC-04 由调用链与兼容构造承接，AC-05/06 由回归与交付记录承接。

用户于 2026-10-09 回复“开干”，统一确认完整 v1 规格；实施与验证证据见 [Tasks](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirements/workhub-server/REQ-718a4a66-bdd6-492d-9fe5-c771ca94b2a3-堡垒机共享连接按配置隔离/tasks.md>)。
