# Tasks：研发任务评估空拆解治理

## v2 执行记录（2026-11-14）

- 后续真实验证（用户明确授权现有 GLM 服务处理真实资料）：隔离需求评估与强制格式校正均通过，正式生成器均解析为 DRAFT、2 项、12h，8 个代码文件存在；第 120 轮 -32603 未复现，确切内部原因仍不可追溯。见 [真实验证记录](验证/20261114-real-zcode/验证记录.md)。此后续结果补充下方先前“待真实验证”时点，不代表应用已重启发布或业务数据已保存。

- 确认来源：用户本会话“修复下”；后续 -32603 反馈继续同一修复。范围限工具接入及协议错误诊断，不重启、不改配置、不自动重跑业务评估。
- 已确认：第 120 轮完成了仓库工具调用，失败原始错误正文未保留；不能将本轮故障继续归为工具不可见。
- 已完成：协议错误上下文脱敏、受控进度日志、针对性验证。真实内部错误根因仍待新诊断信息，不以单元测试替代真实需求闭环。
- 2026-11-14 20:51 验证：离线 Maven 定向 51/51 通过、0 跳过（ZCodeTaskConversationTest 20、ZCodeCliLocalProtocolTest 5、ZCodeCliClientTest 7、DefaultAiGatewayClientTest 19）。真 CLI 使用已安装 ZCode、模型和 MCP 使用本机夹具，未安装依赖。新增覆盖格式校正失败上下文、凭据脱敏及长度、错误正文缺失。
- 交付边界：未重启实例、未重跑需求、未改配置；需加载本次改动后获取实际错误详情。最新运行证据为 analysisId=120、intakeId=277、PID=53419，真实故障尚未闭环。

## 1. 元信息

- 需求编码：REQ-3ad3a708-f088-4f65-95cc-e76bc1bf4aeb
- 目标系统：WorkHub Server
- Proposal / Design：v1，见 [proposal.md](</Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/docs/业务线/BL000012-工作台/需求迭代/REQ-3ad3a708-f088-4f65-95cc-e76bc1bf4aeb-研发任务评估空拆解治理/proposal.md>)、[design.md](</Users/aslight/Documents/workspace/IDEAWorkspace/workhub-server/docs/业务线/BL000012-工作台/需求迭代/REQ-3ad3a708-f088-4f65-95cc-e76bc1bf4aeb-研发任务评估空拆解治理/design.md>)
- 目标分支：server `codex/development-20261102`
- 当前状态：已实施，自动化验证通过；真实重跑待部署验证

## 2. 确认记录

| 日期 | 范围/版本 | 确认人 | 结论 |
| --- | --- | --- | --- |
| 2026-11-13 | 诊断结论（模型零工具调查交"计划"+空拆解被当成功）与三层修复方案（零工具续跑一轮 / 空拆解显式失败 / 失败摘要带根因） | 用户 | 批准实施计划（2026-11-13） |

## 3. 任务与依赖

| 任务 | 交付物 | 依赖 | 验收映射 | 状态 |
| --- | --- | --- | --- | --- |
| T-01 | `ZCodeTaskConversation`：工具计数、`currentInputId`、`requireToolInvestigation` 标志与一次性续跑 | 规格确认 | AC-01、02、03 | 已完成 |
| T-02 | `CodexCliDevelopmentAnalysisGenerator`：空拆解拒绝保存（生成/SDD/调整） | 规格确认 | AC-04 | 已完成 |
| T-03 | `DefaultAiGatewayClient.summarizeException`：根因拼接 | 规格确认 | AC-05 | 已完成 |
| T-04 | 协议与生成器测试、定向回归、SDD 文档 | T-01～03 | AC-01～05 | 已完成 |

修改范围：`ZCodeTaskConversation`、`CodexCliDevelopmentAnalysisGenerator`、`DefaultAiGatewayClient`、`ZCodeTaskConversationTest`、`CodexCliDevelopmentAnalysisGeneratorTest`。其他已有未提交修改保持原状。

## 4. 实际结果（2026-11-13）

- 定向回归 75/75：ZCodeTaskConversationTest 15（新增续跑两用例）、CodexCliDevelopmentAnalysisGeneratorTest 3（新增空拆解两用例）、DefaultAiGatewayClientTest 19、DevelopmentAnalysisServiceTest 16、IntakeSddFlowTest 15、ZCodeCliClientTest 7；模块编译与 test-compile 通过。
- 诊断数据留痕：intake 277 的 8 轮评估记录（5 轮空拆解 DRAFT：110-113、117；run 116 纯文本 FAILED；114/115 协议失败根因丢失）为运行库既有数据，不回溯修改。
- 未覆盖项（待部署）：重启本地实例后重跑 202611050003 研发任务评估的真实链路——预期产出真实任务拆解或显式 FAILED；协议失败类报错应直接可见根因。
- 系统设计更新：现状.md §11a 已补记；迭代日志已记录。
