# Design：参考 amp-saps 调整 amp-order

需求编码：REQ-428dadce-1c72-4d7d-9eb7-d4233554e6c7  
需求名称：参考 amp-saps 调整 amp-order  
业务线：BL000004／消费分期；GitLab 组：asset-plateform  
唯一需求目录：wiki/公司项目/研发需求/业务线/BL000004-消费分期/20261019-中间件升级准备  
涉及仓库：amp-saps（只读参考）、amp-order（实施）  
规格版本：v1.0（2026-10-23，用户已确认；本地实现及离线验证完成）  
当前研发分支：`feature/nacos-xxljob-upgrade_20260123`（2026-10-23 按用户要求完成本地及远程分支更名）


对应 [Proposal v1.0](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirements/业务线/BL000004-消费分期/20261019-中间件升级准备/proposal.md>)，任务见 [Tasks v1.0](</Users/aslight/IDEAWorkspace/workhub-server/docs/requirements/业务线/BL000004-消费分期/20261019-中间件升级准备/tasks.md>)。

## 现状证据

- 2026-10-23 本地只读核对，业务线映射来自 WorkHub list_mcp_targets。
- 参考仓库：/Users/aslight/IDEAWorkspace/asset-plateform/amp-saps，feature/nacos-alibaba-upgrade，4ca0cd7b1db72503ccc8b4d163c63e139ca0a181。
- 目标仓库：/Users/aslight/IDEAWorkspace/asset-plateform/amp-order，master，f696b160af8081f9a2090aca35ced64c2d5005af。
- 两仓库 test namespace 相同，server-addr 不同；amp-order 没有 username/password，config 自有 group 须保留。
- 本地已有 Maven、Java、nacos-client 2.5.4 和 SnakeYAML 缓存。Java 8 离线编译及依赖解析均成功，详见 Tasks。

## 修改设计

### 根 pom.xml

在现有 dependencies 中增加直接依赖 com.alibaba.nacos:nacos-client:2.5.4，保持已有父工程、模块和其余依赖。采用与参考仓库相同的放置方式，不调整共享 thctay-dependencies。

### bootstrap.yml

在原文件上精确修改，避免整体复制 amp-saps 配置。将默认 active 改为 test，移除完整 dev-amp 文档段。只修改 test 文档中 config/discovery 的 server-addr 并各新增 username/password；四项鉴权取值及两个地址均从 amp-saps 对应节点读取，不人工猜测。保留 amp-order 的其他配置和 dev/pre/prod 文档原文。

## 契约与数据

不修改 REST/Feign 契约、业务数据或数据库结构。Nacos 配置加载与注册使用新的 test 端点，应用名仍为 amp-order。客户端失败行为沿用原框架，不引入重试或兜底代码。

## 验证与边界

1. 使用标准库解析 pom.xml，核对直接依赖唯一性和版本。
2. 使用已缓存 SnakeYAML 解析全部配置文档；按结构比较修改前后，断言预期变更及其余节点保留，凭据比较仅输出布尔结果。
3. git diff --check；离线 Maven dependency:tree 与 compile，优先 Java 8（若本地已安装），不得下载依赖。
4. 不启动 Spring 应用，不连接 Nacos、数据库、Redis、RabbitMQ 或远程服务；运行时配置加载、注册及 gRPC 连通性尚不在本次本地验证覆盖内。

## 交付与回滚

改动保留于新分支工作树，不自动提交、推送或部署。回滚本次两处文件差异即可恢复本地基线；若后续发布，客户端版本与 Nacos 地址应配套回退。验证后记录实际证据，并更新业务线迭代日志；现有系统设计若未涉及 Nacos 版本且无已部署现状变化，说明无需修改的原因。

## 确认记录

用户于 2026-10-23 确认 Proposal 所列完整范围，包括默认 test、删除 dev-amp，以及本轮 test 配置以最新 amp-saps 鉴权配置为准。
