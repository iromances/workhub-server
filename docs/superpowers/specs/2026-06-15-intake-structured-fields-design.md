# 需求结构化字段拆分设计

## 背景

当前 `pm_intake_record.structured_data_json` 同时承载正式需求字段、动态抽取字段和少量复杂对象。实际库中 85 条需求记录全部存在 `fields`，去重后有 367 个 `fields.label`。其中一部分是稳定业务字段，例如审批编号、需求名称、需求类型、业务线、阶段日期和工时；另一部分是附件识别、支付参数、回单信息、对账单字段等低频动态内容。

这个结构导致几个问题：

- 列表筛选、排序和统计需要反复解析 JSON，无法稳定使用索引。
- 状态推进、上线日期、业务线、工时等正式字段更新时需要重写整段 JSON，容易覆盖或丢失其他字段。
- 启动迁移 Runner 需要反复扫描和修复历史 JSON。
- 字段语义不清，代码里到处构造 `IntakeStructuredData`，维护成本高。

字段统计与推荐清单见 `docs/事实/需求fields字段拆分建议.md`。

## 目标

本次改造一次性交付，不做长期双写和分阶段切换。目标不是把所有 JSON 都消灭，而是把需求正式字段从 JSON 中彻底移出：

- `pm_intake_record` 承载需求主流程和高频查询需要的正式字段。
- `pm_intake_structured_field` 承载原始动态抽取字段，也就是现有 `fields` 的 label/value 明细。
- 附件、文档、截图类 label 不进主表，统一进入 `pm_intake_structured_field`，用 `source_type` 区分。
- AI 草稿、研发拆解草稿、澄清项草稿继续留在现有专用 JSON 字段或分析表，后续单独治理。

改造完成后，业务代码不再依赖 `structured_data_json` 维护或读取正式需求字段。该列只作为历史备份保留。

## 非目标

- 不把 367 个动态 label 全部变成 `pm_intake_record` 字段。
- 不在本次拆分 `pm_intake_development_analysis.draft_json`。
- 不在本次拆分 `pm_intake_clarification_analysis.items_json`。
- 不在本次完整重构附件存储，只处理 `fields` 中附件/文档类字段的归类。
- 不直接删除历史 `structured_data_json`，但业务代码不再从它回退读取正式字段。

## 表结构设计

### `pm_intake_record` 新增正式字段

建议新增以下字段，作为需求主数据：

| 字段 | 中文含义 | 类型建议 |
|---|---|---|
| `approval_code` | 审批编号 | `VARCHAR(64)` |
| `approval_title` | 审批标题 | `VARCHAR(255)` |
| `approval_status` | 审批状态 | `VARCHAR(32)` |
| `proposer_name` | 提出人/申请人 | `VARCHAR(128)` |
| `submitted_at` | 提交时间 | `DATETIME` |
| `requirement_type` | 需求类型 | `VARCHAR(32)` |
| `requirement_name` | 需求名称 | `VARCHAR(255)` |
| `requirement_summary` | 需求说明 | `TEXT` |
| `requirement_digest` | 需求摘要 | `VARCHAR(255)` |
| `department` | 提出部门 | `VARCHAR(128)` |
| `business_line` | 业务线名称 | `VARCHAR(128)` |
| `business_line_code` | 业务线编码 | `VARCHAR(32)` |
| `project_hint` | 项目组/项目提示 | `VARCHAR(128)` |
| `development_branch_name` | 研发分支名 | `VARCHAR(128)` |
| `zentao_url` | 禅道地址 | `VARCHAR(512)` |
| `remark` | 备注 | `TEXT` |
| `planned_due_date` | 预计完成日期 | `DATE` |
| `planned_development_start_date` | 计划开发开始日期 | `DATE` |
| `planned_testing_start_date` | 计划测试开始日期 | `DATE` |
| `planned_release_date` | 计划上线日期 | `DATE` |
| `development_started_date` | 实际开发开始日期 | `DATE` |
| `testing_started_date` | 实际测试开始日期 | `DATE` |
| `actual_completed_date` | 实际开发完成日期 | `DATE` |
| `scheduled_acceptance_date` | 计划验收日期 | `DATE` |
| `actual_testing_completed_date` | 实际测试完成日期 | `DATE` |
| `acceptance_date` | 验收日期 | `DATE` |
| `released_date` | 实际上线日期 | `DATE` |
| `closed_date` | 关闭日期 | `DATE` |
| `close_reason` | 关闭原因 | `VARCHAR(255)` |
| `estimated_effort` | 预估工时 | `VARCHAR(32)` |
| `actual_effort` | 实际开发工时 | `VARCHAR(32)` |
| `actual_testing_effort` | 实际测试工时 | `VARCHAR(32)` |
| `priority` | 优先级 | `VARCHAR(16)` |
| `urgency` | 紧急程度 | `VARCHAR(32)` |

建议索引：

- `idx_pm_intake_record_approval_code (approval_code)`
- `idx_pm_intake_record_business_line_code (business_line_code, deleted, received_at)`
- `idx_pm_intake_record_requirement_type (requirement_type, deleted, received_at)`
- `idx_pm_intake_record_released_date (released_date, deleted)`
- `idx_pm_intake_record_submitted_at (submitted_at)`

### 新增 `pm_intake_structured_field`

用于承载现有 `structured_data_json.fields` 的动态 label/value。

```sql
CREATE TABLE pm_intake_structured_field (
  id BIGINT NOT NULL AUTO_INCREMENT,
  intake_id BIGINT NOT NULL,
  field_label VARCHAR(128) NOT NULL,
  field_value TEXT NULL,
  normalized_key VARCHAR(64) NULL,
  source_type VARCHAR(32) NOT NULL DEFAULT 'STRUCTURED_FIELD',
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_pm_intake_structured_field_intake (intake_id, sort_order),
  KEY idx_pm_intake_structured_field_label (field_label),
  KEY idx_pm_intake_structured_field_normalized (normalized_key, intake_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

字段说明：

- `field_label` 保存原始 label，例如 `原扣款商户号`。
- `field_value` 保存原始 value。
- `normalized_key` 保存归一键，例如 `approval_code`、`business_line`；无法归一时为空。
- `source_type` 预留来源区分，初始为 `STRUCTURED_FIELD`。
- `sort_order` 保留原始顺序，详情展示需要按原始表单顺序还原。

## 字段归类规则

### 进入主表

稳定、跨需求高频、会被列表/筛选/状态流转/统计使用的字段进入 `pm_intake_record`。例如：

- `审批编号 -> approval_code`
- `提交时间/提出时间 -> submitted_at`
- `审批标题/标题 -> approval_title`
- `审批状态/状态 -> approval_status`
- `提交人/申请人/提报人/提出人 -> proposer_name`
- `需求名称 -> requirement_name`
- `需求简介/需求描述/需求内容 -> requirement_summary`
- `需求所属业务线/业务线/人工补充业务线 -> business_line`
- `项目组/人工补充项目组/项目提示 -> project_hint`
- `预估工时/预计工时 -> estimated_effort`
- `实际工时 -> actual_effort`
- `预估完成时间/预计完成时间 -> planned_due_date`
- `实际完成时间 -> actual_completed_date`

### 进入字段子表

动态表单和业务专属字段进入 `pm_intake_structured_field`。例如：

- `原扣款商户号`
- `新扣款商户号`
- `核心调整`
- `筛选条件`
- `字段说明`
- `展示要求`
- `清分规则`
- `代偿规则`
- `回单付款人账户`
- `对账单1编号`

### 归附件/文档扩展

文件、文档、链接、截图类字段不进入主表。本次统一进入 `pm_intake_structured_field`，但 `source_type` 标记为 `ATTACHMENT_DOC_FIELD`，避免和普通动态字段混在一起。

例如：

- `附件`
- `其他附件`
- `产品方案文档`
- `附件字段`
- `附件Sheet`
- `附件标题`
- `语雀文档链接`
- `原型地址`

## 数据迁移

迁移一次性完成：

1. 从 `structured_data_json` 回填 `pm_intake_record` 正式字段。
2. 从 `structured_data_json.fields` 写入 `pm_intake_structured_field`。
3. 迁移完成后，应用代码只读写主表正式字段和字段子表，不再读写 `structured_data_json` 中的正式字段。

回填要求：

- 日期字段统一解析常见格式：`yyyy/M/d`、`yyyy/MM/dd`、`yyyy/M/d HH:mm`、`yyyy年M月d日`。
- `无`、空串、`null` 统一视为 `NULL`。
- 工时字段使用现有 `EffortUnitNormalizer` 归一。
- `business_line_code` 通过 `pm_business_line.business_line_name` 或已有结构化数据回填。
- 同义 label 按字段清单归一到同一个正式字段。
- 迁移脚本必须幂等，重复执行不能生成重复 `pm_intake_structured_field`。

字段子表增加唯一约束，保证迁移和重跑幂等：

```sql
UNIQUE KEY uk_pm_intake_structured_field_order (intake_id, sort_order)
```

同一个需求内允许同名 label 多次出现，靠 `sort_order` 保序。

## 代码改造

### Model

- `IntakeRecordEntity` 增加主表正式字段。
- 新增 `IntakeStructuredFieldEntity`。
- `IntakeStructuredData` 保留为响应兼容模型，但逐步改为由主表字段 + 子表字段组装，不再作为主存储模型。

### DAO

- `IntakeMapper` 查询、插入、更新正式字段。
- 新增 `IntakeStructuredFieldMapper`，支持按 `intake_id` 查询、批量 upsert、删除重建。
- 列表筛选下推到 SQL，不再全部查出后用 JSON 字段过滤。

### Service

- `IntakeService.toSummaryResponse` 直接从 `IntakeRecordEntity` 取正式字段。
- `IntakeService.toDetailResponse` 用主表字段组装正式字段，用 `pm_intake_structured_field` 组装原始 `fields`。
- `createUploaded` 写入主表正式字段，同时写入字段子表。
- 业务线修改、阶段推进、上线日期、工时等更新改为直接更新主表列。
- `IntakeEnrichmentService` 合并识别结果时更新主表列和字段子表，不再重写完整 `structured_data_json`。

### 兼容

- 迁移脚本负责一次性回填现有数据。
- 应用运行时不做“主表为空再读 JSON”的正式字段回退，避免继续掩盖脏数据。
- 新写入逻辑不再把正式字段写回 `structured_data_json`。
- `structured_data_json` 保留为历史备份和排查依据，不参与正常业务读写。

## 测试策略

### 单元测试

- 字段归一：同义 label 映射到同一正式字段。
- 日期解析：覆盖常见日期格式和 `无`。
- 工时解析：覆盖 `h`、`d`、`min` 和非法值。
- `IntakeService` 响应组装：主表字段优先，JSON 回退有效。
- 字段子表 upsert 幂等。

### 集成测试

- 新录入需求后，主表字段和字段子表均正确写入。
- 列表按审批编号、需求名称、业务线、上线日期筛选不依赖 JSON。
- 详情页仍能返回原始 `fields`。
- 迁移后的历史记录不依赖 JSON 也能展示正式字段。

### 迁移验证

- 回填前后需求总数一致。
- `pm_intake_structured_field` 每条需求字段数与原 JSON fields 数一致。
- 高频字段回填率与统计文档一致或可解释。
- 关键筛选接口结果与迁移前一致。
- 迁移后抽样检查 `pm_intake_record` 关键字段非空率，不允许靠 JSON 回退补齐。

## 风险

- 日期格式历史数据不统一，需要容错解析。
- `预估工时`、`预计工时`、`实际工时` 以前混用，迁移时必须区分 `estimated_effort` 和 `actual_effort`。
- `business_line` 历史名称可能不完全匹配 `pm_business_line.business_line_name`，需要记录无法回填 `business_line_code` 的数据。
- 迁移后一旦主表字段和旧 JSON 不一致，以主表字段为准；旧 JSON 不再作为业务真相。

## 验收标准

- 新增需求不再依赖 `structured_data_json` 保存正式业务字段。
- 现有 85 条需求的正式字段完成回填。
- 现有 85 条需求的 `fields` 全量迁移到 `pm_intake_structured_field`。
- 列表、详情、状态推进、业务线修改、上线日期、工时更新功能保持可用。
- 启动迁移 Runner 不再需要长期修复 `structured_data_json` 中的正式字段。
- 代码中正式字段的列表、详情、筛选、更新路径不再解析 `structured_data_json`。
