# WorkHub Windows 原生运行与跨平台 JAR 部署评估

核对日期：2026-11-27。范围包括 `workhub-server` 和同级前端仓库 `workhub-web`。本文依据源码和构建配置整理。Windows IDEA、Windows 服务和 Linux 服务的实机验收均待执行。

目标是让开发人员在 Windows 上直接用 IDEA 调试，并让 Windows、Linux 服务器部署同一个后端 JAR。现有 Maven 工程可以沿用。要达到完整功能一致，主要工作集中在业务仓库路径、研发归档、AI CLI、数字员工 Git 和堡垒机 SSH。尤其要在脱离 IDEA 源码目录、改用服务账号后验证这些功能。

## 已实现的启动配置

Windows IDEA 选择 JDK 25，以 `workhub-bootstrap` 运行启动类，设置 `SPRING_PROFILES_ACTIVE=dev`。Windows、Linux 服务选择 `prod`，使用同一后端 JAR。前端独立托管 `dist/`，通过反向代理将同源 `/api` 请求交给后端。两种启动方式都从环境变量或 Spring 外部配置读取数据库连接。

IDEA 的工作目录设为后端仓库根，Active profiles 选择 `dev`。开发库连接直接维护在 `workhub-bootstrap/src/main/resources/application-dev.yml`。部署 JAR 时使用 `prod`，生产连接在同目录的 `application-prod.yml` 配置，默认从 `WORKHUB_DB_URL`、`WORKHUB_DB_USERNAME`、`WORKHUB_DB_PASSWORD` 读取。连接池与 Flyway 复用当前环境的连接。开发配置随 JAR 打包，其中包含开发库连接信息。

| 配置 | 如何填写 |
| --- | --- |
| 数据库 | `WORKHUB_DB_URL`、`WORKHUB_DB_USERNAME`、`WORKHUB_DB_PASSWORD`。已有部署显式配置的 `spring.datasource.druid.*` 也可继续使用。首次迁移使用数据库副本核对 Flyway。 |
| 生产密钥 | `WORKHUB_JWT_SECRET`、`WORKHUB_PAYMENT_MASTER_KEY`、`WORKHUB_MCP_MASTER_KEY`。已有密文使用原主密钥，使用 AI 加密配置时也提供原 `WORKHUB_AI_MASTER_KEY`。 |
| 公司仓库 | `WORKHUB_REPOSITORIES_ROOT` 可填绝对路径；默认使用实际 JAR 或 IDEA 工程根下的 `cache/repos/`。按 GitLab 的组、子组、项目检出。 |
| 需求归档 | `WORKHUB_ARCHIVE_PROJECT` 填公司 GitLab 上 WorkHub 后端的完整项目路径。协作仓库默认 `engineering/agents-playbook`。首次写入可使用已维护的 `gitlab.global/webApiUrl` 和 `accessToken` 克隆。实例绝对路径 `WORKHUB_ARCHIVE_PATH`、`WORKHUB_PLAYBOOK_PATH` 优先于数据库中的 `repositoryPath`、`playbookPath`。 |
| 规范 | 默认 `config/standards/`，可通过 `WORKHUB_STANDARDS_ROOT` 单独覆盖。将自定义的 `流程.md`、`proposal.md`、`design.md`、`tasks.md`、`adr.md` 放在其 `研发规范/SDD/` 子目录。 |
| GitLab 规范来源 | 可选 `WORKHUB_STANDARDS_PROJECT`。分支 `WORKHUB_STANDARDS_REF` 默认 `HEAD`，仓库内目录 `WORKHUB_STANDARDS_PATH` 默认 `docs/研发规范/SDD`。按提交号缓存至规范根下的 `.gitlab/`。同名文件优先使用手工维护版。配置的远端读取失败时本次评估报告错误。 |
| Git、AI 与 SSH | Git 使用 `WORKHUB_GIT_EXECUTABLE` 或账号 PATH。Codex、ZCode 在 AI 接入配置中填写可执行程序，ZCode 的 CJS 入口同时填写 Node 运行时。Windows 堡垒机使用 JSch，须在运行账号的 `.ssh/known_hosts` 预置已核实的主机公钥，非默认端口使用 `[host]:port`。 |
| 打开文件夹 | `workhub.desktop.open-folder-enabled` 在 dev 开启，在 prod 和未选环境时关闭。开发模式按归档位置打开文件夹，显式的材料目录 `intake.requirementFolder/basePath` 优先。 |

研发评估与澄清会在仓库根的 `.workhub-analysis/<快照编号>/` 下读取固定版本。目录中的 `revisions.json` 记录各项目及提交号。指定开发分支时，参与分析的仓库须提供对应远端分支。新任务可以读取更新后的代码和规范，在途任务继续使用原有快照。归档仓库可能持有未提交的需求材料，备份或清理时按项目处理。

本机已从与 JAR 不同的目录启动 dev 和 prod，并用独立 JAR 内的归档服务验证了两类需求。2026-11-28 的四项遗留失败已修复，完整测试范围与回归结果见[本次验证记录](需求迭代/REQ-03bc3394-b120-4e0c-9124-acf929d7ad90-Windows原生运行与跨平台JAR部署/tasks.md)。下文保留迁移前的问题定位及目标机验收表。

## 1. 三种运行方式

| 场景 | 构建或启动方式 | 运行环境要点 |
| --- | --- | --- |
| Windows 原生 IDEA | 打开后端根 `pom.xml`，Project SDK 和 Maven 运行 JDK 均选 25。以 `workhub-bootstrap` 为启动模块，运行 `cn.aslight.workhub.WorkhubServerApplication` | 工作目录设为后端仓库根。Git、SSH、AI CLI、私钥和登录状态均由启动 IDEA 的 Windows 用户提供 |
| Windows 部署 JAR | 将构建产物复制到部署目录，用 Java 25 启动。需要常驻时，以指定 Windows 服务账号运行 | 显式指定工作目录、外部配置、日志和可写数据目录。服务账号需要自己的工具路径、凭据和目录权限 |
| Linux 部署 JAR | 使用同一个 JAR 和 Java 25，可交由 systemd 管理进程 | 用实际服务账号配置工作目录、HOME、PATH、时区和权限，持久化研发文档与工作区 |

从后端仓库根构建，产物位于 `workhub-bootstrap/target/`。以下文件名对应当前项目版本。[根构建配置](../../../pom.xml) · [启动模块构建配置](../../../workhub-bootstrap/pom.xml) · [应用入口](../../../workhub-bootstrap/src/main/java/cn/aslight/workhub/WorkhubServerApplication.java)

```shell
mvn -pl workhub-bootstrap -am clean package
```

将可执行 JAR 放入目标机部署目录，确认目标数据库和外部配置后，从该目录启动。

```shell
java -jar workhub-bootstrap-0.1.0-SNAPSHOT.jar
```

**交付方式已确认：前后端分开部署。** 后端发布可执行 JAR，前端独立发布静态产物 `dist/`。 Windows 开发机按 `package-lock.json` 准备对应平台的前端依赖，运行 `npm run dev`。默认端口为 9091，`/api` 代理到本机后端 8080。服务器部署时执行 `npm run build`，托管 `dist`，转发 `/api`，并给前端 history 路由配置刷新回退到 `index.html`。上传代理限额需覆盖后端的单文件 20 MB、单请求 50 MB。[前端构建](../../../../workhub-web/package.json) · [开发代理](../../../../workhub-web/vite.config.ts) · [前端路由](../../../../workhub-web/src/router/index.ts)

## 2. 保留完整功能所需的代码改造

### 代码缓存与规范目录

建议默认创建两个平级的外部目录。IDEA 运行时放在 `workhub-server/` 根目录下；以 JAR 运行时放在实际 JAR 所在目录下，即使从其他工作目录启动也能定位到相同位置。每个目录都允许用户单独指定绝对路径。显式指定的路径优先，仅在未指定时使用默认值。若选定位置无法创建或访问，错误信息应显示解析后的路径。

| 目录 | 默认位置 | 使用方式 |
| --- | --- | --- |
| 公司仓库根目录 | `cache/repos/` | 所有仓库按 GitLab 返回的完整项目路径（`path_with_namespace`）检出到 `<仓库根>/<组>/<可选的多级子组>/<项目>/`。同步、AI 分析、MCP 和归档均使用同一路径，保留各组下的同名项目。AI 分析前按任务选择项目和分支，首次克隆，后续增量获取更新并记录本次读取的提交号。需要改代码时，从该提交创建独立任务工作区 |
| 规范配置 | `config/standards/` | 可以放用户维护的规范文件，也可以从指定的 GitLab 项目、分支和路径同步规范。用户直接维护的文件由用户更新，从 GitLab 同步的文件由远端版本更新，避免同步覆盖本地定制。每次任务开始时读取当时的规范内容，修改保存或同步成功后，下一个任务即可使用，无需重启 WorkHub；进行中的任务沿用开始时的版本 |

例如，JAR 位于 `D:/apps/workhub/workhub.jar` 时，默认代码缓存是 `D:/apps/workhub/cache/repos/`，规范目录是 `D:/apps/workhub/config/standards/`。用户只指定规范目录为 `D:/team/standards/` 时，代码缓存仍使用默认位置。正式研发评估、审批材料和在途任务修改各自持久保存，清理代码缓存仅影响可从 GitLab 重建的副本。

### 独立 JAR 的需求归档

WorkHub 自身需求写入 `workhub-server` 仓库，其他业务线的需求写入 `agents-playbook` 仓库。给定归档项目的 GitLab 完整路径后，只需指定一次公司仓库根目录。例如仓库根设为 `D:/team/repos/` 时，协作归档位于 `D:/team/repos/engineering/agents-playbook/docs/`。本机 WorkHub 的 origin 指向 GitHub，部署时按公司 GitLab 中实际的组和子组定位 WorkHub。已有部署仍可显式指定归档位置，沿用历史文件和链接。

| 需求归属 | 独立 JAR 默认文档根 | 根目录内沿用的需求路径 |
| --- | --- | --- |
| WorkHub 平台 | `cache/repos/<WorkHub 的 GitLab 组路径>/workhub-server/docs/`（随公司仓库根目录调整） | `业务线/BL000012-工作台/需求迭代/{项目}/{年份}/{审批编号}-{需求名称}/` |
| 其他业务线 | `cache/repos/engineering/agents-playbook/docs/`（随公司仓库根目录调整） | `业务线/{业务线编码}-{名称}/需求/{审批编号}-{需求名称}/` |

显式指定的文档根优先；迁移已有实例时，继续识别 `knowledge.project/repositoryPath` 和 `playbookPath`。IDEA 打开已有 `repos/<组路径>/workhub-server` 时，将各顶层组共同所在的 `repos/` 指定为仓库根，即可复用当前检出。独立 JAR 首次归档时，按需求归属复用对应仓库，缺少时从配置的 GitLab 来源克隆。清理缓存时保留两个归档仓库；同步时保留其中尚未提交的材料。文档根按归档边界和读写权限验证，JAR 可以从源码仓库以外的目录启动。

评估时从规范目录读取适用的 SDD 流程与模板；归档、重试以及 MCP 文档定位共用同一套文档根解析。各需求的原始材料版本、已确认评估和 SDD 文档沿用现有发布规则。正式文档根与可清理缓存、规范目录及只读知识库保持隔离。

### 各功能的适配范围

| 功能 | 当前实现与影响 | 建议改造 |
| --- | --- | --- |
| GitLab 仓库同步和 MCP 代码读取 | 同步服务、两处 MCP 上下文写有 `/Users/aslight/IDEAWorkspace`。同步服务查询子组项目后，仍按查询组和项目短名拼接本地目录，子组层级会丢失。单独调整读取白名单后，克隆目标和 AI 获得的目录仍可能指向旧机器 | 新增实例级业务仓库根目录，供仓库同步、上下文和工具说明共用。保留独立的 MCP 只读白名单，并验证两者指向同一批授权仓库。[GitLab 同步](../../../workhub-service/src/main/java/cn/aslight/workhub/service/intake/GitlabRepositoryService.java) · [MCP 运行时](../../../workhub-service/src/main/java/cn/aslight/workhub/service/mcp/McpRuntimeService.java) · [MCP 工具注册](../../../workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/tool/McpToolRegistry.java) |
| 研发归档和 SDD 规范 | 当前归档根校验要求同时存在 `pom.xml`、`workhub-service`、`workhub-bootstrap`。只复制 JAR 时，登录可能成功，但评估归档或读取规范会失败 | 按上节的独立文档根解析归档目标，从规范目录读取输入，保留既有业务线分流、历史目录兼容和路径边界检查。[归档解析](../../../workhub-service/src/main/java/cn/aslight/workhub/service/intake/RepositoryDocumentPaths.java) |
| ZCode、Codex | ZCode 任务显式限制为 macOS/Linux，并使用 POSIX 权限保护任务文件。Codex 部分入口按文件原名查找程序，子进程环境变量经过筛选 | 支持 Windows 的实际可执行入口、含空格的程序路径及必要环境变量，用 NTFS ACL 实现任务目录与凭据隔离。Linux 沿用私有目录权限。分别验证模型列表、需求分析和数字员工任务。[ZCode](../../../workhub-service/src/main/java/cn/aslight/workhub/service/ai/ZCodeCliClient.java) · [Codex 进程](../../../workhub-service/src/main/java/cn/aslight/workhub/service/agent/CodexAgentCliProtocol.java) |
| 数字员工 Git 工作区 | 创建私有工作区使用 POSIX 权限，Git 子进程固定 Unix PATH 并引用 `/dev/null` | 按宿主机选择可信 Git 程序、私有目录权限和禁用全局配置及 hooks 的方式。验证创建、读取、生成变更和清理。[Git 工作区实现](../../../workhub-service/src/main/java/cn/aslight/workhub/service/agent/AgentCodeGit.java) |
| 堡垒机、数据库隧道和服务器诊断 | 共享连接依赖 `/tmp`、POSIX 权限及 OpenSSH 控制套接字，密码认证调用 `sshpass`。部分远端日志路径通过宿主机 Java `Path` 解析，原生 Windows 下会改变对 `/mnt/...` 的解释 | 提供可在 Windows 运行的 SSH 连接和端口转发实现，保留密钥与密码认证、配置隔离、连接复用、超时回收。远端 Linux 路径始终按 POSIX 规则校验。[SSH 连接](../../../workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/ssh/OpenSshConnection.java) · [堡垒机测试入口](../../../workhub-service/src/main/java/cn/aslight/workhub/service/mcp/McpBastionConnectionTester.java) · [远端文件策略](../../../workhub-mcp-server/src/main/java/cn/aslight/workhub/mcp/security/ServerFilePolicy.java) |
| 附件识图 | 识图前会把数据库中的图片写入临时目录。附件原名含冒号等字符时，Windows 上可能无法创建临时文件 | 支持图片的 AI API 直接处理内存中的图片字节，省去本机临时文件。现有 CLI 的文件路径依赖见下节。[附件临时文件](../../../workhub-service/src/main/java/cn/aslight/workhub/service/attachment/AttachmentTemporaryFileSet.java) |
| 打开目录 | “打开需求文件夹”和“打开方案文件夹”在后端进程所在机器调用桌面程序 | 后端从应用配置文件读取 `workhub.desktop.open-folder-enabled`。按 Spring Profile 提供两份环境配置：开发环境 `application-dev.yml` 设为 `true`，生产环境 `application-prod.yml` 设为 `false`。公共配置 `application.yml` 保留关闭的兜底值。IDEA 本地 Run/Debug 配置使用环境变量 `SPRING_PROFILES_ACTIVE=dev`，Windows 和 Linux 生产服务均使用 `SPRING_PROFILES_ACTIVE=prod`。同一份 JAR 跟随所选环境，未指定环境时默认关闭。启用时按需求 ID 和业务线定位前文约定的本机需求归档目录；“打开需求文件夹”兼容用户显式指定的 `intake.requirementFolder/basePath`，“打开方案文件夹”跟随实际方案的归档位置。关闭时前端隐藏两个入口，后端在创建目录或调用桌面程序前拒绝打开请求。开关仅控制桌面操作，材料导出和需求归档继续按原流程运行。[需求文件夹](../../../workhub-service/src/main/java/cn/aslight/workhub/service/intake/RequirementFolderService.java) · [方案文件夹](../../../workhub-service/src/main/java/cn/aslight/workhub/service/intake/DevelopmentPlanFolderService.java) |

测试也要随实现迁移。涉及 Mac 绝对路径、`/tmp`、`#!/bin/sh` 和 POSIX 文件权限的用例，可分别改用临时目录、可跨平台的测试程序和针对目标文件系统的权限断言。[ZCode 测试示例](../../../workhub-service/src/test/java/cn/aslight/workhub/service/ai/ZCodeCliClientTest.java)

### 附件识图在内存中处理

- 新上传的图片原件存入数据库。识图场景配置支持图片输入的 AI API，WorkHub 读取图片字节，在内存中将其编码成 API 请求所需的 Base64 数据，再发给模型。识图入口与其他复用同一图片输入流程的 API 场景省去创建和读取临时图片的步骤。附件名称用于展示和下载。[附件存储](../../../workhub-service/src/main/java/cn/aslight/workhub/service/attachment/AttachmentService.java#L243) · [需求录入识图](../../../workhub-service/src/main/java/cn/aslight/workhub/service/intake/CodexCliStructuredExtractor.java#L110) · [API 图片编码](../../../workhub-service/src/main/java/cn/aslight/workhub/service/ai/AiHttpPayloadSupport.java#L84)
- 当前 Codex 通过 `-i 文件路径` 接收图片，ZCode 通过 `localPath` 接收图片。需要本机无图片缓存的识图场景使用 API。将来若要让 CLI 也直接接收图片字节，先验证对应 CLI 协议支持，再适配 WorkHub。[Codex 图片参数](../../../workhub-service/src/main/java/cn/aslight/workhub/service/intake/CodexCliClient.java#L194) · [ZCode 图片参数](../../../workhub-service/src/main/java/cn/aslight/workhub/service/ai/ZCodeTaskConversation.java#L383)
- 若要求从浏览器上传到识图结束全程不写本机磁盘，还要单独检查应用的 multipart 请求解析和反向代理的请求体缓冲。历史附件的 `storage_path` 按下文的存量数据方案处理。

### Windows 原生 AI CLI 和 Git 修复方案

Codex、ZCode、GitLab 同步和数字员工 Git 工作区纳入同一次交付。验收从 Windows IDEA 中的仓库同步一直覆盖到 Windows、Linux 服务账号下的分析与代码变更。各部署可以按需启用 CLI，兼容性验收对 Codex 和 ZCode 分别执行。

| 改造点 | 约定 |
| --- | --- |
| 统一解析程序 | 显式配置的完整路径优先，否则从 WorkHub 运行账号的 `PATH` 查找。Windows 对 `git`、`codex` 等程序名识别对应的 `.exe`，Codex、ZCode 的旧配置也按实际文件名正确识别 CLI 类型。ZCode 同时支持原生程序及 Node 加 `zcode.cjs`，后者把运行时和脚本作为独立参数传入。[CLI 类型识别](../../../workhub-service/src/main/java/cn/aslight/workhub/service/ai/AiCliType.java) |
| 统一启动方式 | 模型列表、连接测试、只读分析和数字员工任务共用程序解析规则，通过 `ProcessBuilder` 分别传入程序和参数，支持中文及带空格的路径。预检与实际任务使用相同的运行账号和对应的环境策略。Windows 按大小写不敏感的方式识别 `Path` 等环境变量，按用途传入 `SystemRoot`、临时目录、证书和相应认证配置，避免把无关业务凭据带入子进程 |
| 私有任务目录 | 在复制配置或认证材料前，先创建并验证服务账号的私有根目录。Windows 使用 NTFS ACL，Linux 延续目录 0700、文件 0600。隔离模式下将用户目录、会话库和临时文件指向本次任务；权限不满足时，终止该任务并报告具体目录 |
| GitLab 同步与提交号读取 | 同步服务、分析时的提交号读取和数字员工使用同一 Git 程序。同步保留 GitLab 完整组路径与本地修改。进程运行期间持续排空输出并限制保留量；发生超时先终止并回收进程树，再结束输出读取和清理，防止输出管道阻塞。[仓库同步](../../../workhub-service/src/main/java/cn/aslight/workhub/service/intake/GitlabRepositoryService.java) |
| 数字员工 Git | 保留独立 Git 对象库、基线提交校验和授权路径限制。使用按平台构造的受控环境、WorkHub 管理的空 Git 配置文件及空 hooks 目录，替换硬编码的 Unix PATH 和 `/dev/null`。修改只进入任务工作区，从该工作区生成差异，保留源仓库和归档仓库已有的未提交材料 |

## 3. 换机配置与存量数据

| 项目 | 迁移时核对的内容 |
| --- | --- |
| 主机目录 | 需求文件夹配置为 `intake.requirementFolder/basePath`。归档和知识库配置在 `knowledge.project` 下，包括 `repositoryPath`、`playbookPath`、`vaultPath`。MCP 本地读取白名单为 `WORKHUB_MCP_REPOSITORY_READ_ROOTS`。业务仓库根可按 Windows 的 `D:/workhub/repos` 或 Linux 的 `/srv/workhub/repos` 规划。两台主机共用数据库时，建议对宿主机路径增加实例级覆盖，以免互相覆盖配置 |
| 文件与任务 | 保存前后端未提交变更、WorkHub 和协作业务线的归档，以及仍需使用的 Agent 工作区。核对 `agent.code/policy` 中的仓库根与源目录。目录调整会改变策略哈希，旧工作区可能需要重新分派，切换前应先收尾在途任务 |
| 历史附件 | `pm_attachment.file_content` 为空时，下载仍读取旧 `storage_path`。旧机器下线前使用已有回填工具的默认只读预览，核对待回填数和缺失文件，再安排回填或文件搬迁。验收同时抽取新旧附件。[附件读取](../../../workhub-service/src/main/java/cn/aslight/workhub/service/attachment/AttachmentService.java) · [回填工具](../../../workhub-bootstrap/src/main/java/cn/aslight/workhub/maintenance/AttachmentContentBackfillCli.java) |
| 数据库与密钥 | 数据库按 dev/prod 的 YAML 配置，支持环境变量覆盖；默认启用 Flyway。首次启动先连数据库副本。复用已有库时需核对 AI、MCP、支付加密主密钥；需要延续现有登录会话时核对 JWT 密钥。[应用配置](../../../workhub-bootstrap/src/main/resources/application.yml) |
| 服务账号与网络 | 核对日志和临时目录的读写权限、Asia/Shanghai 时区、Git、SSH 私钥、已启用的 AI CLI 及其登录状态。ZCode 使用 `.cjs` 分发时还需 Node 和 Provider 配置。分别验证 MySQL、GitLab、禅道、Jenkins、ELK 和堡垒机的实际连通性。读取远端服务器文件所需的 Python 3 位于被诊断的目标服务器 |
| 定时任务切换 | 当前动态调度用 JVM 内的 `AtomicBoolean` 防止同一进程重复执行。验收实例使用隔离库；正式切换时先停旧实例调度，再启新实例并检查执行记录。后续需要多实例时，再引入跨实例互斥或独立调度节点。[动态调度实现](../../../workhub-service/src/main/java/cn/aslight/workhub/service/system/DynamicJobScheduler.java) |

### 部署环境清单

以下工具由部署人员在运行 WorkHub 的机器上准备。通过 IDEA 启动时，以启动后端的开发账号核对。通过服务运行时，以服务账号及其实际的 `PATH`、用户目录和凭据核对。Codex、ZCode 按启用的 CLI 通道准备。使用 API 通道的场景配置 API 地址、凭据和模型即可。

| 环境项 | 原生 Windows（IDEA 或 JAR） | Linux（JAR） |
| --- | --- | --- |
| Java（必需） | IDEA 使用 JDK 25，JAR 使用 Java 25。用 `java -version` 核对启动环境 | 服务账号使用 Java 25，用 `java -version` 核对 |
| Git（仓库同步、提交号读取和数字员工 Git） | 准备 Windows 版 Git，将 `git.exe` 所在目录加入 WorkHub 进程的 `PATH`，用 `Get-Command git` 和 `git --version` 核对 | 准备 Linux 版 Git，让服务账号在 `PATH` 中找到 `git`，用 `command -v git` 和 `git --version` 核对 |
| Codex CLI | 准备 Windows 可用的 CLI 入口，让 WorkHub 进程能找到它，或在 AI 接入配置中指定完整路径。用 `Get-Command codex` 和 `codex --version` 检查命令，并给运行账号配置认证。原生 Windows 的 WorkHub 调用仍需验收 | 准备 Linux 版 CLI，通过服务账号的 `PATH` 或 AI 接入配置中的完整路径调用。用 `command -v codex` 和 `codex --version` 检查命令，并给服务账号配置认证 |
| ZCode CLI | 启用前先准备可在 Windows 控制台执行的 `zcode` 入口和运行账号的 Provider 配置及认证。用 `Get-Command zcode` 和 `zcode --help` 检查入口。当前 WorkHub 的任务隔离仍需完成 Windows 适配并通过应用内验收 | 让服务账号能直接执行 `zcode`，用 `command -v zcode` 和 `zcode --help` 检查入口，也可以在 AI 接入配置里填可执行文件完整路径。准备内置 Provider 定义、服务账号的个人 Provider 配置及认证 |

Git 的拟采用行为是默认从进程的 `PATH` 查找 `git.exe` 或 `git`。部署人员显式指定完整路径时优先使用该路径。GitLab 同步、AI 读取提交号和数字员工 Git 共用同一个解析结果。这项统一解析属于上面的代码改造范围。

ZCode 沿用现有 Mac 的使用方式。控制台直接输入 `zcode` 能启动交互界面时，如果 `zcode` 是 WorkHub 进程也能从 `PATH` 找到的实际命令，AI 接入配置的 CLI 命令就填 `zcode`。内置 Provider 定义仅在代码识别到分发包布局时自动定位，其余情况填写文件完整路径。个人 Provider 配置默认从运行账号的用户目录读取 `.zcode/v2/provider_config.json`，也可显式指定。使用 `zcode.cjs` 分发时，将该文件的完整路径填入 CLI 命令，并准备 Node.js。控制台可启动是环境预检，部署验收还要在 WorkHub 中加载模型并执行一次已启用的 CLI 任务。[ZCode 命令与 Provider 配置](../../../workhub-service/src/main/java/cn/aslight/workhub/service/ai/ZCodeCliClient.java#L228) · [ZCode 任务隔离](../../../workhub-service/src/main/java/cn/aslight/workhub/service/ai/ZCodeCliClient.java#L78) · [Codex 命令解析](../../../workhub-service/src/main/java/cn/aslight/workhub/service/ai/CodexModelCatalogService.java#L92)

## 4. 验收标准

Windows IDEA 使用源码运行。另取同一次构建生成的 JAR，分别在 Windows、Linux 的干净部署目录下，用计划投入使用的服务账号启动。以下项目在三个环境逐项留存结果。

- [ ] **启动与页面**：确认 Java 25、目标数据库、Flyway 结果及实际日志目录；独立部署前端 `dist/` 和后端 JAR，从前端入口以普通用户登录，通过同源 `/api` 请求读取需求详情，直接刷新详情页仍能打开。
- [ ] **附件与归档**：上传中文名图片并完成 AI 识图，下载一份历史附件并核对内容；各评估并确认一条 WorkHub 自身需求和一条其他业务线需求，核对归档仓库、材料和文档链接。
- [ ] **API 识图无本机临时图片**：在 Windows 和 Linux 上使用已存入数据库的图片发起 API 识图，核对结果、原名与下载内容。监测识图期间的文件创建，确认任务成功或失败时都没有写入本机临时图片。若验收覆盖上传全链路，再检查上传请求解析和代理缓冲是否产生临时文件。
- [ ] **独立 JAR 归档**：Windows 和 Linux 各在无 WorkHub 源码检出的目录启动 JAR，完成两类需求归档。仅覆盖公司仓库根时，核对两类需求分别落在新根的 `<WorkHub 的 GitLab 组路径>/workhub-server/docs/` 和 `engineering/agents-playbook/docs/`。同步仓库、清理业务代码缓存、更换 JAR 并重启后，核对两个仓库中未提交的材料、历史版本及已确认评估仍可读取，归档重试仍定位到同一需求。
- [ ] **AI CLI 和 Git 联合验收**：在 Windows 原生 IDEA、Windows 服务和 Linux 服务三个环境，用同一版本代码验证完整流程，两台服务部署同一份 JAR。同步不同 GitLab 子组的同名项目，核对本地路径和提交号；Codex、ZCode 分别完成模型加载、连接测试和指定提交的只读分析；数字员工在独立工作区生成修改并查看差异。核对源仓库及归档仓库已有的未提交材料仍然保留。
- [ ] **程序路径、权限与超时**：分别验证自动发现和显式路径，覆盖中文及含空格的目录；检查任务文件对无关普通账号不可读；让测试子进程持续输出并触发超时，确认进程树回收、任务正常结束并报告原因。三种环境的联合验收和这些边界用例全部通过，才认定 AI CLI 和 Git 适配完成。
- [ ] **默认目录与自定义目录**：从不同工作目录启动同一 JAR，确认复用默认缓存；分别只指定代码缓存或规范目录，确认另一项仍使用默认位置。向测试分支提交一次变更并更新一份规范，确认下一次分析使用新提交和新规范，在途任务仍使用原版本。
- [ ] **运维链路**：按启用的认证方式连接堡垒机，经隧道执行一次只读查询，读取授权远端目录中的日志；验证白名单外路径被拒绝，以及连接结束后的回收。
- [ ] **打开本机文件夹**：IDEA 激活 `dev`，分别用 Run 和 Debug 验证两个按钮打开预期的需求目录和方案目录，覆盖自定义路径。在 Windows 和 Linux 上用同一份 JAR 激活 `prod`，并验证未指定 Profile 时的兜底行为：两个入口均隐藏，直接请求打开接口也无法创建目录或启动桌面程序，材料导出和需求归档正常。
- [ ] **服务行为**：重启后验证已有密文仍可解密、历史附件可读、研发归档仍在，并确认一个调度周期内任务按预期执行一次。
