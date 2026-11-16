# Tasks：研发产出回归项目仓库

- 需求编码：REQ-898b9c28-bad4-4a1e-ac14-4baad707814b；Proposal/Design v1。
- 所属项目：WorkHub；分支：codex/dev-20260820。
- 来源与范围确认：2026-10-30 用户先授权文档回归各自仓库，随后明确选择“同时修改应用自动归档功能”。据此实施文档迁移、自动归档目录与相关路径兼容；未将部署或配置写库视为已授权。
- 建议研发任务标题：研发产出回归项目仓库并调整自动归档。

| 任务 | 交付物与验收 | 状态 |
| --- | --- | --- |
| T-01 | 文件迁移、规则、入口与完整性；AC-01 | 完成；275 + 53 个文件、14 段自动分析；247 个原始/历史/附件文件逐字节校验，13 份 manifest 的 39 个文件哈希一致 |
| T-02 | 项目文档根目录、自动归档与旧路径兼容；AC-02/03/04 | 完成；定向回归通过 |
| T-03 | 文档检索、文件夹与 MCP 目录；AC-05/06 | 完成；定向回归通过 |
| T-04 | 离线测试、系统设计与迭代日志更新 | 完成；85 项测试通过，系统文档与迭代日志已更新 |

## 已有证据

- 文档迁移备份与逐文件计划：`/private/tmp/rd-docs-move-20261030-oBiO9c/`。
- 知识库迁移前 `git pull --ff-only` 返回 `Already up to date.`；现有未提交改动保留在迁移目标或原位置。
- 文档迁移执行：328 个文件、350 次受控写入；移除源文件前已比较写入哈希。

## 验证与生效

- 85 项测试，0 失败、0 错误，覆盖 11 个测试类；日志见 [离线测试](验证/maven-offline-tests.log)，汇总见 [validation.json](验证/validation.json)。
- 使用已安装 JDK 25、Maven 和本机缓存的 Mockito agent，以 `mvn -o` 离线执行；未安装软件或下载依赖。最初动态 agent 自附加受环境限制，改用已缓存 agent 后通过；增加未创建目录的 macOS 别名重叠用例后发现并修复目录规范化问题。
- 当前迁移文档的本仓库/知识库链接检查无缺失；历史原件中的旧链接和旧路径说明按原字节保留。过程记录中的 14 段原始 JSON 完整保留。
- `git diff --check` 通过；原有非本任务暂存文件未改动。
- 自动归档应用尚未重新加载；本轮未重启、部署、写数据库或修改运行配置。已有运行进程仍可能使用旧归档逻辑，重新加载后才能按新规则写入。

执行命令：

```sh
mvn -o -pl workhub-service,workhub-mcp-server -am -Dtest=RepositoryDocumentPathsTest,ProjectKnowledgeBaseServiceTest,KnowledgeArchiveIntegrationTest,KnowledgeArchiveWorkflowTest,RequirementMaterialExportServiceTest,DevelopmentPlanFolderServiceTest,DevelopmentAnalysisServiceTest,IntakeClarificationAnalysisServiceTest,McpResourceServiceTest,McpRuntimeServiceTest,McpToolRegistryTest -Dsurefire.failIfNoSpecifiedTests=false -DargLine=-javaagent:/Users/aslight/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar test
```

## 版本管理与最终复核

- 知识库按其指引创建本地提交 `8a1cc0db8a8263c557a0b4c4f068135c4815bc1c`，使用独立暂存区仅纳入本轮规范、链接和迁移删除；未推送。既有其他工作区内容和未跟踪文件保留。
- 两个代码仓库的文件与代码改动保留在当前工作区；原先暂存的 WorkHub Python 缓存及 asset 的 `.DS_Store` 条目保持不变。
- 最终复核：旧迁移目录无剩余产出；WorkHub、asset 的 `git diff --check` 均通过。
