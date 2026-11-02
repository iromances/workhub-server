# Design：研发产出回归项目仓库

- 需求编码：REQ-898b9c28-bad4-4a1e-ac14-4baad707814b；Proposal v1。
- 范围：workhub-server 自动归档与 MCP；WorkHub、asset 和知识库的文档结构。
- 当前事实：`ProjectKnowledgeBaseService` 使用 `knowledge.project/vaultPath` 同时检索和写入；MCP 同样从该地址推导 raw/wiki；存在历史来源字符串与不可变 manifest。

## 路径与读取

新增共用路径解析，`knowledge.project/repositoryPath` 可指定 WorkHub 仓库根目录；未配置时从应用工作目录向上查找包含 `pom.xml`、`workhub-service/`、`workhub-bootstrap/` 的仓库。归档根固定为该仓库 `docs/`。找不到仓库时明确报错，不回退知识库。

原 `vaultPath` 继续用于读取知识库。写入根必须是有效 WorkHub 仓库，且不得位于配置的知识库内；目录和文件仍采用现有符号链接、路径越界、原子写入及文件锁保护。检索同时覆盖项目 docs 和知识库，沿用业务范围筛选并限制总上下文长度，排除历史版本和 notes。

## 归档与兼容

- 新路径分别为 `requirement-sources/{业务线}/{项目}/{年份}/{审批编号}/source.md` 与 `requirements/{业务线}/需求迭代/{项目}/{年份}/{审批编号}-{名称}/研发评估.md`，相对归档根 docs。
- 旧 `raw/公司项目/requirements/` 和 `raw/requirements/` 来源前缀只在解析时映射到 `requirement-sources/`；不修改数据库或不可变快照。`.workhub-analysis-sources/` 绑定原样迁移。
- 仓库文档使用标准 Markdown 链接；来源记录、索引及正式评估能从实际文件位置打开。
- 既有 API 的 `requirementWikiPath` 等名称兼容保留，含义改为相对项目 docs 的正式评估路径；新归档 URL 为 null，用户使用现有打开文件夹动作。读取旧评估时规范化旧路径并清除过期 Obsidian URL，存量数据库不批量改写。
- MCP 新增 `projectDocumentPath`，原 `projectVaultPath` 表示只读知识库；归档 pattern 与业务线目录改为 docs 内路径。独立 MCP 与内置 MCP 同步。
- 现有幂等标记、事务提交后导出、失败单独记录、不可变来源与历史版本机制保留。

## 迁移与回滚

迁移前记录原件哈希并完整备份，持有 WorkHub 原归档文件锁；所有目标完整写入并验哈希后才移除旧文件。原始材料与历史版本不改写，当前 Markdown 仅更新链接和归档指引。迁移清单存于两仓库 `docs/migrations/2026-10-30.json`。

运行中的旧进程仍可能按旧逻辑写入；需要重新加载修改后的应用才生效。本轮不擅自重启已有进程或修改数据库配置。回滚先停止新的归档写入、保存新产物，再按清单和备份恢复旧位置；不可直接覆盖迁移后新增内容。

## 验证与文档

使用现有 JDK 25 与 Maven 离线运行归档、路径、上下文、文件夹和 MCP 测试；不访问真实数据库或下载依赖。更新仓库系统设计、迭代日志与本任务 Tasks，分别记录文件迁移、代码验证和运行生效状态。
