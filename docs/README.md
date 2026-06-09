# WorkHub 文档索引

本目录沉淀 WorkHub 的业务边界、架构约束、实施路线、接口说明和研发方案规范。后续 AI 与人工开发应优先通过本索引定位规则来源。

## 核心入口

- `project-charter.md`：业务范围、对象模型、生命周期基线和防飘逸约束。
- `backend-architecture.md`：后端模块边界、包结构、数据模型、API 分组和集成原则。
- `implementation-roadmap.md`：当前阶段目标、里程碑、开发顺序和验收基线。
- `controller-api.md`：Controller 层接口说明。
- `研发设计规范.md`：中等及以上研发需求启动开发前的设计方案输出规范。

## 使用指引

- 做功能判断时，先读 `project-charter.md` 和 `implementation-roadmap.md`，确认需求仍在当前范围和阶段内。
- 涉及后端结构、模块归属、数据模型或接口分组时，对照 `backend-architecture.md`。
- 涉及新增或变更 API 时，同步检查 `controller-api.md` 是否需要更新。
- 涉及新功能、页面改造、接口改造、数据库结构变更、数据修复、定时任务、批处理或外部接口改造时，先按 `研发设计规范.md` 输出方案；方案审核通过前，不修改代码、不执行会改变项目状态的命令。
- 如果文档与代码不一致，先判断代码是否只是骨架；准备改变既有业务规则、生命周期、对象模型或全局交互规则时，先更新对应文档。

## 专题文档

- `elk-monitoring.md`：本地 ELK、Filebeat、Kibana 看板和日志监控说明。
- `intake-ai-test-case-tech-design.md`：需求 AI 测试用例相关技术设计。
- `intake-ai-test-case-tab-demo.html`：需求 AI 测试用例页签交互演示。
- `intake-test-review-tech-design.md`：需求提测后测试复盘、禅道 bug 读取和自动化漏测分析技术方案。
- `superpowers/`：历史方案、计划和执行记录归档。
