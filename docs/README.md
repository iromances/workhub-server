# WorkHub 项目文档

研发产出保存在本仓库；共用研发规范、SDD 模板和工作流继续引用知识库。

- [需求归档指引](业务线/AGENTS.md)
- [MCP 能力总览](mcp-server.md)：运行时接入、工具清单、安全边界、文件读取白名单与配置
- [需求分析与排查笔记](notes.md)

历史原文、附件与历史版本保持原字节；其中旧路径表示记录当时位置，可按 git 历史定位。

### 本轮归档调整

- [研发产出回归项目仓库](业务线/BL000012-工作台/需求迭代/REQ-898b9c28-bad4-4a1e-ac14-4baad707814b-研发产出回归项目仓库/tasks.md)

### WorkHub项目文档

- [WorkHub 仓库指引](</Users/aslight/IDEAWorkspace/workhub-server/AGENTS.md>)：SDD 补充与文档归属
- [需求归档指引](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/AGENTS.md>)：正式目录、跨仓库归属与自动评估历史
- [BL000012-工作台 · 现状](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/BL000012-工作台/现状.md>)：WorkHub 平台（业务线 BL000012-工作台）当前系统事实
- [BL000012-工作台 · 迭代日志](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/BL000012-工作台/迭代日志.md>)：研发进展和验证记录
- [后端 AI 入口](</Users/aslight/IDEAWorkspace/workhub-server/AGENTS.md>) · [前端仓库指引](</Users/aslight/IDEAWorkspace/workhub-web/AGENTS.md>)
- [WorkHub 代码审查方法](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/schema/研发/代码审查方法.md>) · [WorkHub 业务运维分析方法](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/schema/研发/业务运维分析方法.md>)：适用于 WorkHub 及协作业务仓库
- [研发规范与模板索引](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/index.md#研发规范与模板>)

### WorkHub归档业务线

业务线目录统一为 `docs/业务线/{业务线编码}-{业务线名称}/`，含现状（`现状.md`）、迭代日志（`迭代日志.md`）与历史需求（`需求迭代/`）；支付中台（原共享支付系统）作为跨业务线公共系统按同样结构归档在 `docs/业务线/支付中台/`。已有实质现状的条目：

- [BL000003-嘉泰消费分期 · 现状](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/BL000003-嘉泰消费分期/现状.md>)
- [BL000004-消费分期 · 现状](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/BL000004-消费分期/现状.md>)
- [BL000005-智能柜 · 现状](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/BL000005-智能柜/现状.md>)

### 公司项目研发需求

- [物流平台、叉车、智能柜 Nacos 和 XXL-JOB 升级方案（2026-10-30）](业务线/BL000006-物流平台/需求迭代/REQ-9b086f9b-2e40-4ec6-b822-df8acd2ab6ba-物流平台叉车智能柜Nacos和XXLJOB升级方案整理/README.md)：三条业务线改动点、两套公共件、任务与直接调用清单、迁移回退和待落实项；方案草案，未实施。

- [202610120004 · 保费分期线下签约代码审查（2026-10-23）](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/BL000001-保费分期/需求迭代/聚保融保费分期项目/2026/202610120004-【保费分期】新增自营项目线下模式及【运营端】全局页面优化/代码审查-20261023.md>)：固定三个仓库提交，确认 5 项 P2；含复现步骤、验证证据及待核实事项。

- [保费分期入金空流水异常修复](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/BL000001-保费分期/需求迭代/REQ-c15a7da7-8d88-4b2d-b4f2-6d4e22ed7848-保费分期入金空流水异常修复/proposal.md>)：2026-10-21，仅 SAPS 保留用户确认的明确业务异常处理，7 项回归通过；Payment 改动已撤回，执行边界见 Tasks。

- [支付中台 R1 全历史代付复核](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/支付中台/需求迭代/20261019-代码审查与预警归类/上线以来代付交易全量复核-20261019.md>)：2026-10-19，现存 3504 笔代付全量筛查、87 笔失败逐笔取证；确认 6 月 23 日六笔系统异常被写为交易失败，涉及 28,610 元；同日再次提交成功，另查明智能柜审核状态与历史明细金额问题，未证实资金损失。 15:48 已核实 R1 核心修复进入 master，尚未取得生产部署证据。

- [支付中台 R1 生产影响复核](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/支付中台/需求迭代/20261019-代码审查与预警归类/生产影响复核-20261019.md>)：2026-10-19，近 7 天 167 笔代付、5 笔失败逐笔核查；未发现该缺陷造成错误失败传播，含渠道和调用方证据及核查边界。

- [支付中台代码审查与预警归类](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/支付中台/需求迭代/20261019-代码审查与预警归类/xxt-pay与intf-paychannel代码审查及预警归类.md>)：2026-10-19，远程 master 审查记录；2 项 P1、1 项 P2，489 条 xxt-pay 页面预警分类及验证证据，R1 核心异常分类修复已于 12:21 合入 master，生产部署待核实。

- [消费分期 Nacos 升级迁移方案](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/BL000004-消费分期/需求迭代/20261019-中间件升级准备/消费分期Nacos升级迁移方案_20261022.md>)：2026-10-22 v1.1；全新单节点 2.5.4 原生库通过迁移 SQL 导入 1.3.2 数据与历史，客户端升级，保持现有接入方式；含配置清单、代码核查、验收与联合回退。
- [消费分期 XXL-JOB 升级迁移方案](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/BL000004-消费分期/需求迭代/20261019-中间件升级准备/消费分期XXLJOB升级迁移方案_20261022.md>)：2026-10-22 v1.0；2.2.0 → 2.5.0，单节点、空 Token；含公共组件、86 个业务入口与公共 MQ、任务和数据库迁移；与 Nacos 同一约 2 小时窗口切换。

- [公司业务线中间件升级方案（新消费分期示例）](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/BL000011-新消费分期/需求迭代/20261014-中间件升级准备/公司业务线中间件升级方案_新消费分期示例_20261014.docx>)：2026-10-14 评审稿，13 页 Word；2026-10-19 从原生成记录恢复归档，保留原核查时点。
- [REQ-9209b10e-c3f9-41ac-b2fe-79f9b2cdd442-Codex模型能力动态加载](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/BL000012-工作台/需求迭代/REQ-9209b10e-c3f9-41ac-b2fe-79f9b2cdd442-Codex模型能力动态加载/proposal.md>)
- [REQ-718a4a66-bdd6-492d-9fe5-c771ca94b2a3-堡垒机共享连接按配置隔离](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/BL000012-工作台/需求迭代/REQ-718a4a66-bdd6-492d-9fe5-c771ca94b2a3-堡垒机共享连接按配置隔离/proposal.md>)
- [REQ-b6f5532c-e901-42f8-9d00-4ad0bd4f78cd-需求录入保存与AI识别解耦](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/BL000012-工作台/需求迭代/REQ-b6f5532c-e901-42f8-9d00-4ad0bd4f78cd-需求录入保存与AI识别解耦/proposal.md>)
- [REQ-896192ba-730a-481f-bc8d-c3c03ff2ab2e：统一知识库分层归档与重评历史保留（规格草案）](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/BL000012-工作台/需求迭代/REQ-896192ba-730a-481f-bc8d-c3c03ff2ab2e-统一知识库分层归档与重评历史保留/proposal.md>)
- [维护指引](</Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/AGENTS.md>)

