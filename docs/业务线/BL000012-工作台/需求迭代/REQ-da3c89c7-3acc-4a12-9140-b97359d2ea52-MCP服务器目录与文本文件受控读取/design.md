# Design：MCP 服务器目录与文本文件受控读取

需求编码：REQ-da3c89c7-3acc-4a12-9140-b97359d2ea52；版本 v2.0；日期 2026-11-03。所属业务线 BL000012-工作台；后端 workhub-server、前端 workhub-web 共用本唯一目录。

## 配置与契约

保留历史 allowedLogPaths 字段和 allowed_log_paths_json 列作为唯一配置，统一语义为“允许 AI 读取的白名单目录”。移除未发布的 allowedFilePaths 全链路和新增列。目录/文件工具描述、catalog 和测试上下文均指向 allowedLogPaths，并要求范围外目录先取得用户明确授权。

SERVER 创建请求省略字段时默认 ["/mnt"]；修改时省略保留原值，显式 [] 撤销路径访问。目录配置只接受非根绝对目录，拒绝 .. 和控制字符。数据库资源保持无服务器目录。页面服务器新建表单默认 /mnt，清空不再表示无限制，说明明确其他目录需用户授权。

## 实现与安全

继续保留 list_server_directory/read_server_file。日志尾读与检索在原闭合命令校验后，用同一固定读取程序按目录描述符 O_NOFOLLOW 打开普通文件；tail/grep 接收已验证描述符，ZIP 用标准库流式检索并限制条目和单行大小，不重新打开原路径；既有日志不能借助 /mnt 下软链接读取未授权目录。grep 无匹配仍保持原退出码和输出约定，ZIP 不解压落盘。远端已有 Python 3，缺失时失败，不安装。

目录浏览/文件正文的容量限制、二进制/特殊文件/凭据文件拒绝、脱敏和审计沿用 v1。MCP 不新增资源写入能力，不自动修改白名单。服务名日志查询不接收文件路径，继续由服务白名单控制。

## 数据迁移与回滚

本轮新迁移仅操作已有 allowed_log_paths_json：先新建仅含 SERVER 资源 id/target_key/原目录的备份表，再 UPDATE 全部 SERVER 为 ["/mnt"]，不备份账号或凭据，不增加独立列。Flyway 按版本执行一次；未执行任何真实数据库迁移。迁移前确认备份名和版本未存在；迁移后检查所有服务器配置均为 ["/mnt"]。回退旧应用时可保留此配置；恢复其他旧目录属于再次扩权，必须逐项获得用户明确授权，不能自动整表恢复。

## 验证

分别验证同一目录可列目录/读文件/尾读/检索，空目录以及范围外路径拒绝，日志与脚本软链接不能越界；资源创建默认、更新省略、显式空数组、目录规范化和 catalog 无独立字段。离线运行后端定向测试与编译，前端只使用现有 node_modules 执行生产构建。原 v1 规格与验证保存在历史目录，不将旧验证当作 v2 已通过。

页面保存先固定请求范围并锁定提交；新增且不被 /mnt 或原授权目录覆盖的根目录必须用户点击“明确授权并保存”，取消不保存。资源管理 API 仍受现有维护权限控制，通过 API 添加其他目录也必须有用户明确授权；AI 的只读 MCP 不承担授权写入。


## v2 迁移核对

版本路径：`workhub-bootstrap/src/main/resources/db/schema/mysql/V20261103_193242__unify_mcp_ai_read_paths_default_mnt.sql`。备份名 `bak_mcp_ai_read_paths_20261103_193242`。Flyway 只执行一次，失败时按历史核对，不能手工重复 CREATE TABLE。当前未执行。

发布前后计划校验 SQL（只读，未执行）：

```sql
SELECT COUNT(*) AS server_count,
       SUM(CASE WHEN allowed_log_paths_json = '["/mnt"]' THEN 1 ELSE 0 END) AS mnt_count
FROM mcp_resource_config
WHERE resource_type = 'SERVER';
```

迁移后两个计数应相同，备份行数应与迁移前 server_count 相同。数据库资源不在迁移范围。没有自动回滚其他目录授权的 SQL；确需恢复时先核对单个目标及用户授权，再按主键单表恢复其目录配置，不能整表恢复成原有无限制或其他目录。

实际工作区：后端 `/Users/aslight/IDEAWorkspace/workhub-server`，沿用 `codex/development-20261102`；前端 `/Users/aslight/IDEAWorkspace/workhub-web`，`codex/development-20261103`，核对基线 `1a52cd93a3fcc07bd5c713c074574d6a037818d0`。本轮保留两仓已有其他任务改动。
