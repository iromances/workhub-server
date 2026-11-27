# WorkHub 业务线归档指引

上级：[仓库指引](../../AGENTS.md)。WorkHub 平台和协作业务线按下列目录分别归档；WorkHub 自身使用本仓库的研发规范与模板，公司业务线使用 [agents-playbook 指引](/Users/aslight/IDEAWorkspace/agents-playbook/AGENTS.md)及其研发规范与模板。

## 正式目录

- 业务线归档分两处，均按 `{业务线编码}-{业务线名称}/` 组织：
  - WorkHub 平台自身（业务线 `BL000012-工作台`）归档在本目录 `docs/业务线/BL000012-工作台/`。
  - 各协作业务线（BL000001~BL000011）与支付中台的归档已迁至同级仓库 `agents-playbook`（远端 `engineering/agents-playbook.git`），业务线材料在该仓库 `docs/业务线/{业务线编码}-{业务线名称}/`，支付中台在 `docs/支付中台/`；需求按其[文档指引](/Users/aslight/IDEAWorkspace/agents-playbook/docs/AGENTS.md)建档。
- WorkHub 平台自身目录内：
  - 业务线现状：`现状.md`。
  - 研发进展：`迭代日志.md`。
  - 历史需求：`需求迭代/`，含 SDD（Proposal、Design、Tasks）、验证证据与已确认研发评估。
- 研发评估确认后由 WorkHub 按业务线编码自动归档：BL000012 在工作台目录下使用 `需求迭代/{项目名称}/{年份}/{审批编号}-{需求名称}/`；其余业务线在 `agents-playbook` 对应业务线目录下使用 `需求/{审批编号}-{需求名称}/`。协作仓库路径由 `knowledge.project/playbookPath` 配置。评估前的各轮输入版本保存在同一需求目录的 `原始材料/`：`source.md` 为最近一轮，`历史版本/{版本ID}/` 为不可变版本（需求原文与附件清单），输入未变化时复用上一版本。
- 已确认自动评估索引：WorkHub 平台为 `docs/业务线/index.md`（确认后自动生成）；其余业务线为 agents-playbook 仓库 `docs/业务线/index.md`。
- 需求原件与附件以数据库为准，AI 经 MCP `get_intake_detail` / `get_intake_attachment` 读取；仓库内只保存需求原文版本和附件清单（附件 ID、大小、SHA-256），不保存附件文件。2026-11-25 前的原始材料位于本仓库 `docs/requirement-sources/`，该目录已下线，历史可按 git 历史定位。
- 自动分析、澄清回复与过程调整只保留数据库中的历次分析记录和需求历史，不写入 `docs/notes.md` 或知识库 `notes.md`；未确认草稿不生成文件，确认后才发布 `研发评估.md`。
- 具体业务代码仍在经核实的业务线仓库维护；WorkHub 平台源码即本仓库。

## 归属与历史

- 建档前按编码和名称核对唯一正式目录，沿用既有需求编码、审批、历史版本和证据；不得在知识库重建副本。agents-playbook 仓库已有建档的业务线按其[需求归属与建档规则](/Users/aslight/IDEAWorkspace/agents-playbook/docs/AGENTS.md#需求归属与建档)执行。
- 跨 WorkHub 前后端的需求复用对应归档目录，规格注明各仓库范围；不因迁移重置已确认状态。
- `历史版本/`、原始附件和快照按原字节保留；其中旧路径表示记录当时位置，可按 git 历史定位。历史指引快照不作为现行指令。2026-11-17 前各协作业务线材料位于本仓库 `docs/业务线/`，已按 git 历史定位迁出。
- WorkHub 自身的 Proposal、Design、Tasks 与 ADR 采用[本仓库 SDD 流程](../研发规范/SDD/流程.md)，模板路径中的归档位置以本页为准；公司业务线采用 [agents-playbook SDD 流程](/Users/aslight/IDEAWorkspace/agents-playbook/docs/研发规范/SDD/流程.md)及其模板，按该仓库文档指引维护唯一正式需求目录。
