# Tasks · Windows原生运行与跨平台JAR部署（REQ-03bc3394-b120-4e0c-9124-acf929d7ad90）

工作台（BL000012） · workhub-server、workhub-web · v1.0 · 2026-11-27

[用户确认的方案及完整验收表](../../Windows原生运行与跨平台JAR部署评估.md)

需求来源为本次聊天。用户于 2026-11-27 确定前后端分开部署并指示按上述方案执行开发，同日授权增加并获取 mwiede/JSch。分支为两个仓库现有 codex/development-20261122。

| 范围 | 本机验证结果 |
| --- | --- |
| 仓库、代码版本和规范 | 本地 Git 两个子组均包含同名项目，含空格与中文路径。指定分支生成独立快照，相同版本可复用，缺少分支时报错，源仓库的未提交修改保留。模拟 GitLab HTTP 响应验证提交锁定及规范手工覆盖。 |
| 独立 JAR 归档 | 在源码目录外加载实际发布 JAR，工作目录与 JAR 目录不同。默认目录、只覆盖仓库根、只覆盖规范根三组，均验证 WorkHub 与 agents-playbook 各自落盘，重复归档复用同一版本。使用临时 Git 检出。 |
| CLI、Git 和图片 | 运行已安装工具可执行的本机协议、文件权限与工作区回归。两种 API 直接编码内存图片，CLI 的 Windows 非法原名转换和临时文件清理通过测试。 |
| MCP 与 SSH | MCP 路径白名单、远端 Linux 路径及共享连接生命周期回归。使用临时本机 SSH 服务和可信临时密钥，JSch 密钥认证、回环转发和拒绝未知主机指纹验证通过。 |
| 前端与桌面 | 独立前端类型检查及生产构建通过。后端测试验证开关关闭时两个 POST 在调用目录服务前即被拒绝。 |
| JAR 启动 | 独立临时 MySQL、只监听回环、13 项业务调度关闭；JAR 放在带空格的中文路径，从另一个目录启动，dev 和 prod 均就绪，`/api/system/ping` 返回 HTTP 200。此启动测试关闭 Flyway，只使用调度所需的测试表。 |

## 2026-11-27 测试结果与边界

- 本机专项 Maven 回归共 233 项，224 项通过，9 项跳过，命令退出码为 0。
- 后端全量 Maven 回归共 1543 项，1527 项通过，12 项跳过，4 项失败。原始 HEAD 副本复现了数字员工 ELI5 提示词和 ELK 测试范围两项失败。团队别名恢复测试引用的 `docs/WorkHub/团队重复人员合并/SDD/rollback.sql` 在 HEAD 中缺失。财务规则迁移断言来自工作区原有的未提交测试。
- 用于验证的 JAR 来源于当前工作区，包含工作区现有的财务规则迁移。本次未对真实业务数据库执行迁移。部署前需独立核对该迁移及上述全量回归项。
- 用户确认本轮仅做本机可执行的验证。Windows IDEA、Windows 服务和 Linux 服务账号上的端到端运行、NTFS ACL、真实 GitLab / Codex / ZCode 账号、密码型堡垒机和存量数据库升级留待目标环境验收。

## 2026-11-28 全量回归修复

用户确认排查并修复上一轮的四项失败，以四项定向复测及根工程完整 Maven 回归通过为验收条件。沿用本需求规格。报告、日志与财务迁移按当前契约验证；团队别名恢复材料独立于自动迁移，验证事务约束及前后快照。

| 失败项 | 根因与修复 |
| --- | --- |
| 数字员工 API 报告 | 旧断言要求提示词包含 ELI5。当前报告契约要求简明中文、总体结论先行、事实与证据分栏。测试已从实际发送给网关的提示词核对这三项。 |
| ELK 查询证据 | 请求使用小写环境名时，服务以大写环境编码查询授权范围。修正模拟范围，并验证相邻用例确实触发“无启用服务”和“范围已变化”两个不同的拒绝原因。 |
| 财务规则迁移 | H2 默认将未加引号的标识符折叠为大写，MySQL 的索引元数据字段也不适用于当前 H2。改为核对 H2 唯一约束和组合列顺序，并在插入记录后重复执行建表语句，验证数据保留。 |
| 团队别名恢复 | 文档重组后原回滚文件缺失。依据原合并迁移及快照重建[手动恢复 SQL](../../../../../workhub-bootstrap/src/main/resources/db/recovery/team-developer-aliases/rollback.sql)，配套事务配置随 JAR 独立打包；静态测试通过类路径读取。 |

定向复测 13 项全部通过。使用本机独立 MySQL 8.4 临时实例执行原合并迁移和恢复 SQL，核对 4 张业务表的恢复结果、自动提交拒绝，以及存在后续修改时前序恢复操作也随事务回滚。

根工程执行 `mvn -o clean verify`，所有模块构建成功，退出码 0。Surefire 合计 1543 项，1531 项通过，0 失败，0 错误。12 项按原条件跳过，其中 10 项缺少 ZCode 实机参数，2 项缺少 Axure RP 样本。产物中包含手动恢复 SQL 和事务配置，均位于 `db/recovery/`。

## 2026-11-28 数据库连接按环境配置

按用户反馈调整为 dev/prod 配置。数据库地址、账号和密码可一起写在服务工作目录内的 `config/application-dev.yml` 或 `config/application-prod.yml`。本机 IDEA 的 Active profiles 已设为 dev。连接池与 Flyway 的六项配置已逐项与迁移前比对。

按后续确认，开发库三项连接参数已迁入启动模块资源目录的 `application-dev.yml`，移除了工程根 `config/application-dev.yml`。生产环境继续使用资源目录的 `application-prod.yml`。

## 2026-11-28 Windows 首次连接自动登记堡垒机主机公钥

需求来源为本次聊天。用户在 Windows IDEA 运行后，经堡垒机查询生产库报“数据库查询失败：SSH 连接失败，请核对服务账号的主机指纹、凭据和网络”。定位结果是 `db-prod` 所用 2 号堡垒机（JumpServer `xxt-jumpserver.thchengtay.com:62222`）的主机公钥未登记在运行账号的 known_hosts，JSch 在认证之前拒绝连接。非 Windows 的 OpenSSH 实现使用 `StrictHostKeyChecking=accept-new`，首次连接自动登记，没有这个前置步骤。用户询问能否免去手动登记，并于 2026-11-28 说明应用可能在其他 Windows 机器和 Linux 上运行，确认按本节范围与方案实施。

范围只含 Windows 的 JSch 路径 `JavaSshConnection`（数据库隧道、堡垒机连接测试和服务器诊断共用）；Linux 的 OpenSSH 路径已是 accept-new，不改。JSch 2.28.8 不识别 `StrictHostKeyChecking=accept-new`，实测该值会让主机公钥校验失效且不记录，因此改为包装 `HostKeyRepository`。

| 情形 | 行为 | 本机验证结果 |
| --- | --- | --- |
| 公钥未登记 | 按 `host type base64` 追加到运行账号 `.ssh/known_hosts` 后继续连接，并在应用日志以 WARNING 记录主机与 SHA-256 指纹；文件或 `.ssh` 目录不存在时创建，原内容末尾无换行时另起一行。只追加，不改写已有内容 | 通过。真实堡垒机首次连接写入一行，日志为“首次登记 SSH 主机公钥：[xxt-jumpserver.thchengtay.com]:62222 ssh-rsa SHA256:wz2RrpjX0GoYcGer/BjdZHtzxY8FTnMjC7EfqD+Tays”；文件与目录不存在时创建；末尾无换行（LF、CRLF）时另起一行，原内容不变 |
| 公钥已登记且一致 | 直接继续，不写文件 | 通过。再次连接放行，文件内容与字节数不变 |
| 公钥已登记但变化 | 拒绝连接，文件不变 | 通过。判定为公钥已变化，连接被拒绝，文件不变 |
| 追加失败 | 连接失败并明确报“无法写入 SSH known_hosts”，不静默放行 | 通过。真实的 `connect` 在登录前失败，报“无法写入 SSH known_hosts：<路径>” |

验证于 2026-11-28 在本机 Windows 完成。`AcceptNewHostKeyRepositoryTest` 6 项通过。`workhub-mcp-server` 模块共 98 项，79 项通过，19 项失败或出错；这 19 项全部位于 `OpenSshConnectionTest`、`ServerFilePolicyTest`、`ServerDiagnosticServiceTest`，未改动的 HEAD 在本机同样是 92 项中的同 19 项，原因是依赖 POSIX 权限、符号链接和 Linux 绝对路径，与本次改动无关。改动叠加到 HEAD 干净副本后全工程 `test-compile` 通过。另用编译后的生产类对真实堡垒机验证：首次连接写入并放行、再次连接放行且文件不变、公钥不一致拒绝、本机真实 known_hosts 副本放行且不变、文件不可写时在登录前报错；验证在公钥判定后即中止，未发起登录，也未写入本机真实的 known_hosts。

同日用户已手工把该堡垒机公钥登记到本机 known_hosts，13:00 起应用日志出现“堡垒机共享连接已建立”，`db-prod`、`amp-db-prod` 的只读查询成功，JSch 密码认证在 Windows 上可用得到确认。未执行：全新 Windows 账号上从首次连接到登录成功的完整链路，以及 Windows 服务账号和 Linux 实机验收。改动未提交、未部署。
