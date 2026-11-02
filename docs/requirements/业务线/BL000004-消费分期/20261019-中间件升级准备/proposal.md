# Proposal：参考 amp-saps 调整 amp-order

需求编码：REQ-428dadce-1c72-4d7d-9eb7-d4233554e6c7  
需求名称：参考 amp-saps 调整 amp-order  
业务线：BL000004／消费分期；GitLab 组：asset-plateform  
唯一需求目录：wiki/公司项目/研发需求/业务线/BL000004-消费分期/20261019-中间件升级准备  
涉及仓库：amp-saps（只读参考）、amp-order（实施）  
规格版本：v1.0（2026-10-23，用户已确认；本地实现及离线验证完成）  
当前研发分支：`feature/nacos-xxljob-upgrade_20260123`（2026-10-23 按用户要求完成本地及远程分支更名）


## 背景与范围

用户要求参考 IDEA 中的 amp-saps 调整 amp-order。IDEA 当前打开 amp-saps 的 feature/nacos-alibaba-upgrade 分支，最新提交 4ca0cd7b1db72503ccc8b4d163c63e139ca0a181，标题为“nacos升级”，只修改根 pom.xml 和启动模块 bootstrap.yml。本轮拟按此提交同步 Nacos 客户端及测试环境配置；用户于 2026-10-23 确认本轮即上述 Nacos 升级范围。

amp-order 当前 master，提交 f696b160af8081f9a2090aca35ced64c2d5005af，工作树干净。已从此基线新建 codex/amp-order-nacos-upgrade 分支，在本地开发；配置目标为 test，不进行远程联调或部署。

## 本期交付

1. amp-order 根 pom.xml 的 dependencies 添加 com.alibaba.nacos:nacos-client:2.5.4，与 amp-saps 一致。
2. amp-order-start/src/main/resources/bootstrap.yml 默认 profile 从 dev 改为 test，删除 dev-amp 段（amp-order 无 sit 段）。
3. test 的 config/discovery 地址和 username/password 逐项取自 amp-saps test 的对应值。凭据仅在本地配置文件中处理，不复制到规格、日志或聊天。
4. 保留 amp-order 的服务名称、端口、namespace、group、上传限制、日志及其余 profile 内容。

不涉及业务接口、数据库、生产配置或框架 BOM 变更。不安装软件或依赖；验证使用已安装工具及本地缓存。

## 验收标准

| 编号 | 条件 | 核对方式 |
| --- | --- | --- |
| AC-01 | Nacos 客户端直接依赖为 2.5.4，父依赖与 BOM 不变 | XML 解析与离线依赖树 |
| AC-02 | 默认 test、无 dev-amp，test 地址和鉴权项匹配参考提交 | YAML 解析和逐项断言（只输出匹配结果） |
| AC-03 | amp-order 服务身份、其余环境及其他配置保留 | YAML 结构对比和 Git diff |
| AC-04 | 静态检查通过并完成可用离线编译；缓存不足须如实记录 | git diff --check、离线 Maven |

## 统一确认结果

用户于 2026-10-23 回复“确认，按这个 Nacos 升级范围执行”，本规格已获确认。旧《消费分期Nacos升级迁移方案_20261022.md》写明“不新增账号密码”；本轮按已确认范围，以用户指定的 amp-saps 最新代码作为 test 配置依据。此差异只针对本轮测试配置，不改变原生产迁移口径。
