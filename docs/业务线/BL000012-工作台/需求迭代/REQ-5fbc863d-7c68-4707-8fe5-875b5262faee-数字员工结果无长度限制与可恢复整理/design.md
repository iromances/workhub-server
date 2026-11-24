# Design：数字员工结果无长度限制与可恢复整理

需求编码：REQ-5fbc863d-7c68-4707-8fe5-875b5262faee
需求名称：数字员工结果无长度限制与可恢复整理
唯一目录：docs/业务线/BL000012-工作台/需求迭代/REQ-5fbc863d-7c68-4707-8fe5-875b5262faee-数字员工结果无长度限制与可恢复整理
业务线：BL000012 工作台；仓库：workhub-server；环境：本地开发；分支：codex/development-20261102。

对应 Proposal v1；按用户本轮确认范围实施。

## 流程与契约
ZCode 数字员工 turn.completed → 同步保存脱敏 CLI_RAW_RESULT → 程序移除纯 JSON 围栏并校验 → 必要时无工具 workspace/generateText → CLI_RESULT → 原验收与报告。
数字员工不设置 maxOutputTokens，不设报告字节、字符或数组条数上限；模型仍有物理上限，length 时通过消息前缀续写，最多三次格式请求，耗尽后保留原文报错。普通需求 AI 会话保持现有边界。
模型选择保持有效模型，格式请求独立选择已核实支持的较低档位；无法证明支持时沿用兼容档位并记录限制，不猜参数。
CLI_RAW_RESULT 保存 text 与 inputThroughOrdinal；用业务输入快照序号判断是否有新指令（不能用原文落盘时间判断）。CLI_RAW_RESULT 使用既有 LONGTEXT 事件存储，无 DDL；结果脱敏与工具输出截断分开。CLI_RESULT 和最终报告完整脱敏保存。
现有重试创建新运行：只在规格与配置完全一致、原运行无活动句柄且原文后没有人工新输入时复制恢复原文。适配器发现恢复原文后，只发格式请求；新指令仍走既有重规划语义。
格式失败保留 FAILED 状态并给出“结果整理失败，可重试恢复”的说明，避免新增状态导致前端不兼容。原运行证据不删除；交付仍独立核验，不仅凭原文宣称完成。

## 安全、验证与交付
原文持久化保留脱敏，协议结构/身份/顺序校验、取消、租约、进程树停止及业务验收不放松。协议传输不以报告长度截断；时间预算仍生效。
离线测试，不调用真实模型/业务工具；真实模型续接行为和 ELI5 质量需运行后验收。无需迁移；回滚代码后事件保留，旧版本忽略新增事件。
更新工作台现状与迭代日志，区分代码完成与运行环境生效。

## 实施落点

- `AgentResultFormatter`：数字员工专用无工具格式协议，移除固定 token 限额；长度中断续接/格式失败重整最多 3 次，失败不生成成功结果。
- `ZCodeTaskConversation`：数字员工分流、保存回调及目录支持的低档位选择；`CliTaskConversation` 标记数字员工长协议消息，普通场景不改变。
- `AgentNativeCliAdapter`：无长度 Schema/文件/summary 校验，ELI5 提示，保存原文与输入序号，恢复入口。
- `AgentToolData.sanitizeReport`、`AgentRunStore`：全文脱敏持久化；工具输出分页截断仍保留。
- `AgentWorkbenchService.retry`：规格配置未变且没有新指令时复制 CLI_FORMAT_RECOVERY，原运行证据留存；新运行按原验收规则确认完成。
