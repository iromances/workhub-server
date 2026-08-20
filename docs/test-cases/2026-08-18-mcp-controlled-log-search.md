# MCP 受控日志检索测试用例

## 测试范围

- 模块：`workhub-mcp-server`
- 能力：普通日志与 ZIP 历史日志固定字符串检索
- 安全边界：路径白名单、参数校验、输出上限、审计关键词脱敏

## 测试用例

| 编号 | 场景 | 输入要点 | 预期结果 |
|---|---|---|---|
| TC-01 | 普通日志检索 | 白名单内 `.log`、固定关键词、命中数超过 profile 上限 | 生成 `grep -F` 参数列表，命中数收敛到 profile 上限 |
| TC-02 | ZIP 日志检索 | 白名单内 `.zip`、无正则元字符的固定关键词、有限命中数 | 生成带 `-m` 上限的 `zipgrep` 参数列表 |
| TC-03 | 路径越界 | 白名单外绝对路径 | 拒绝执行并提示路径不在白名单 |
| TC-04 | 关键词控制字符 | 关键词含换行 | 拒绝执行，不产生远程命令 |
| TC-05 | 审计脱敏 | 搜索命令及完整 SSH 参数 | 审计副本中的关键词替换为 `[REDACTED]`，执行参数不变 |
| TC-05A | ZIP 选项注入 | ZIP 检索关键词以 `-` 开头 | 拒绝执行，避免关键词被 `zipgrep` 解释为选项 |
| TC-05B | ZIP 正则注入 | ZIP 检索关键词包含正则元字符 | 拒绝执行，保证仅支持字面量业务标识 |
| TC-06 | 工具注册 | 拉取 MCP tools/list | 返回 `search_service_logs` 及四个必填参数、一个可选参数 |
| TC-07 | 模块回归 | 执行模块测试与编译 | 测试通过，既有只读 SQL、状态和 tail 日志能力不受影响 |

## 执行记录

- `mvn -pl workhub-mcp-server test`：通过，共 32 个测试，0 失败、0 错误、0 跳过。
- `mvn -pl workhub-service -am -DskipTests compile`：通过，`workhub-mcp-server` 与嵌入 MCP runtime 的 `workhub-service` 均编译成功。
- 本地及生产目标 ZIP 兼容性验证：`zipgrep -n -m2 <keyword> <zip>` 能返回普通业务标识命中并限制数量；GNU `zipgrep` 内部固定调用 `egrep`，与 `-F` 组合会冲突，因此最终方案通过拒绝以 `-` 开头及包含正则元字符的 ZIP 关键词来保证字面量语义。
- TC-01 至 TC-07 已由单元测试、真实 `zipgrep` 命令验证和 reactor 编译覆盖，结果通过。
