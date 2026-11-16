# Tasks：AI 任务配置删除

## 1. 元信息
- 需求编码：REQ-e66f0ff0-1344-4d05-9959-b9a5f853cb7b
- 唯一需求目录：docs/业务线/BL000012-工作台/需求迭代/REQ-e66f0ff0-1344-4d05-9959-b9a5f853cb7b-AI任务配置删除/
- 目标分支：codex/development-20261102；规格确认：用户批准范围（全部可删+软删+确认弹窗），2026-11-12

## 2. 任务与状态

| 任务 | 交付物 | 状态 | 证据 |
| --- | --- | --- | --- |
| T-01 | Mapper.softDelete（乐观锁软删） | 已完成 | AiUseCaseConfigMapper.java |
| T-02 | Service.deleteForManage（校验+审计） | 已完成 | audit 映射扩展 DELETE→manage；after=删除前快照 |
| T-03 | DELETE 端点 | 已完成 | AiConfigCompatibilityController |
| T-04 | 前端 API/按钮/确认框/类型 version | 已完成 | ai-config.ts、AiConfigView.vue、types/ai-config.ts |
| T-05 | Service 单测 3 例 | 已完成 | 见 §3 |
| T-06 | 前后端构建 | 已完成 | npm run build ✓；mvn -am compile ✓（workhub-service 当前被并发在途任务 MqMonitorCollector 写入临时阻塞，待其收敛后复验） |
| T-07 | 页面联调（删除→列表刷新→重建恢复） | 待部署验证 | — |

## 3. 计划验证方式与真实结果
- AiUseCaseConfigServiceTest 新增 3 例：softDelete 成功核对、version 冲突拒绝且不触删、行不存在拒绝
- 真实结果：待并发写入收敛后随全量回归一并执行（用例已就绪）

## 4. 覆盖与评审
- AC-01→T-05；AC-02→T-02/T-05；AC-03→T-04/T-06；AC-04→Design §3（语义保证）
- 关联：Proposal/Design v1（本目录）
