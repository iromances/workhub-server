# 自动化测试用例：历史需求支持修改业务线

## 测试范围

- 后端服务层业务线校验、结构化 JSON 更新和历史留痕。
- 后端 Controller 新接口路由。
- 后端编译。
- 前端构建和详情页面手工验证。

## 用例清单

| 编号 | 场景 | 前置条件 | 操作 | 预期结果 | 执行结果 |
|---|---|---|---|---|---|
| TC-001 | 终态历史需求修改业务线 | 需求状态为 `已完成`，原业务线为 `供应链科技`，目标业务线 `资产业务` 已启用 | 调用 `IntakeService.updateBusinessLine` | `structuredData.businessLine` 和 `projectHint` 更新为 `资产业务`，其他字段保留，写入修改历史 | 已编写自动化用例；执行受 Mockito inline mock maker 在 JDK 25 下无法 self-attach 阻塞 |
| TC-002 | 禁用或不存在业务线拒绝修改 | 目标业务线不存在或 `enabled=false` | 调用 `IntakeService.updateBusinessLine` | 抛出业务异常，不更新 JSON，不写历史 | 已编写自动化用例；执行受 Mockito inline mock maker 在 JDK 25 下无法 self-attach 阻塞 |
| TC-003 | Controller 暴露接口 | 登录用户为 `admin` | `POST /api/intake/9/business-line` | 返回 200，调用服务层并返回更新后的详情 | 已编写自动化用例；未单独执行，原因同 Mockito 环境限制 |
| TC-004 | 后端编译 | 当前代码完成 | 执行 `mvn -q -DskipTests compile` | 编译通过 | 通过，2026-06-12，Codex |
| TC-005 | 前端构建 | 前端代码完成 | 执行 `npm run build` | 构建通过 | 通过，2026-06-12，Codex |
| TC-006 | 页面手工验证 | 存在可编辑历史需求 | 在需求详情点击业务线编辑并保存 | 详情和列表显示新业务线，修改历史有记录 | 待执行 |

## 实际执行记录

- `mvn -q -DskipTests compile`：通过，2026-06-12，Codex。
- `npm run build`（`../workhub-web`）：通过，2026-06-12，Codex。
- `mvn -pl workhub-service -am -Dtest=cn.aslight.workhub.service.intake.IntakeServiceTest#updateBusinessLine_shouldPersistBusinessLineForCompletedHistoricalDemand -Dsurefire.failIfNoSpecifiedTests=false test`：失败，失败原因是 Mockito inline mock maker 在 JDK 25 下无法 self-attach；加 `MAVEN_OPTS=-Djdk.attach.allowAttachSelf=true` 后仍失败。
- 剩余风险：自动化断言未实际跑绿，需要后续在可用 Mockito agent 配置或兼容 JDK 环境下复跑。
