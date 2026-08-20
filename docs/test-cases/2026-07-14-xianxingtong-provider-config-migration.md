# 先行通 Provider 配置平移测试用例

## 1. 需求信息

- 需求名称：先行通 Provider 配置平移
- 研发方案：`docs/研发设计/迭代/2026-07-14-xianxingtong-provider-config-migration.md`
- 涉及系统：workhub-server
- 编写时间：2026-07-14

## 2. 测试范围

- V20 初始化先行通 Provider 模板。
- `XIANXINGTONG + OPENAI_COMPATIBLE` URL 安全校验。
- API Key 不进入迁移脚本，模板默认停用且不绑定场景。
- 既有 AI 配置与统一网关回归。

## 3. 计划用例

| 编号 | 类型 | 场景 | 预期 | 结果 |
|---|---|---|---|---|
| TC-01 | 核心 | 新库执行 V20 | 新增唯一 `xianxingtong-openai-api` | 待执行 |
| TC-02 | 幂等 | 同编码已存在 | 不覆盖已有配置 | 待执行 |
| TC-03 | 安全 | 检查迁移脚本 | `api_key=NULL`、`enabled=0` | 待执行 |
| TC-04 | 兼容 | 检查场景绑定 | 绑定数量为 0，现有场景仍走 CLI | 待执行 |
| TC-05 | 核心 | 保存标准兼容协议 URL | 保存成功 | 待执行 |
| TC-06 | 边界 | 保存显式 443 和尾斜杠 | 保存成功 | 待执行 |
| TC-07 | 异常 | HTTP、伪造域名、userinfo、8443、错误路径、query、fragment | 全部拒绝 | 待执行 |
| TC-08 | 安全 | 新模板录入 API Key | AES-GCM 加密落库，响应只脱敏 | 待执行 |
| TC-09 | 回归 | AI Provider 定向测试 | 全部通过 | 待执行 |
| TC-10 | 编译 | 后端全模块编译 | 成功 | 待执行 |

## 4. 计划执行命令

- `mvn -pl workhub-service -am -Dtest=AiProviderConfigServiceTest,AiCredentialCryptoServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`
- `mvn -q -DskipTests compile`
- `git diff --check`

## 5. 不纳入自动化测试

- 真实先行通 API 探针、计费、限流、生产网络和模型 ID。
- 原因：缺少本次明确的真实密钥、模型和费用授权。
- 替代验证：HTTP Mock 合约测试；上线后由授权管理员发送固定无业务数据探针。

## 6. 执行记录

方案阶段尚未执行；开发完成后回填时间、命令、通过数量、失败原因和剩余风险。
