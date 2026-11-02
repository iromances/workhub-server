# Tasks：参考 amp-saps 调整 amp-order

需求编码：REQ-428dadce-1c72-4d7d-9eb7-d4233554e6c7  
需求名称：参考 amp-saps 调整 amp-order  
业务线：BL000004／消费分期；GitLab 组：asset-plateform  
唯一需求目录：wiki/公司项目/研发需求/业务线/BL000004-消费分期/20261019-中间件升级准备  
涉及仓库：amp-saps（只读参考）、amp-order（实施）  
规格版本：v1.0（2026-10-23，用户已确认；本地实现及离线验证完成）  
当前研发分支：`feature/nacos-xxljob-upgrade_20260123`（2026-10-23 按用户要求完成本地及远程分支更名）


目标：本地开发、test 配置；目标分支 codex/amp-order-nacos-upgrade。实施：Codex；确认人：用户（2026-10-23）。

## 任务及真实状态

| 任务 | 交付物 | 前置 | 验收 | 状态 |
| --- | --- | --- | --- | --- |
| T-01 | 核实 IDEA、业务线、基线和两仓库差异 | 无 | 证据完整 | 已完成，只读 |
| T-02 | 完整规格统一确认 | T-01 | 范围与旧文档差异明确 | 用户已确认，2026-10-23 |
| T-03 | 分支与两文件最小修改 | T-02 | AC-01～AC-03 | 已完成，工作树未提交 |
| T-04 | XML/YAML 检查、离线依赖树与编译 | T-03 | AC-01～AC-04 | 已完成，本地检查通过 |
| T-05 | 更新本记录及业务线迭代日志、交付文件链接 | T-04 | 实际结果和缺口清楚 | 已完成 |

按 T-01 至 T-05 顺序执行。允许修改范围为 amp-order 根 pom.xml、amp-order-start/src/main/resources/bootstrap.yml 及对应规格/进展文档。参考 amp-saps 不修改；配置凭据不得输出至记录。

## 验证结果

- [x] XML 解析及 Nacos 客户端直接依赖唯一性。
- [x] YAML 解析；test 新值与源配置一致，dev-amp 删除，其他 profile 和服务属性保留。
- [x] Git 空白错误检查及两文件差异审阅。
- [x] Maven 离线依赖树和 compile；如环境或缓存阻塞，记录原始失败原因，不擅自下载安装。
- [x] 交付说明开发、验证与部署状态；没有运行时验证即不得写为已完成联调。

## 统一确认记录

范围：Proposal v1.0、Design v1.0 全文。用户于 2026-10-23 回复“确认，按这个 Nacos 升级范围执行”，明确确认新增 nacos-client 2.5.4、默认 test、移除 dev-amp、测试地址与账号密码沿用 amp-saps，以及新分支离线验证。结论：已确认，可以实施。旧迁移文档的不新增凭据口径在本轮 test 范围内以此确认及 amp-saps 参考提交为准。

## 实际证据

2026-10-23：IDEA 显示 amp-saps／feature/nacos-alibaba-upgrade。Git 记录 amp-saps 4ca0cd7b1db72503ccc8b4d163c63e139ca0a181（“nacos升级”）；amp-order master f696b160af8081f9a2090aca35ced64c2d5005af，两工作树均干净。WorkHub 映射 BL000004／消费分期／asset-plateform。以上为实施前基线记录。


### 2026-10-23 实施与验证

- 分支：`codex/amp-order-nacos-upgrade`；基线保持 `f696b160af8081f9a2090aca35ced64c2d5005af`，改动未提交。
- 改动：仅根 `pom.xml` 和 `amp-order-start/src/main/resources/bootstrap.yml`；新增直接依赖 `com.alibaba.nacos:nacos-client:2.5.4`，默认 profile 改为 test，删除 dev-amp，test 配置/注册两个地址及鉴权项逐项沿用 amp-saps 参考提交。
- XML 结构验证通过：除新增唯一的 nacos-client 2.5.4 直接依赖外，POM 结构与基线一致。
- 使用本地 SnakeYAML 1.25 和 JDK 8 解析验证全部 YAML 文档；默认 test、删除 dev-amp、test 目标字段与参考配置一致，其余配置结构与基线完全相同，应用名称仍为 amp-order。凭据未输出到文档或日志。
- `git diff --check` 通过。
- 本地 Java SE 8 `1.8.0_481`、Maven `3.9.14`，执行 `mvn -o -B -Dstyle.color=never -DskipTests compile`：退出码 0，BUILD SUCCESS，聚合工程及 7 个业务模块全部成功。
- 执行 `mvn -o -B -Dstyle.color=never -N dependency:tree -Dincludes=com.alibaba.nacos:nacos-client`：退出码 0，BUILD SUCCESS，解析结果 `com.alibaba.nacos:nacos-client:jar:2.5.4:compile`。
- 构建仍报告既有重复依赖声明、Druid 1.2.6 POM 中 tools/jconsole 的 systemPath 问题和 Hibernate Validator relocation 提示；这些依赖声明不在本次变更中，构建未被阻断。本轮未扩大范围修复。
- 临时完整构建日志：`/var/folders/br/ndyhl3gs0rvb6z12m_x8rqf00000gn/T/amp-order-nacos-c2dc0z0j/compile.log` 与 `dependency-tree.log`。
- 未运行 Spring 应用或集成测试；Nacos 配置拉取、注册、鉴权及 gRPC 连通性尚待指定环境联调。未安装或下载任何软件/依赖，未提交、推送或部署。
- 已核对业务线系统设计：当前仅描述跨业务线业务规则，本轮没有变更业务契约或部署现状，故不改系统设计。实际进展记入业务线 `迭代日志.md`；原生产迁移文档维持其原适用范围。


### 2026-10-23 分支名称更新（本轮最新状态）

用户要求 amp-order、amp-saps、thctay-public 的本地及远程升级分支统一为 `feature/nacos-xxljob-upgrade_20260123`，按用户原样指定的日期后缀执行。

| 仓库及工作区 | 原本地分支 | 远程处理 | 核验提交 |
| --- | --- | --- | --- |
| asset-plateform/amp-order | codex/amp-order-nacos-upgrade | 原分支未推送，创建目标远程分支 | f696b160af8081f9a2090aca35ced64c2d5005af |
| asset-plateform/amp-saps | feature/nacos-alibaba-upgrade | 原子创建目标远程分支并删除旧远程分支 | 4ca0cd7b1db72503ccc8b4d163c63e139ca0a181 |
| IDEAWorkspace/thctay-public | feature/nacos-xxljob-upgrade | 原分支未推送，创建目标远程分支 | 3bb5e766c850ef43bde4bdb58de0eb211aaaabc9 |

三仓库的本地分支与 origin 同名分支提交一致，均已设置同名上游，旧本地分支与对应旧远程分支均不存在。通过变更前后的工作树 diff、暂存区 diff 及状态摘要校验，全部未提交改动保持原样。amp-order 和 thctay-public 的未提交代码仍仅在本地，本次没有创建提交。`xxt-plateform/thctay-public` 的另一份修复工作区未操作。

前述旧分支名称和“未推送”描述保留为历史验证记录，当前分支及远程状态以上述最新核验为准。
