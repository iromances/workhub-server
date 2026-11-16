# Tasks：数字员工删除

## 1. 元信息

- 需求编码：REQ-3f9a1c7e-8d24-4b6a-9c15-7e2d84a05b31
- 需求名称与唯一需求目录：数字员工删除；`docs/业务线/BL000012-工作台/需求迭代/REQ-3f9a1c7e-8d24-4b6a-9c15-7e2d84a05b31-数字员工删除/`
- 涉及仓库与功能域：workhub-server（dao/service/controller/测试）、workhub-web（数字员工列表/api）
- Proposal 版本：v1；Design 版本：v1
- 目标环境与分支：server `codex/development-20261102`；web `codex/development-20261103`
- 任务计划状态：已确认
- 负责人/评审人：用户

## 2. 规格确认记录

- 2026-11-13：用户提出"数字员工增加删除按钮"；同日实施计划（删除语义镜像 REQ-8985f2f0 任务删除模型：物理级联 + 四项前置校验 + 审计先行 + 前端二次确认）经用户批准，即本需求 v1 规格确认。

## 3. 任务总览

| 任务 | 交付物 | 对应验收 | 状态 |
| --- | --- | --- | --- |
| T-01 | SDD 建档 | 全文 | 已完成 |
| T-02 | 后端：AgentMapper 删除查询/级联方法 + Service deleteAgent + Controller DELETE | AC-01~AC-04 | 已完成 |
| T-03 | 后端测试：级联与审计/四项拒绝/权限/版本冲突，全量 mvn test | AC-01~AC-04 | 已完成（本需求范围 0 失败） |
| T-04 | 前端：删除按钮 + api，npm run build | AC-05 | 已完成 |
| T-05 | 交付：现状.md、迭代日志.md 更新与验证证据 | AC-05 | 已完成 |

## 4. 验证记录

2026-11-13 实施完成：`AgentWorkbenchServiceTest` 新增 3 项（停用/周期/未结束运行三重拒绝与删除级联、过程数据拒绝含类别文案与级联审计、权限与版本冲突），66 项全部通过；全量 `mvn test`（根 reactor `-fn` 汇总）workhub-service 1111 项中仅 3 项既有在途改动失败（澄清提示词字节一致、知识归档路径、ELK 范围 fixture，与 REQ-0051c0a8 记录相同），本需求范围 0 失败；controller 主代码编译通过（测试编译失败为既有 agent-tool 旧测试引用已删除类）；job、mcp-server 通过；bootstrap 1 项既有 docs 路径移动错误；前端 `npm run build` 通过。证据见 `验证/20261113-Agent删除/验证说明.md`。
