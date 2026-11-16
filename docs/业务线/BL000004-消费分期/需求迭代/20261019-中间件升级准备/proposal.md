# Proposal：参考 amp-saps 调整 amp-order

需求编码：REQ-428dadce-1c72-4d7d-9eb7-d4233554e6c7  
需求名称：参考 amp-saps 调整 amp-order  
业务线：BL000004／消费分期；GitLab 组：asset-plateform  
唯一需求目录：wiki/公司项目/研发需求/业务线/BL000004-消费分期/20261019-中间件升级准备  
涉及仓库：amp-saps（只读参考）、amp-order（实施）  
规格版本：v1.0（2026-10-23，用户已确认；本地实现及离线验证完成）  
当前研发分支：`feature/nacos-xxljob-upgrade_20260123`（2026-10-23 按用户要求完成本地及远程分支更名）


## 背景与范围

用户要求参考 IDEA 中的 amp-saps 调整 amp-order。IDEA 当前打开 amp-saps 的 feature/nacos-alibaba-upgrade 分支，最新提交 4ca0cd7b1db72503ccc8b4d163c63e139ca0a181，标题为“nacos升级”，只修改根 pom.xml 和启动模块 bootstrap.yml。本轮拟按此提交同步 Nacos 客户端及测试环境配置；用户于 2026-10-23 确认本轮即上述 Nacos 升级范围。

amp-order 当前 master，提交 f696b160af8081f9a2090aca35ced64c2d5005af，工作树干净。已从此基线新建 codex/amp-order-nacos-upgrade 分支，在本地开发；配置目标为 test，不进行远程联调或部署。

## 本期交付

1. amp-order 根 pom.xml 的 dependencies 添加 com.alibaba.nacos:nacos-client:2.5.4，与 amp-saps 一致。
2. amp-order-start/src/main/resources/bootstrap.yml 默认 profile 从 dev 改为 test，删除 dev-amp 段（amp-order 无 sit 段）。
3. test 的 config/discovery 地址和 username/password 逐项取自 amp-saps test 的对应值。凭据仅在本地配置文件中处理，不复制到规格、日志或聊天。
4. 保留 amp-order 的服务名称、端口、namespace、group、上传限制、日志及其余 profile 内容。

不涉及业务接口、数据库、生产配置或框架 BOM 变更。不安装软件或依赖；验证使用已安装工具及本地缓存。

## 验收标准

| 编号 | 条件 | 核对方式 |
| --- | --- | --- |
| AC-01 | Nacos 客户端直接依赖为 2.5.4，父依赖与 BOM 不变 | XML 解析与离线依赖树 |
| AC-02 | 默认 test、无 dev-amp，test 地址和鉴权项匹配参考提交 | YAML 解析和逐项断言（只输出匹配结果） |
| AC-03 | amp-order 服务身份、其余环境及其他配置保留 | YAML 结构对比和 Git diff |
| AC-04 | 静态检查通过并完成可用离线编译；缓存不足须如实记录 | git diff --check、离线 Maven |

## 统一确认结果

用户于 2026-10-23 回复“确认，按这个 Nacos 升级范围执行”，本规格已获确认。旧《消费分期Nacos升级迁移方案_20261022.md》写明“不新增账号密码”；本轮按已确认范围，以用户指定的 amp-saps 最新代码作为 test 配置依据。此差异只针对本轮测试配置，不改变原生产迁移口径。


## 2026-11-04 补充范围：public v2 同步到 v1 和 lease-public

- 沿用需求编码 `REQ-428dadce-1c72-4d7d-9eb7-d4233554e6c7`；补充版本 v2.0，唯一目录为本文件所在的 WorkHub 业务线需求目录。前述 v1.0 内容保留为历史记录。
- 用户原始指令：“public有个v2版本，帮我更新到v1版本还有lease-public那边”。本轮按已经存在的 v2 提交原样同步，未提出新的依赖升级方案。
- 来源：根目录 `thctay-public` 的远程 `release/nacos-2.5.4-xxljob-2.5.0_20261027-v2`，提交 `71494aa9133c1a973c2cf0a48ecc11004e31a6e8`。
- 目标：根目录 `thctay-public` 和 `lease-public` 的 `release/nacos-2.5.4-xxljob-2.5.0_20261027`。此处 v1 指无 `-v2` 后缀的原升级分支，不指 `master` 或构件版本 `0.0.1-SNAPSHOT`。
- 允许变更：两个 `*-dependencies/pom.xml` 中 Netty 版本与 BOM 管理；配套规格、验证及迭代记录。工作区变更不包含提交、推送、发布或安装构件。
- 环境：本地源码同步及离线验证；不读取生产 Nacos 配置。另两个组目录副本的 MQ 在途改动不纳入本轮。

| 验收 | 条件 |
| --- | --- |
| AC-N1 | thctay-dependencies 工作区文件与 v2 来源提交逐字节一致 |
| AC-N2 | lease-dependencies 使用相同的 Netty 4.1.140.Final 属性与首位 BOM 导入，其他内容保持原样 |
| AC-N3 | 两侧有效模型中本次 Netty BOM 覆盖的 Java 模块，以及升级版 schedule / MQ 实际解析的 Netty Java 模块，均为 4.1.140.Final |
| AC-N4 | Java 8 离线编译及 Git 空白检查通过；若原基线或缓存导致失败，明确记录原因与验证边界 |

## 2026-11-04 补充范围 v3.0 生产不停机迁移方案

沿用需求编码 REQ-428dadce-1c72-4d7d-9eb7-d4233554e6c7 和本目录，任务名称为“消费分期生产 Nacos 和 XXL-JOB 不停机迁移方案”。当前正式目录为 docs/业务线/BL000004-消费分期/需求迭代/20261019-中间件升级准备；前述旧路径、任务名及审批只代表相应历史阶段。

用户要求准备生产不停机平滑过渡方案，并确认可以临时部署完整新版本；在线业务不停机，定时任务允许短暂延后并保证补齐。沿用 amp-wolease 排除项，生产配置文件/内容读取限制继续有效。

交付 [生产不停机迁移方案](生产Nacos和XXLJOB不停机迁移方案_20261104.md)，覆盖两套应用并行、Nacos 配置及服务发现、跨业务线调用、灰度流量、共用 MQ 的应用发布检查、XXL-JOB 调度交接、补跑、回退及旧环境退出。用户已确认使用同一套 MQ，不安排独立 MQ 迁移或交接阶段。本轮只编写方案与交付记录，不修改应用代码或执行生产变更。

| 验收 | 条件 |
| --- | --- |
| AC-P1 | 在线入口保持可用，两套应用分别连接各自中间件，列明灰度与回退步骤 |
| AC-P2 | 新侧接流量前实际验证配置拉取与完整调用依赖，跨业务线有过渡方式 |
| AC-P3 | 任务交接前收敛在途、重试、父子链和回调，隔离旧侧触发，再启新侧 |
| AC-P4 | 按计划时间和业务批次补跑，结果不明先核实，确保业务幂等与完整性 |
| AC-P5 | 列明后台副作用、共用 MQ 的并行消费和实例正常关闭、回退兼容性及退出条件，区分历史快照与待核实生产事实 |

本轮已确认目标和资源条件。执行前按最终清单完成方案确认与生产发布授权，不将方案交付视为已部署或已授权生产变更。

## 2026-11-05 补充范围 v4.0：生产迁移辅助脚本

沿用本需求编码和归属。用户要求迁移时替换新 Nacos 中的 XXL-JOB 参数，并“照着老的任务一键启动”；提供《消费分期等保生产迁移.md》作为新入口和数据库的依据。授权范围为脚本准备与本地验证，生产执行另行安排；不读取其他生产配置正文、不安装依赖。

- AC-S1：仅按明确的 namespace / group / dataId 清单，在新 Nacos 替换 `xxl.job.admin.addresses` 和 `xxl.job.accessToken`；旧中心保持原状，密码和 token 不写入交付物。
- AC-S2：停用旧侧前保存业务任务定义摘要及启停状态，恢复时只启用快照中启用的任务，保留原 STOP 和子任务关系，排除示例及 amp-wolease。
- AC-S3：恢复前检查任务 ID、执行器 AppName、定义摘要、执行器注册与清单一致性；默认预览，显式执行后逐项回读；失败中止并保留进度，重跑跳过已经启用的任务。
- AC-S4：启用调用新中心正式控制台启用接口，由中心计算下次计划时间；不使用修改数据库状态代替、不立即触发业务任务、不自动补跑。
- AC-S5：本地模拟覆盖越界、定义漂移、原 STOP、部分成功和重复执行；交付说明区分本地验证与尚未执行的生产演练。Nacos 清单缺失时拒绝运行，不擅自扩大读取范围。
