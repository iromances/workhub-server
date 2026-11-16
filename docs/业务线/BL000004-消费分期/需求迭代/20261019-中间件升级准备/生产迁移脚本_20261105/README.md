# 消费分期生产迁移脚本（2026-11-05）

本目录承接 [生产不停机方案](/Users/aslight/IDEAWorkspace/workhub-server/docs/业务线/BL000004-消费分期/需求迭代/20261019-中间件升级准备/生产Nacos和XXLJOB不停机迁移方案_20261104.md)。已准备脚本并完成本地模拟，**没有执行生产导入、发布配置、停用或启用任务**。执行需在正式切换窗口由有权限的操作人完成。

## 入口与范围

入口来自用户提供的《消费分期等保生产迁移.md》，没有把其中密码、token 复制到这里。

| 项目 | 本次使用 |
| --- | --- |
| 新 Nacos HTTP 根入口 | `https://amp-nacos.xxingtong.com/nacos` |
| 新 XXL-JOB 根入口 / 应用 admin.addresses | `https://amp-xxl-job.xxingtong.com/xxl-job-admin`，不带 `/jobinfo` |
| Nacos 源 / 目标库 | `amp_nacos` / `amp_nacos_254` |
| XXL-JOB 源 / 目标库 | `amp_xxl_job` / `amp_xxl_job_250` |
| 数据库 | 用户材料指定 `172.17.0.110:3306`；SQL 假设源、目标库在同一实例 |
| 启用范围 | `amp-risk`、`amp-order`、`amp-capital`、`amp-saps`、`amp-payment`、`amp-ops-reporting` |

2026-11-05 通过 WorkHub 受控只读查询确认：旧侧六组共 **82 条，68 ON、14 STOP**；新侧对应 82 条全部 STOP。另有 1 条停止的示例任务，amp-wolease 无任务。此数量用于本次准备，**执行时从老中心重新保存快照，不写死 68 个 ID**。

用户材料没有列出 Nacos 的 DataId / Group。`nacos-scope.example.json` 因而保留空清单，脚本会在登录前拒绝运行。需要执行人填写实际清单，不能填通配符或猜测后缀。旧 XXL-JOB 根入口在保存快照时通过 `--old-admin` 指定。

执行前从应用节点核实新 `admin.addresses` 可达、TLS 可信，且域名实际路由到新库；如果后台域名仅供管理网络访问，应将 `--new-admin` 改为已核实的新中心内网入口。材料中两项 IP:端口相同，不能据此自行推导 Nacos 的 SDK/gRPC 地址；本脚本只处理 XXL-JOB 属性，Nacos 客户端连接仍按部署方案核实。

## 文件和顺序

| 文件 | 作用 / 执行时点 |
| --- | --- |
| `01-nacos-import-prod.sql` | 从桌面既有 SQL 派生的生产库名版本，仅初次导入空的新库 |
| `02-xxljob-import-prod.sql` | 初次导入空的新库，任务全部 STOP、自动注册地址清空 |
| `nacos_xxl_patch.py` | 新 Nacos 已启动并能取到迁入配置后，新业务应用启动前，定向替换两个 XXL-JOB 属性 |
| `xxl_job_cutover.py snapshot` | 最终定义冻结后，**停用老任务之前**保存启停快照 |
| `xxl_job_cutover.py check` | 新应用完整部署、执行器注册完成后做预检 |
| `xxl_job_cutover.py start --apply` | 旧侧触发隔离、在途/重试/子任务/回调收敛后，一次命令批量恢复旧 ON 项 |
| `test_migration.py` | 仅本地模拟，不调用真实服务 |

只使用 Python 3.9+ 标准库。本机以已有 Python 3.9.6 验证；没有安装软件或依赖。生产操作可以从已有 Python、能访问两中心的受控运维机器发起，无需把脚本安装到业务服务器。

## 初始 SQL：已经迁过的库不要重跑

两份 SQL 保留桌面脚本的 INSERT SELECT 列映射，增加目标表/引擎和非空检查，以及出错回滚。Nacos 生产版将桌面 SQL 的 `nacos_config` / `nacos_config_254` 改为已核实的 `amp_nacos` / `amp_nacos_254`。旧文件保留。

仅适用于新中心尚未启动、源配置/任务定义已冻结、备份完成、目标官方表已建好且没有初始化 INSERT 数据的场景。SQL 不创建数据库或业务表，不迁移 Nacos 用户/权限等既有六表之外的对象；鉴权账号/授权需沿用原迁移方案另外核实。XXL-JOB 原用户存储值按既有映射迁入，不创建新默认账号。

用新的数据库会话和支持 `DELIMITER` 的客户端执行完整文件，遇错停止，禁止 `--force`。需要对源表 SELECT、目标表 INSERT 以及目标库 CREATE ROUTINE / EXECUTE / ALTER ROUTINE 权限。失败时事务回滚；辅助存储过程可能留在目标库，先核实结果再清理，不能跳过检查直接重跑局部 INSERT。这是交付 SQL，尚未在 MySQL 实例上演练。

**当前新 XXL-JOB 库已经有迁移数据，不应运行 `02-xxljob-import-prod.sql`。** 新旧定义若后续发生差异，`check` 会阻止启用；应按差异单同步任务定义，不能清空新库再导入。

## Nacos：替换地址和 token

先把示例清单复制为本次实际 `nacos-scope.json`。以下仅展示格式，`实际DataId.yml` / `实际Group` 必须替换成真实标识；不能作为生产清单直接运行。

```json
{
  "namespace": "8941c382-6c67-4de8-a6fe-6ae6b8d9003b",
  "targets": [
    {
      "dataId": "实际DataId.yml",
      "group": "实际Group",
      "format": "yaml",
      "keys": ["xxl.job.admin.addresses", "xxl.job.accessToken"]
    }
  ]
}
```

namespace 示例取既有生产 ID，迁移保留 ID 时适用；执行前仍需核对新中心对应空间。共享配置若只负责一个属性，只填写该属性到 `keys`。若共享配置还被迁移范围外应用引用，须先确认影响范围，不能仅以 DataId 的名字推断使用方。`amp-wolease` 不纳入本次。

在本目录执行预览；按提示输入新 Nacos 账号/密码及用户材料里的新 XXL-JOB token。也可由受控进程注入 `NACOS_USER`、`NACOS_PASSWORD`、`XXL_NEW_ACCESS_TOKEN`，不要把凭据写入命令行参数、Shell 历史或仓库。

```bash
python3 nacos_xxl_patch.py --scope nacos-scope.json
```

核对预览中的标识、键和 `changed` 后，确认新 admin 已使用相同 token，且新业务应用尚未启动，再执行发布：

```bash
python3 nacos_xxl_patch.py --scope nacos-scope.json --apply --new-center-confirmed
```

脚本不查询配置列表，只读取清单中指定的新中心配置，正文仅在内存中处理。只替换 `xxl.job.admin.addresses`、`xxl.job.accessToken`；地址键必须已存在，缺少 token 时允许新增该平铺属性。AppName、执行器 IP/端口、数据源及其他内容保持。目标配置的描述、标签、类型等元数据随发布保留。旧中心和历史内容不做字符串替换。

支持单文档 YAML 的普通嵌套/平铺单行标量，以及普通 properties。重复/等价键、复杂 XXL YAML、合并键、多文档、跨行引号、转义属性键等会中止，不能当作通用 YAML 编辑器。原位置和换行尽量保留；不重序列化整份配置。插件加密配置不按普通文本处理。

先完整预检所有清单项，再逐项通过正式发布接口和 `casMd5` 检查写入，并从客户端 GET 接口回读摘要。操作不是跨配置事务：失败会停止，之前成功项保留；按报告定位，重新预览后用新的 `--report` 文件名重跑，已一致项不再发布。报告只含标识、键、摘要和阶段，不含配置正文/token。发布后仍需从新应用节点验证实际 SDK、gRPC 和 XXL-JOB 注册。

## XXL-JOB：一次命令按旧清单恢复

1. 冻结两侧任务定义编辑，完成最终定义同步。**老任务尚未停用时**保存快照，使用当次切换文件名，禁止覆盖既有快照：

```bash
python3 xxl_job_cutover.py snapshot --old-admin '旧中心根地址' --out xxl-before-stop.json
```

按提示输入老中心管理员账号/密码。脚本完整分页读取六组任务，连读两次检查定义/启停状态一致；快照保留 ID、AppName、原 ON/STOP、子任务 ID、定义 SHA256、旧注册地址和当时下次计划时间。业务执行参数与 GLUE 正文只参与摘要，不写入快照。管理员需要能读取六组执行器和任务。

2. 新中心所有迁入任务保持 STOP，新执行器已注册。预检新旧 ID、定义、原 STOP、子任务范围和注册地址：

```bash
python3 xxl_job_cutover.py check --snapshot xxl-before-stop.json
```

按提示输入新中心管理员账号/密码。检查新注册地址非空且不包含快照中的旧执行器地址；这不代替实际网络、handler 和业务演练。域名别名可能指向同一套机器/库，入口隔离仍需按部署清单核实。

3. 在线流量稳定后，按不停机方案停用旧计划/外部触发，等待预读、执行队列、失败重试、子任务和回调全部收敛，再建立有效的旧侧触发隔离。不能仅凭任务全 STOP 或等待固定秒数判断完成。

4. 到交接点，一次命令恢复：

```bash
python3 xxl_job_cutover.py start --snapshot xxl-before-stop.json --apply --old-center-isolated
```

`--old-center-isolated` 是操作人对上一步完成的明确声明，脚本不能自动证明服务器已隔离。省略 `--apply` 仅预检。新中心地址默认已填用户材料里的入口，必要时通过 `--new-admin` 指定核实过的准确地址。

逐任务调用新中心 `/jobinfo/start`，按子任务优先顺序恢复原 ON 项；原 STOP 继续 STOP，父任务仍可通过既有关系触发 STOP 子任务。没有 `/trigger` 调用，不手工写 `trigger_next_time`，没有“所有任务立即执行一遍”。控制台接口计算将来的计划时间；执行器业务 handler 是否正常还需真实回调和业务结果验收。[2.5.0 启用实现](https://github.com/xuxueli/xxl-job/blob/2.5.0/xxl-job-admin/src/main/java/com/xxl/job/admin/service/impl/XxlJobServiceImpl.java)

5. 保管 `xxl-start-progress.json`。每次启用前落请求意图，成功后回读状态并记录当时观察到的下次计划时间。再次执行同一命令使用同一快照、入口和进度文件，已启用项跳过，不重复计算下次计划。不得并发使用多个不同进度文件对同一批任务发起启动。

单任务失败会停止后续任务，已启用项保持，不会自动恢复旧中心，也不会自动停掉新侧已开始的业务。请求超时但回读已启用时，重跑可确认并跳过；请求结果未知/曾启用、现在却 STOP 时拒绝盲重试。执行人先检查该 ID 的日志与业务结果；确认确实未启用、未执行并排除故障后，备份进度文件，仅清理该 ID 的状态项再重试，其他成功记录保留。不得删除整个进度文件来绕过检查。

## 补跑和回退

启用不补齐停调期间的所有批次。以停旧前快照、切换时间、旧日志及实际业务结果整理漏跑账期/批次，按既有补跑台账执行；支付、清结算等结果不明先核实，不能直接重发。

若需回退，先停新侧计划和外部触发，收敛/隔离新侧，再按原快照恢复旧侧；不能只把域名或 Nacos 地址切回去。本工具不提供自动反向启动旧中心，也不撤销已发生的业务结果。Nacos 配置回退优先回流旧应用；新侧如需恢复参数，通过该 DataId 历史版本定向发布并回读，避免清库或全量覆盖。

## 验证与依据

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest -v test_migration.py
```

本地 24 项模拟检查通过；验证了分页、定义漂移、原 STOP、依赖关系、快照摘要、部分失败、重复执行、响应丢失、精确参数替换、CAS 冲突、缓存延迟和日志脱敏。没有真实 MySQL、XXL-JOB 或 Nacos 的写入演练；上线前应在隔离副本中完成一次全流程演练。

- [XXL-JOB 2.2.0 任务控制器](https://github.com/xuxueli/xxl-job/blob/2.2.0/xxl-job-admin/src/main/java/com/xxl/job/admin/controller/JobInfoController.java)、[2.5.0 任务控制器](https://github.com/xuxueli/xxl-job/blob/2.5.0/xxl-job-admin/src/main/java/com/xxl/job/admin/controller/JobInfoController.java)：读取和启用参数。
- [Nacos 2.5.4 配置控制器](https://github.com/alibaba/nacos/blob/2.5.4/config/src/main/java/com/alibaba/nacos/config/server/controller/ConfigController.java)：指定配置详情、正式发布、CAS 请求头及客户端读取。
- [MySQL SIGNAL](https://dev.mysql.com/doc/refman/8.0/en/signal.html)、[事务语法](https://dev.mysql.com/doc/refman/8.0/en/commit.html)：初始导入失败处理。SQL 原列映射来自桌面已维护脚本，本次未重新验证全部历史数据库列类型。
