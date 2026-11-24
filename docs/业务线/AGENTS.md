# WorkHub 业务线归档指引

上级：[仓库指引](../../AGENTS.md)。WorkHub 平台和协作业务线按下列目录分别归档；研发规范与模板由所属代码仓库维护。

## 正式目录

- 业务线归档分两处，均按 `{业务线编码}-{业务线名称}/` 组织：
  - WorkHub 平台自身（业务线 `BL000012-工作台`）归档在本目录 `docs/业务线/BL000012-工作台/`。
  - 各协作业务线（BL000001~BL000011）与支付中台的归档已迁至同级仓库 `agents-playbook`（远端 `engineering/agents-playbook.git`），业务线材料在该仓库 `docs/业务线/{业务线编码}-{业务线名称}/`，支付中台在 `docs/支付中台/`；需求按其[文档指引](../../../agents-playbook/docs/AGENTS.md)建档。
- WorkHub 平台自身目录内：
  - 业务线现状：`现状.md`。
  - 研发进展：`迭代日志.md`。
  - 历史需求：`需求迭代/`，含 SDD（Proposal、Design、Tasks）、验证证据与已确认研发评估。
- 研发评估确认后由 WorkHub 按业务线编码自动归档：BL000012 在工作台目录下使用 `需求迭代/{项目名称}/{年份}/{审批编号}-{需求名称}/`；其余业务线在 `agents-playbook` 对应业务线目录下使用 `需求/{审批编号}-{需求名称}/`。协作仓库路径由 `knowledge.project/playbookPath` 配置。
- 已确认自动评估索引：WorkHub 平台为 `docs/业务线/index.md`（确认后自动生成）；其余业务线为 agents-playbook 仓库 `docs/业务线/index.md`。
- 需求原件与附件以数据库为准，AI 经 MCP `get_intake_detail` / `get_intake_attachment` 读取。查找已归档的文件时使用本仓库的[历史原始材料索引](../requirement-sources/index.md)，不随业务线归档根分流。
- 自动分析、澄清回复与过程调整保留系统历史，并归入仓库 `docs/notes.md`；不写入知识库 `notes.md`。
- 具体业务代码仍在经核实的业务线仓库维护；WorkHub 平台源码即本仓库。

## 归属与历史

- 建档前按编码和名称核对唯一正式目录，沿用既有需求编码、审批、历史版本和证据；不得在知识库重建副本。agents-playbook 仓库已有建档的业务线按其[需求归属与建档规则](../../../agents-playbook/docs/AGENTS.md#需求归属与建档)执行。
- 跨 WorkHub 前后端的需求复用对应归档目录，规格注明各仓库范围；不因迁移重置已确认状态。
- `历史版本/`、原始附件和快照按原字节保留；其中旧路径表示记录当时位置，可按 git 历史定位。历史指引快照不作为现行指令。2026-11-17 前各协作业务线材料位于本仓库 `docs/业务线/`，已按 git 历史定位迁出。
- Proposal、Design、Tasks 与 ADR 采用 [共用 SDD 流程](../研发规范/SDD/流程.md)，模板路径中的归档位置以本页为准。
