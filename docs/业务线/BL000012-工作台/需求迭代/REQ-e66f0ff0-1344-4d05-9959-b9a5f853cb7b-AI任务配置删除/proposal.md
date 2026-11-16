# Proposal：AI 任务配置删除

> SDD 流程的 Proposal 模板。随完整规格在实施前统一确认。

| 项目 | 内容 |
| --- | --- |
| 需求编码 | REQ-e66f0ff0-1344-4d05-9959-b9a5f853cb7b |
| 需求名称 | AI 任务配置删除 |
| 涉及仓库与功能域 | workhub-server（workhub-dao、workhub-service、workhub-controller）；workhub-web（AI 配置页） |
| 所属项目或业务线（适用时） | BL000012-工作台 |
| 唯一需求目录 | docs/业务线/BL000012-工作台/需求迭代/REQ-e66f0ff0-1344-4d05-9959-b9a5f853cb7b-AI任务配置删除/ |
| 版本/状态 | 草案 v1 |
| 目标系统 | WorkHub 前后端（AI 任务配置管理） |
| 原始需求来源及版本 | 用户："AI 任务配置增加删除按钮"（清理冗余/误配置任务条目） |
| 目标环境/分支 | 本地开发环境；codex/development-20261102 |
| 负责人/评审人 | 用户；AI 协作（实施） |

## 1. Why

- AI 任务配置只有创建/更新/启停，无法清理冗余或误配置的任务条目（如历史遗留的摆设绑定）。
- 表结构已具备软删字段（is_delete/delete_time/delete_by/version），仅缺接口与入口。

## 2. What：目标与范围

### 本期目标
所有 AI 任务均可删除（用户已确认范围）；软删可恢复（同编码重新保存即重建）；注册场景删除时弹窗强提示平台功能影响。

### 本期交付范围
1. `AiUseCaseConfigMapper.softDelete`（乐观锁软删）。
2. `AiUseCaseConfigService.deleteForManage`（版本校验 + 软删 + DELETE 审计）。
3. `AiConfigCompatibilityController` `DELETE /api/manage/system/ai-use-case/{id}?version=`（权限 system:ai-config:update/manage）。
4. 前端：删除按钮（danger link）+ ElMessageBox 确认（注册场景强提示）+ 类型补 version 字段。
5. Service 删除单测；前后端构建。

### 明确不做
1. 不做物理删除与批量删除。
2. 不校验任务是否被流程引用（删除注册场景的影响由确认弹窗承担，停用仍是推荐的安全替代）。

## 3. Done：成功标准

| 编号 | 验收条件 | 核对方式 |
| --- | --- | --- |
| AC-01 | 删除走软删（is_delete=1 + delete_by/delete_time + version+1），带乐观锁；冲突时报"已被其他操作修改" | 单测 |
| AC-02 | 删除记录 DELETE 审计（主体为删除前快照，权限码 system:ai-config:manage） | 单测实现核对 |
| AC-03 | 前端删除按钮二次确认（注册场景强提示），成功后刷新列表；构建通过 | npm run build |
| AC-04 | 软删后同编码重新保存可重建（saveForManage 对已删行不冲突） | 既有 ensureCodeAvailable 语义（is_delete=0 过滤） |
