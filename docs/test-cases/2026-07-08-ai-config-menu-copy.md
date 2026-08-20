# 测试用例：AI配置菜单能力复制

## 1. 测试范围

- 后端 AI provider 接口。
- 后端 AI useCase 接口。
- Flyway 表和权限脚本。
- 前端系统管理 / AI配置页面。

## 2. 计划用例

| 编号 | 场景 | 步骤 | 预期 |
| --- | --- | --- | --- |
| TC-01 | 查询 provider 列表 | 登录后请求 `GET /api/system/ai-providers` | 返回分页结构，默认可为空列表 |
| TC-02 | 新增 API provider | 填写 API 协议、Base URL、API Key、模型厂商后保存 | 保存成功，列表展示 API 通道 |
| TC-03 | 新增 CLI provider | 填写 CLI 命令后保存 | 保存成功，列表展示 CLI 通道 |
| TC-04 | provider 必填校验 | API 通道缺少 Base URL 或 API Key 保存 | 后端拒绝并返回错误 |
| TC-05 | 新增 useCase | 绑定启用 provider，填写编码、名称、领域和提示词 | 保存成功，提示词版本为 v1 且有 checksum |
| TC-06 | 编辑 useCase 提示词 | 修改提示词模板保存 | 保存成功，提示词版本递增 |
| TC-07 | useCase 筛选 | 按领域、模型厂商、通道、启停、关键字查询 | 返回符合条件的数据 |
| TC-08 | 权限控制 | 无 `system:ai-config:view` 访问页面或接口 | 前端跳转 403，后端拒绝 |
| TC-09 | 前端构建 | 执行 `npm run build` | 构建通过 |
| TC-10 | 后端编译 | 执行 `mvn -q -DskipTests compile` | 编译通过 |

## 3. 执行结果

| 项目 | 结果 | 时间 | 执行人/代理 | 说明 |
| --- | --- | --- | --- | --- |
| 后端编译 | 通过 | 2026-07-08 | Codex | 命令：`mvn -q -DskipTests compile` |
| 前端构建 | 通过 | 2026-07-08 | Codex | 命令：`npm run build` |
| 接口手工验证 | 未执行 | 2026-07-08 | Codex | 当前未启动后端服务、未执行 Flyway 脚本和登录态接口验证 |
| 页面手工验证 | 未执行 | 2026-07-08 | Codex | 当前未启动前端 dev server 做浏览器交互验证 |

## 4. 剩余风险

- 本次按用户要求明文保存 API Key，需通过权限控制降低误访问风险。
- 未完成真实数据库 Flyway 执行验证，需上线或本地联调时确认 `V13` 执行成功。
