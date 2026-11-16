-- 2026-11-15｜巡检派发死锁解卡（运维操作，用户确认执行）
-- 背景：11-14 晚服务重启期间 3 个巡检 Agent 的在途运行被恢复为 WAITING_RESOURCE 且残留
-- owner_token + 未决 CLI 句柄；旧口径 countOwnedRuns 按 owner_token 计数，单 Agent 并发=1
-- 被永久占满，三个 Agent 自 11-14 23:15 起共 25 轮巡检卡 QUEUED。代码已修复为只统计存活租约
-- （AgentMapper.countOwnedRuns），本脚本完成数据面收尾。
--
-- 内容：
--   1) 终结 3 条 WAITING_RESOURCE 运行为 FAILED（CLI 进程昨晚已随旧进程死亡，句柄不可回收）；
--   2) 每个受影响任务仅保留最新 1 条 QUEUED，其余 22 条积压轮次取消（增量窗口由保留轮次接续，
--      避免 25 轮 × 4-8 分钟的回放）；
--   3) 全部变更写 pm_agent_audit 审计与 pm_agent_event 事件，幂等可重跑。
--
-- 执行库：workhub（10.10.116.32:3306）；执行前请核对第 0 节预检查询返回与预期一致。

-- ============ 0. 预检（应看到 3 行 WAITING_RESOURCE 且 external_handle 非空；QUEUED 每任务 8~9 行） ============
SELECT id, agent_id, status, external_handle IS NOT NULL AS has_handle, error_message
FROM pm_agent_run WHERE owner_token IS NOT NULL;
SELECT task_id, COUNT(*) FROM pm_agent_run WHERE status='QUEUED' GROUP BY task_id;

START TRANSACTION;

-- ============ 1. 终结 3 条 WAITING_RESOURCE（CLI 进程已死，人工核实后关闭） ============
UPDATE pm_agent_run
SET status='FAILED', owner_token=NULL, lease_until=NULL, external_handle=NULL,
    error_message='运维关闭：执行器重启后 CLI 进程已不存在，未决句柄无法回收，经确认终结',
    version=version+1, updated_at=UTC_TIMESTAMP(6)
WHERE id IN (
    '7a81f1b8-265f-41d6-a075-d7a3858aa957',
    '9d5798ef-f782-4003-8e2f-26b32cb04cba',
    'd6b48066-8f52-494a-820e-ebc7809c468f')
  AND status='WAITING_RESOURCE' AND owner_token IS NOT NULL;

INSERT INTO pm_agent_audit(id, object_type, object_id, action, actor, payload_json, created_at)
SELECT UUID(), 'RUN', id, 'OPS_CLOSE', 'ops-sql',
       JSON_OBJECT('reason', '执行器重启后 CLI 进程已死亡，未决句柄经确认终结', 'fromStatus', 'WAITING_RESOURCE', 'toStatus', 'FAILED'),
       UTC_TIMESTAMP(6)
FROM pm_agent_run
WHERE id IN ('7a81f1b8-265f-41d6-a075-d7a3858aa957','9d5798ef-f782-4003-8e2f-26b32cb04cba','d6b48066-8f52-494a-820e-ebc7809c468f')
  AND updated_at > DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 5 MINUTE)
  AND error_message LIKE '运维关闭%';

INSERT INTO pm_agent_event(id, run_id, event_key, ordinal, kind, payload_json, actor, created_at)
SELECT UUID(), id, 'ops:close', 9000, 'FAILED',
       JSON_OBJECT('reason', '执行器重启后 CLI 进程已死亡，未决句柄经确认终结'), 'ops-sql', UTC_TIMESTAMP(6)
FROM pm_agent_run r
WHERE r.id IN ('7a81f1b8-265f-41d6-a075-d7a3858aa957','9d5798ef-f782-4003-8e2f-26b32cb04cba','d6b48066-8f52-494a-820e-ebc7809c468f')
  AND r.status='FAILED'
  AND NOT EXISTS (SELECT 1 FROM pm_agent_event e WHERE e.run_id=r.id AND e.event_key='ops:close');

-- ============ 2. QUEUED 每任务只保留最新 1 条，其余取消 ============
UPDATE pm_agent_run r
JOIN (
    SELECT task_id, MAX(created_at) AS keep_at
    FROM pm_agent_run WHERE status='QUEUED'
    GROUP BY task_id
) keep ON keep.task_id = r.task_id
SET r.status='CANCELLED', r.cancel_requested=1,
    r.error_message='运维取消：派发死锁期间的积压轮次，保留每任务最新一轮接续增量窗口',
    r.version=r.version+1, r.updated_at=UTC_TIMESTAMP(6)
WHERE r.status='QUEUED' AND r.created_at < keep.keep_at;

INSERT INTO pm_agent_audit(id, object_type, object_id, action, actor, payload_json, created_at)
SELECT UUID(), 'RUN', id, 'OPS_CANCEL', 'ops-sql',
       JSON_OBJECT('reason', '派发死锁期间的积压轮次取消，保留最新一轮', 'fromStatus', 'QUEUED', 'toStatus', 'CANCELLED'),
       UTC_TIMESTAMP(6)
FROM pm_agent_run
WHERE status='CANCELLED' AND updated_at > DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 5 MINUTE)
  AND error_message LIKE '运维取消%';

INSERT INTO pm_agent_event(id, run_id, event_key, ordinal, kind, payload_json, actor, created_at)
SELECT UUID(), id, 'ops:cancel', 9000, 'CANCELLED',
       JSON_OBJECT('reason', '派发死锁期间的积压轮次取消'), 'ops-sql', UTC_TIMESTAMP(6)
FROM pm_agent_run r
WHERE r.status='CANCELLED' AND r.error_message LIKE '运维取消%'
  AND NOT EXISTS (SELECT 1 FROM pm_agent_event e WHERE e.run_id=r.id AND e.event_key='ops:cancel');

COMMIT;

-- ============ 3. 复核（应看到：owner_token 残留=0；每任务 QUEUED=1；被关闭 3 行 FAILED） ============
SELECT COUNT(*) AS residual_owned FROM pm_agent_run WHERE owner_token IS NOT NULL;
SELECT task_id, status, COUNT(*) FROM pm_agent_run
WHERE task_id IN ('ad59acd8-e377-4382-9038-29469860aa1a','2181445e-dd1c-11f1-8721-005056949076','e2d82c44-e64a-4835-a0a5-0354a145874b')
  AND status IN ('QUEUED','FAILED','CANCELLED') AND updated_at > DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 10 MINUTE)
GROUP BY task_id, status;

-- ============ 4. 补充收尾（2026-11-15 08:50 执行）：应用二次停止产生的孤儿运行 ============
-- 用户 08:38-08:41 曾重启并认领 3 轮（37fb8b5b/a6605da3/d1f11f4a），随后应用再次停止，
-- 心跳冻结、结果无人接收；连同 00:41 恢复出的 c35b6629（新消费分期）一并按失败关闭。
UPDATE pm_agent_run
SET status='FAILED', owner_token=NULL, lease_until=NULL, external_handle=NULL,
    error_message='运维关闭：执行器进程停止后 CLI 结果无人接收，运行已不可恢复',
    version=version+1, updated_at=UTC_TIMESTAMP(6)
WHERE id IN ('37fb8b5b-931e-4c69-8850-97b7d617fc8f','a6605da3-96a8-4ba1-a236-d814a66a1fef',
             'd1f11f4a-8d2c-4bdc-8d29-0df3396bbad6','c35b6629-53a2-4606-9a60-8f7c5d149aa5')
  AND status IN ('RUNNING','WAITING_RESOURCE') AND owner_token IS NOT NULL;

INSERT INTO pm_agent_audit(id, object_type, object_id, action, actor, payload_json, created_at)
SELECT UUID(), 'RUN', id, 'OPS_CLOSE', 'ops-sql',
       JSON_OBJECT('reason', '执行器进程停止后 CLI 结果无人接收，运行不可恢复', 'toStatus', 'FAILED'),
       UTC_TIMESTAMP(6)
FROM pm_agent_run
WHERE id IN ('37fb8b5b-931e-4c69-8850-97b7d617fc8f','a6605da3-96a8-4ba1-a236-d814a66a1fef',
             'd1f11f4a-8d2c-4bdc-8d29-0df3396bbad6','c35b6629-53a2-4606-9a60-8f7c5d149aa5')
  AND status='FAILED' AND updated_at > DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 5 MINUTE)
  AND error_message LIKE '运维关闭%';

INSERT INTO pm_agent_event(id, run_id, event_key, ordinal, kind, payload_json, actor, created_at)
SELECT UUID(), id, 'ops:close', 9000, 'FAILED',
       JSON_OBJECT('reason', '执行器进程停止后 CLI 结果无人接收，运行不可恢复'), 'ops-sql', UTC_TIMESTAMP(6)
FROM pm_agent_run r
WHERE r.id IN ('37fb8b5b-931e-4c69-8850-97b7d617fc8f','a6605da3-96a8-4ba1-a236-d814a66a1fef',
               'd1f11f4a-8d2c-4bdc-8d29-0df3396bbad6','c35b6629-53a2-4606-9a60-8f7c5d149aa5')
  AND r.status='FAILED' AND r.error_message LIKE '运维关闭%'
  AND NOT EXISTS (SELECT 1 FROM pm_agent_event e WHERE e.run_id=r.id AND e.event_key='ops:close');

-- 复核：owner_token 残留应为 0，QUEUED 每任务仍为 1
SELECT COUNT(*) AS residual_owned FROM pm_agent_run WHERE owner_token IS NOT NULL;
SELECT task_id, COUNT(*) AS queued FROM pm_agent_run WHERE status='QUEUED' GROUP BY task_id;
