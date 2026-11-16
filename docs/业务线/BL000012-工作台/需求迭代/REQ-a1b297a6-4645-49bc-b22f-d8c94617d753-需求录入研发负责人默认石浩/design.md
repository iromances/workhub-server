# Design：需求录入研发负责人默认石浩

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-a1b297a6-4645-49bc-b22f-d8c94617d753 |
| 需求名称与唯一需求目录 | 同 Proposal，`docs/业务线/BL000012-工作台/需求迭代/REQ-a1b297a6-4645-49bc-b22f-d8c94617d753-需求录入研发负责人默认石浩/` |
| 涉及仓库与功能域 | workhub-web 需求录入弹窗；后端无改动 |
| 对应 Proposal 版本 | v1 |
| 状态 | v1 已确认并实施 |
| 当前事实核对环境、分支、时间 | web 本地源码 `codex/development-20261103`，2026-11-03；值口径核对自生产库只读查询 |

## 1. 现状与证据

- `workhub-web/src/views/intake/IntakeListView.vue:149-155`：`createForm` 定义 `developmentOwnerUserName: ''`。
- 同文件 `:906-915`：`openCreateDialog()` 为弹窗唯一打开入口，每次重置 `developmentOwnerUserName = ''`；录入人 `senderName` 已有默认当前用户的先例。
- 同文件 `:3144-3148`：研发负责人为自由文本 `el-input`；`:1070-1073` 提交校验非空；`:1080` 以 FormData 提交。
- 后端 `IntakeUploadRequest.developmentOwnerUserName` 为自由文本，保存至 `pm_intake_record.development_owner_user_name`，不做账号表校验。
- 值口径证据：生产库分组统计 `shihao` 140 条、“石浩” 27 条、空 31 条、其余为尹军/耿/蔡等零散值；两种口径并存均可正常流转。
- 尚未核实的假设：无。改动为单行默认值，行为可静态确认。

## 2. 方案

- 仅修改 `openCreateDialog()`：`createForm.developmentOwnerUserName` 默认置 `'石浩'`。`reactive` 初始值保持 `''` 不动，弹窗打开必经该函数，无其他入口。
- 用户可清空或改写；提交校验与提交取值逻辑不变，空值仍被拦截。
- 前端负责表单默认与输入收集；默认值是交互层便利，不属于后端业务规则，符合前后端职责边界。

| 模块 | 负责 | 不负责 | 输入/输出 | 依赖 |
| --- | --- | --- | --- | --- |
| IntakeListView 录入弹窗 | 表单默认值与提交 | 默认值持久化规则、账号口径统一 | 用户输入 → FormData | 既有 `createUploadIntake` API |

## 3. 契约与数据

- 无接口、数据库变更；`POST /api/intake/upload` 契约不变。
- 默认值口径：按用户明确指令使用字面“石浩”；生产库同时存在 `shihao`/`石浩` 两种历史值，本次不统一、不迁移。如后续需改为账号口径，仅需替换该字符串常量。

## 4. 非功能约束

不涉及权限、性能、外部依赖变化。

## 5. 交付与回滚

- 前端独立发布后生效；回滚仅需还原该单行默认值。
- 系统设计第 7 节描述后端保存与 AI 识别行为，不涉及表单默认值，本次不更新；实际进展记录迭代日志。

## 6. 一致性与待确认

- 目标 1 对应第 2 节方案；明确不做项均未被引入。
- 待确认：无（值口径以用户指令为准，替换成本一行）。
- 关联：[proposal.md](proposal.md)、[tasks.md](tasks.md)。
