# Design：AI 任务配置删除

> 承接 REQ-e66f0ff0 Proposal。核心契约与非功能约束如下，完整分层同 REQ-d97d16b2 的 Design 风格。

## 1. 契约

- `DELETE /api/manage/system/ai-use-case/{id}?version={version}`（AiConfigCompatibilityController）
  - 权限：`system:ai-config:update or system:ai-config:manage`
  - 返回 `ApiResponse<Void>`；冲突/不存在抛 400（GlobalExceptionHandler 统一）
- Service：`deleteForManage(id, version, operator, ip)`——requireExisting → version 乐观校验 → `mapper.softDelete(id, version, operator)` → `audit("DELETE", existing, existing, operator, ip)`（after=删除前快照作审计主体）
- Mapper：`softDelete(id, version, deleteBy)`——`SET is_delete=1, delete_time=CURRENT_TIMESTAMP, delete_by, updated_at, version=version+1 WHERE id AND is_delete=0 AND version`
- 审计权限码：DELETE → `system:ai-config:manage`（AiUseCaseConfigService.audit 映射扩展）

## 2. 前端

- `deleteAiUseCase(id, version)`（api/ai-config.ts）
- 操作列删除按钮（danger link，canUpdate 可见）→ ElMessageBox.confirm：注册场景（row.capability 非空）强提示"平台功能依赖，删除后相关流程将不可用，可重新创建恢复"；未注册场景常规提示
- 类型 `AiUseCaseConfig` 补 `version: number`（管理列表已下发）

## 3. 非功能

- 软删可恢复：saveForManage 的 ensureCodeAvailable 仅查 is_delete=0，同编码重建即插入新行
- 并发：乐观锁版本校验，冲突明确报错不覆盖
- 审计：DELETE 记录删除前快照

## 4. 验证

- 单测：删除成功（softDelete 调用核对）、version 冲突拒绝、行不存在拒绝
- 前端构建通过；后端全链编译通过
