# ELK 错误日志监控配置

本文说明如何用本仓库内置的可选 ELK 配置，在 Kibana 中近实时监控 WorkHub 后端错误日志和基础统计。

## 1. 应用日志格式

`workhub-bootstrap/src/main/resources/application.yml` 已配置 Spring Boot ECS 结构化文件日志：

- 日志文件：`logs/workhub-server.log`
- 格式：换行分隔 JSON
- 服务名字段：`service.name`
- 环境字段：`service.environment`
- 日志级别字段：`log.level`
- 异常字段：`error.type`、`error.message`、`error.stack_trace`

默认环境为 `local`。生产环境建议启动时指定：

```bash
WORKHUB_OBSERVABILITY_ENV=prod \
WORKHUB_LOG_FILE=/var/log/workhub-server/workhub-server.log \
mvn -pl workhub-bootstrap spring-boot:run
```

## 2. 本地启动 ELK

先启动后端，让 `logs/workhub-server.log` 文件存在。然后启动 ELK：

```bash
docker compose -f deploy/elk/docker-compose.yml up -d
```

访问 Kibana：

```text
http://127.0.0.1:5601
```

检查 Elasticsearch 是否收到日志：

```bash
curl 'http://127.0.0.1:9200/workhub-logs-*/_search?size=1&pretty'
```

## 3. 创建 Kibana Data View

在 Kibana 中进入：

```text
Stack Management -> Data Views -> Create data view
```

填写：

```text
Name: workhub-logs
Index pattern: workhub-logs-*
Timestamp field: @timestamp
```

## 4. 常用 Discover 查询

生产错误：

```kql
service.name: "workhub-server" and service.environment: "prod" and log.level: "ERROR"
```

本地错误：

```kql
service.name: "workhub-server" and service.environment: "local" and log.level: "ERROR"
```

指定异常类型：

```kql
error.type: "*NullPointerException*"
```

指定日志类：

```kql
log.logger: "cn.aslight.workhub.common.exception.GlobalExceptionHandler"
```

## 5. Dashboard 建议

在 Kibana `Analytics -> Dashboard` 中创建以下 Lens 图表：

- `ERROR 总数`：指标图，过滤 `log.level: "ERROR"`
- `ERROR 趋势`：柱状图，X 轴 `@timestamp`，Y 轴 `count()`
- `异常类型 Top 10`：表格或条形图，Top values `error.type`
- `日志类 Top 10`：表格或条形图，Top values `log.logger`
- `服务环境分布`：表格，Top values `service.environment`

Dashboard 右上角建议设置：

```text
Time range: Last 15 minutes
Refresh every: 10 seconds
```

## 6. 告警规则

进入：

```text
Stack Management -> Rules -> Create rule -> Index threshold
```

规则一：5 分钟内生产错误超过 10 次。

```text
Index: workhub-logs-*
Time field: @timestamp
WHEN count() IS ABOVE 10
FOR THE LAST 5 minutes
KQL filter: service.name: "workhub-server" and service.environment: "prod" and log.level: "ERROR"
Check every: 1 minute
```

规则二：严重 JVM 错误立即通知。

```kql
service.environment: "prod" and log.level: "ERROR" and (
  error.type: "*OutOfMemoryError*" or
  error.type: "*StackOverflowError*"
)
```

通知渠道在 `Stack Management -> Connectors` 配置。企业微信机器人可使用 Webhook Connector。

## 7. 生产部署注意事项

- Filebeat 建议直接部署在应用服务器上，读取 `/var/log/workhub-server/workhub-server.log`。
- 生产 Elasticsearch/Kibana 不建议关闭安全认证；本仓库的 `deploy/elk/docker-compose.yml` 只用于本地开发联调。
- 建议为 `workhub-logs-*` 配置 ILM，按磁盘容量保留 30 到 90 天。
- 告警阈值先从宽配置，观察一周后再收紧，避免告警噪音。

## 8. WorkHub 自动感知

WorkHub 可按“系统预警”中启用的业务线关注范围，从 Elasticsearch 自动同步 `ERROR` 日志到
`ops_system_alert_event`，并向业务线成员创建去重后的站内通知。该能力默认关闭，采集失败属于旁路故障，
不会影响需求、支付、MCP 等核心接口。

采集事件在入库前由“系统预警 -> 过滤规则”分类。规则动作支持 `IGNORE`、`SLOW_SQL` 和
`SYSTEM_ERROR`；匹配范围支持消息正文或标题、异常类型、消息与堆栈组成的全部文本；匹配方式支持
任一关键词或全部关键词。优先级数值越小越先执行，命中第一条规则后停止；未命中时默认归入
`SYSTEM_ERROR`。`IGNORE` 不入库、不通知，`SLOW_SQL` 只在慢 SQL 页签入库展示且不创建站内通知，
`SYSTEM_ERROR` 沿用批量站内通知逻辑。

规则新增、编辑、启停或删除后，从下一轮日志采集开始生效，不自动重分历史事件。采集器每轮只加载一次
启用规则和关键词，避免逐事件查询数据库。数据库迁移会把原有噪音过滤项和 Druid 长时间未收包慢 SQL
识别项初始化为可维护规则。

系统异常和慢 SQL 明细支持按错误消息模糊查询，并可由具有删除权限的用户选择当前页事件进行批量物理删除；
每页可选择 20、50、100、500 或 1000 条。删除先通过事件 `source_event_id`、业务线和环境精确定位关联的
`ELK_ERROR` 站内通知主键，再分别按主键批量删除 `sys_notification` 和 `ops_system_alert_event`，并记录系统操作审计。单批写操作最多 500 个主键，全部批次、两张表和审计仍位于同一事务；不再使用去重键后缀函数联表删除。没有源事件 ID
的事件不按消息文案宽泛删除通知。该操作不删除 Elasticsearch 原始日志，也不修改采集游标；后续重置游标并
重新回看日志时，同一原始事件仍可能再次入库并生成通知。

页面还提供“删除&过滤”：以必填消息关键词和当前完整查询条件创建独立异步任务，同时新增或复用全局
`IGNORE/MESSAGE/ANY` 规则。规则对所有业务线和环境生效；历史事件删除仍受提交时的业务线、环境、服务、
级别、事件分类和时间条件约束。任务提交后按钮保持可用，可继续提交其他任务；后台固定事件 ID 上界并按
最多 500 条分批删除、提交和记录进度，避免依赖单个长 HTTP 请求或超长数据库事务。任务范围重叠时按实际
受影响行数累计，已经被其他任务删除的主键按幂等成功处理。任务失败会保留已完成数量和失败原因，已提交的
物理删除不会回滚；过滤规则仍保持启用，重新提交可继续清理剩余数据。该操作同样不删除 Elasticsearch 原始日志。

### 8.1 系统配置模式

生产环境优先在“系统管理 -> 系统配置”维护 ELK 参数。统一使用配置分组：

```text
elk.alert
```

连接和认证配置：

| configKey | valueType | 必填条件 | 说明 |
|---|---|---|---|
| `enabled` | TEXT | 必填 | `true` 开启、`false` 关闭；建议最后开启 |
| `baseUrl` | TEXT | 开启时必填 | Elasticsearch API 地址，例如 `https://es.example.com:9200`，不是 Kibana `/app/discover` 地址 |
| `authType` | TEXT | 开启时必填 | `BASIC`、`API_KEY` 或 `NONE` |
| `username` | TEXT | BASIC | 只保存用户名 |
| `password` | SECRET | BASIC | 加密保存，接口和审计不返回明文 |
| `apiKey` | SECRET | API_KEY | 加密保存，接口和审计不返回明文 |

实际 `amp-saps-*` 日志的字段配置如下。未维护的非必填项使用表中默认值：

| configKey | 默认值 | 用途 |
|---|---|---|
| `timestampField` | `@timestamp` | 查询、排序和事件时间 |
| `serviceQueryField` | `appName.keyword` | 精确过滤服务名 |
| `serviceSourceField` | `appName` | 从 `_source` 读取服务名 |
| `environmentQueryField` | 空 | 为空时不生成环境过滤条件 |
| `levelQueryField` | `level.keyword` | 精确过滤错误级别 |
| `levelSourceField` | `level` | 从 `_source` 读取级别 |
| `errorLevel` | `ERROR` | 需要采集的级别值 |
| `messageSourceField` | `message` | 错误消息 |
| `loggerSourceField` | `logger_name` | Logger 名称 |
| `errorTypeSourceField` | 空 | 异常类型；当前日志未发现该字段 |
| `stackTraceSourceField` | `stack_trace` | 异常堆栈 |
| `traceIdSourceField` | `TID` | 链路编号 |
| `requestIdSourceField` | 空 | 请求编号；当前日志未发现该字段 |

性能配置及默认值：

```text
connectTimeoutSeconds=5
requestTimeoutSeconds=30
pageSize=200
maxEventsPerRun=1000
initialLookbackMinutes=5
pitKeepAlive=1m
```

系统配置存在 `elk.alert.enabled` 时，它是权威开关；每轮采集都会重新读取系统配置，修改后最迟在下一轮生效。
`environmentQueryField` 为空时，应保证各业务线关注范围的索引模式按环境隔离，防止不同环境日志混采。
`maxEventsPerRun` 限制单个业务线范围每轮最多保留的最近错误数；高频日志环境应按 WorkHub 数据库承载能力进一步调低。

### 8.2 业务线关注范围与索引模式

日志索引不属于全局 ELK 连接配置。在“系统预警 -> 关注业务线”的新增或编辑抽屉中，为每个
“业务线 + 环境”维护关注模式和一个或多个索引模式，例如：

```text
业务线：保费分期
环境：生产
关注范围：整条业务线
索引模式：
  - amp-*
```

索引模式保存在 `ops_system_alert_scope_index`，按配置顺序组合为 Elasticsearch 支持的逗号分隔
索引表达式，并在该索引集合上创建 PIT。单个范围最多配置 20 个索引模式，重复值自动去重。

`ALL` 模式不添加服务过滤，索引必须天然只包含目标业务线；共享索引在没有可靠业务线字段过滤时不得使用
`ALL`。`SELECTED` 模式从 `ops_system_alert_scope_service` 读取启用服务名，通过 Elasticsearch `terms`
一次过滤多个服务。修改模式、服务或索引后清除范围游标，下一轮从初始回看窗口重新读取。

### 8.3 环境变量兼容模式

没有维护 `elk.alert.enabled` 时，继续兼容原环境变量及 ECS 字段：

```text
WORKHUB_ELK_ALERT_ENABLED=true
WORKHUB_ELK_BASE_URL=https://elasticsearch.example.com
WORKHUB_ELK_INDEX_PATTERN=workhub-logs-*
WORKHUB_ELK_API_KEY=通过部署系统注入，不写入仓库
```

也可以使用 `WORKHUB_ELK_USERNAME` 和 `WORKHUB_ELK_PASSWORD` 配置 Basic Auth。API Key 优先，所有凭据只在
服务端内存中用于请求头，不进入事件表、站内通知或应用日志。

定时表达式仍通过 `WORKHUB_ELK_ALERT_CRON` 管理，修改后需要重启应用。系统配置模式只负责连接、认证、索引、
字段和查询性能参数。

### 8.4 分页、幂等与失败隔离

采集器每轮创建短期 PIT，按 `@timestamp DESC + _shard_doc` 使用 `search_after` 读取最近错误，达到
`maxEventsPerRun` 或读完结果后关闭 PIT；
跨轮次通过 Elasticsearch `_index + _id` 的 SHA-256 指纹唯一索引防止重复落库。每个业务线关注范围独立保存
最后成功时间和采集状态，单个范围失败不会阻塞其他范围。只有首次成功落库的事件才计入通知，
同一范围同一采集轮次按实际服务分别合并通知，避免高频错误形成通知洪峰。

### 8.5 MCP 实时日志查询

MCP `search_business_logs` 复用系统配置中的 ELK 连接和字段映射，并复用“业务线 + 环境”的启用关注范围，直接查询 Elasticsearch 原始日志，不依赖 `ops_system_alert_event`。因此它可以查询 `INFO/WARN/ERROR/DEBUG`，用于补足系统告警定时采集只保留 `ERROR` 的边界。

生产问题排查默认优先使用该工具；服务器 SSH 日志仅在 ES 未采集、需要原始历史文件或进程状态时补充。调用方只能提供业务线、环境、服务、级别、时间、固定消息短语、Trace ID、请求 ID 和条数，不能指定索引、字段或 Query DSL。包含 `INFO/DEBUG` 的查询最长 24 小时，仅 `WARN/ERROR` 最长 7 天，单次最多 100 条；结果在 MCP 出口统一截断、脱敏和审计。
