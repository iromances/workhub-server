# WorkHub 需求归档指引

上级：[仓库指引](../../AGENTS.md)。需求与材料由 WorkHub 仓库统一维护，知识库仅保留研发规范、模板与工作流。

## 正式目录

- WorkHub 平台规格与验证证据：`docs/requirements/workhub-server/{需求编码}-{需求名称}/`。
- WorkHub 管理的业务线 SDD 与验证证据：`docs/requirements/业务线/{业务线编码}-{业务线名称}/{需求编码}-{需求名称}/`；具体业务代码仍在经核实的业务线仓库维护。
- 已确认自动评估：`docs/requirements/{业务线}/需求迭代/{项目名称}/{年份}/{审批编号}-{需求名称}/研发评估.md`。
- 原始需求、附件与来源解析：按 [原始材料指引](../requirement-sources/AGENTS.md) 归档。
- 自动分析、澄清回复与过程调整保留系统历史，并归入仓库 `docs/notes.md`；不写入知识库 `notes.md`。

## 归属与历史

- 建档前按编码和名称核对唯一正式目录，沿用既有需求编码、审批、历史版本和证据；不得在知识库重建副本。
- 跨 WorkHub 前后端的需求复用本目录，规格注明各仓库范围；不因迁移重置已确认状态。
- `历史版本/`、原始附件和快照按原字节保留；其中旧路径表示当时位置，现行路径见 [迁移清单](../migrations/2026-10-30.json)。历史指引快照不作为现行指令。
- Proposal、Design、Tasks 与 ADR 采用 [共用 SDD 流程](</Users/aslight/Documents/ObsidianMapping/Asset Obsidian Vault/schema/研发/SDD/流程.md>)，模板路径中的归档位置以本页为准。
