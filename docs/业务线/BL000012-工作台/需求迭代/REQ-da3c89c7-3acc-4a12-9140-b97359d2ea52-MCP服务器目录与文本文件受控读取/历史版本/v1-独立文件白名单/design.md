# Design：MCP服务器目录与文本文件受控读取

需求编码：REQ-da3c89c7-3acc-4a12-9140-b97359d2ea52；需求名称：MCP服务器目录与文本文件受控读取；版本 v1.0；日期 2026-11-03。

所属业务线：BL000012-工作台；涉及仓库：workhub-server（MCP、资源管理、持久化与测试）。唯一目录：`docs/业务线/BL000012-工作台/需求迭代/REQ-da3c89c7-3acc-4a12-9140-b97359d2ea52-MCP服务器目录与文本文件受控读取`。本地分支 `codex/development-20261102`，基线提交 `4412e13387f0eb9cb6294b65c67433f057155af2`；保留现有其他任务改动。

## 现状与实现

`McpToolRegistry.firstVersion` 为独立及 HTTP MCP 的公共注册入口。`ServerDiagnosticService` 运行闭合命令模板，通过受控 SSH/堡垒机连接目标；现有白名单仅含服务和日志。资源 API、MyBatis、catalog、上下文摘要需同步增加文件白名单。

新增 `allowed_file_paths_json`（NULL/[] 均关闭），不回填其他资源；资源 API 字段 `allowedFilePaths` 仅接受 SERVER 资源的非根绝对目录，去重；更新省略保留，显式 [] 撤销。需先部署迁移，再发布后端。通过资源保存 API 为物流 TEST 资源配置 `/mnt/etc-server`，此配置操作不在本轮执行。

## 工具契约

- `list_server_directory(targetKey, profileKey, path, maxDepth?, maxEntries?)`：默认深度 1，最大 3；条数默认取 profile 行数与 1000 的较小值。返回 path、entries（path/type/sizeBytes/modifiedAt）、truncated。
- `read_server_file(targetKey, profileKey, path, lines?, maxBytes?)`：默认行数取 profile（硬上限 2000），字节默认/最大 262144。返回 path、sizeBytes、content、truncated、dataMasked。
- 资源/权限/SSH/不存在/非文本等失败显式返回错误，不伪装空成功。没有任意命令或脚本参数。

## 安全与依赖

仅使用固定、随程序发布的 Python 3 读取逻辑，通过 SSH 一次性解释执行，不上传、不安装、不写远端文件，不执行被读取的脚本；远端须已提供 python3，缺失时明确失败。用目录文件描述符和 O_NOFOLLOW 逐级打开绝对路径，拒绝所有路径组件软链接，避免 check-then-open 越界；普通文件 O_NONBLOCK 打开并 fstat，设备/FIFO 拒绝。目录遍历不跟随链接，输出与读取均有硬限制。拒绝常见私钥、凭据文件与敏感目录。跨行块、常见密码/token/认证信息脱敏，复用已有个人信息脱敏；未知格式无法保证自动识别所有敏感值，读取范围必须最小化。

SSH 参数统一逐项 shell 引用，固定程序文本不能被路径插入修改；持续排空输出并限制内存，超时结束子进程。文件内容不写审计；记录工具、资源、路径、限制、结果。固定程序正文在审计命令中用摘要占位。

## 验证与交付

本地临时目录模拟脚本、嵌套目录、软链接、FIFO、二进制及超大文本，无需真实网络。Maven 离线验证，不安装依赖。更新平台现状与迭代日志的源码事实，明确尚未发布；停用目录白名单或回退应用可关闭能力，回退时可保留新增空列。


## 迁移执行与校验

脚本落点：`workhub-bootstrap/src/main/resources/db/schema/mysql/V20261103_190110__mcp_server_file_read_paths.sql`，版本使用脚本创建时间，沿用 WorkHub 现有 Flyway 扫描目录；不套用知识库中 asset 专属模块目录。Flyway 按版本仅执行一次，不能手工重复运行 ADD COLUMN，持久环境已执行后不得修改原迁移。新增列 nullable，不修改已有数据，也不自动授予文件访问权限。

发布前核对 schema 和 Flyway 版本，迁移后核对列类型；以下为计划校验 SQL，尚未执行：

```sql
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'mcp_resource_config'
  AND column_name = 'allowed_file_paths_json';
```

预期迁移前 0 行、迁移后 1 行，DATA_TYPE=text、IS_NULLABLE=YES。若已存在同名列，先核对 Flyway 历史而非重复执行。配置目录授权后通过资源 API/catalog 核对 allowedFilePaths，不读取或导出其他凭据列。回退优先清空白名单并回退应用，保留新增列，不自动执行 DROP 或恢复授权数据。
