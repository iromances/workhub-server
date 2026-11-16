# Tasks：MCP服务器目录与文本文件受控读取

需求编码：REQ-da3c89c7-3acc-4a12-9140-b97359d2ea52；需求名称：MCP服务器目录与文本文件受控读取；版本 v1.0；日期 2026-11-03。

所属业务线：BL000012-工作台；涉及仓库：workhub-server（MCP、资源管理、持久化与测试）。唯一目录：`docs/业务线/BL000012-工作台/需求迭代/REQ-da3c89c7-3acc-4a12-9140-b97359d2ea52-MCP服务器目录与文本文件受控读取`。本地分支 `codex/development-20261102`，基线提交 `4412e13387f0eb9cb6294b65c67433f057155af2`；保留现有其他任务改动。

## 规格确认

来源：本会话用户先确认只读命令及权限边界，随后明确要求修改代码和 MCP。确认人：用户；日期：2026-11-03。v1.0 将上述授权细化为独立文件白名单、固定只读工具及测试；不扩大为执行脚本、写文件或自动部署。状态：开发完成，离线自动化已验证，未部署。

| 任务 | 交付物 | 验收 | 状态 |
| --- | --- | --- | --- |
| T-01 | 资源字段、SQL、catalog 与兼容处理 | AC-02 | 已完成 |
| T-02 | 固定文件读取、路径保护、脱敏及审计 | AC-03/04 | 已完成 |
| T-03 | 两个 MCP 工具、契约与摘要 | AC-01 | 已完成 |
| T-04 | 离线测试、文档及交付核对 | AC-01—05 | 已完成 |

实际验证记录见下节；本轮没有执行远端目录读取、数据库迁移或应用发布。


## 实际实施与验证

- T-01：新增独立 `allowed_file_paths_json` 与 `allowedFilePaths`，打通 Entity/Mapper/保存与响应/catalog/上下文；省略保留、空数组撤销、默认关闭与数据库资源拒绝已测试。
- T-02：固定读取程序、目录描述符逐级 O_NOFOLLOW、普通文件检查、UTF-8 和容量限制；敏感凭据及跨行配置脱敏；SSH 参数引用、输出内存上限、超时回收与审计元数据已实现。文件内容不写审计。
- T-03：公共注册表与 HTTP MCP 暴露两个文件工具，补充服务器上下文与调用指引；没有任意 shell/写入/用户脚本执行入口。
- T-04：2026-11-03 最终定向测试 **60 项全部通过，0 失败/错误/跳过**；主应用及依赖模块离线编译通过，差异格式检查通过。见 [测试日志](验证/offline-tests.log)、[编译日志](验证/offline-compile.log)、[机器结果](验证/result.json)。

测试命令：

```bash
mvn -o -pl workhub-mcp-server,workhub-service -am -Dtest=ServerFilePolicyTest,ServerFileContentMaskerTest,SensitiveDataMaskerTest,ServerDiagnosticServiceTest,CommandPolicyGuardTest,McpToolRegistryTest,McpFileResourceServiceTest,McpRuntimeServiceTest -Dsurefire.failIfNoSpecifiedTests=false -DargLine=-javaagent:/Users/aslight/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar test
mvn -o -pl workhub-bootstrap -am -DskipTests compile
```

Mockito 自附加在当前沙箱环境不可用，改用本机已缓存的 Mockito agent，无依赖安装；子进程回收测试需要本地进程查询权限，最终在获准的宿主测试环境执行，不连接业务服务器。测试曾发现超长单行触发原邮件脱敏正则高耗时，增加词边界后回归通过；模拟资源缺少 SSH 凭据的测试数据已补全。

## 交付边界与后续生效

- 已更新平台现状、迭代日志及 [配置与调用说明](../../../../system/mcp-server-file-access.md)，保留工作区原有其他任务改动。
- 未安装软件、依赖或远端脚本；未执行真实服务器读取、数据库迁移、目录授权或应用发布。
- 生效需先执行新增迁移并发布，再由具备资源维护权限的账号为物流 TEST 资源配置 `allowedFilePaths=["/mnt/etc-server"]`，刷新 MCP 工具列表。远端需已有 Python 3；不自动安装。前端未增加新输入框，当前通过已有资源保存 API 配置。
- 尚未核实物流服务器是否已有 Python 3、目录实际权限及脚本路径；这些属于发布后的受控只读联调，不将本地隔离测试替代真实环境验证。
- 回滚：撤销白名单即可关闭新能力；应用回退可保留新增列。
