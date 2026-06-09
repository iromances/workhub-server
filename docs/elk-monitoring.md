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
