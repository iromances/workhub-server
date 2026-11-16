# 提测确认自动创建禅道版本（build）与测试单

## Context

用户在确认提测时收到报错：「禅道项目（ID 892）下没有名为「feature/repurchase_rule_202611030001」的版本，请先在禅道按研发分支创建版本后重新提测」。当前 `ZentaoClient.createTestTask` 只会**查找**禅道版本（版本名=研发分支名），查不到即失败，需要人工先去禅道建版本。目标：确认提测时若版本不存在，自动调用禅道接口创建版本，再创建测试单，一次提测成功，无需人工预建。

本次为既有需求 **REQ-a71851db-1d1c-4f20-b99e-330950af6ad9**（需求任务评估SDD化与AI开发提测流程升级）的后续变更，沿用其规格目录。

## 禅道接口契约（已从禅道开源仓库 `api/v1/entries/builds.php` 源码核实）

- `POST {baseUrl}/api.php/v1/projects/{projectId}/builds`，Token 头
- 必填字段：`execution`（执行ID）、`product`（产品ID）、`name`（版本名=分支名）、`builder`（禅道账号）、`date`（yyyy-MM-dd）；可选 `desc` 等
- 成功：HTTP 201 + 版本对象（含 `id`）；失败：400/404 + message
- 与现有 `findBuildByBranch` 查询的 GET 同路径，客户端已在该禅道（开源版 21.7.8）真实验证过

## 改动内容

### 1. `workhub-service/src/main/java/cn/aslight/workhub/service/intake/ZentaoClient.java`

- `findBuildByBranch`（:114）：匹配不到时返回 `null` 而非抛错（方法注释同步改）。
- 新增私有方法 `createBuild(baseUrl, token, mapping, branchName)`：
  - POST `/api.php/v1/projects/{projectId}/builds`，payload：`execution=mapping.executionId()`、`product=mapping.productId()`、`name=branchName`、`builder=配置账号 zentao.base.account`（保证是禅道合法用户，避免研发负责人非禅道用户导致 400）、`date=LocalDate.now()`、`desc="WorkHub 提测确认自动创建"`；
  - 复用现有 `postJson`/401 重换 Token 重试/`readJsonLong` 解析 id 的模式；非 2xx 或无 id 时抛 `IllegalStateException("禅道自动创建版本（{branch}）失败…")`，沿用失败即整体失败语义（提测停留开发中）。
- `createTestTask`（:54）：`findBuildByBranch` 返回 null 时先 `createBuild` 再建测试单；更新类与方法 Javadoc（链路描述加入“版本不存在时自动创建”）。
- 已知小概率竞态：并发提测同名分支可能重复建版本，禅道侧无唯一约束，接受（记录于设计文档）。

### 2. 测试 `workhub-service/src/test/java/cn/aslight/workhub/service/intake/IntakeSddFlowTest.java`

沿用仓库既有 `com.sun.net.httpserver` 桩模式，新增用例：
- 版本列表不含分支 → 依次调用 builds POST（断言请求体含 execution/product/name/builder/date）与 testtasks POST，返回测试单回执；
- 版本列表已含分支 → 不再发 builds POST（计数断言）；
- builds POST 返回 400 → 抛出明确“禅道自动创建版本…失败”异常。
- 既有 `zentaoClient_shouldFailFastWhenNotConfigured` 等配置快速失败用例不受影响。

### 3. 文档（SDD 后续变更）

- `docs/业务线/BL000012-工作台/需求迭代/REQ-a71851db-.../design.md`：追加“后续变更：提测自动创建禅道版本”小节（动机、接口契约、决策：builder 用配置账号、失败回滚语义、竞态说明）。
- 同目录 `tasks.md`：记录本次任务、验证证据与确认记录（确认人=用户，日期=实施日）。
- `docs/业务线/BL000012-工作台/迭代日志.md`：按既有行格式追加一行。
- `docs/业务线/BL000012-工作台/现状.md`：更新提测流程描述中“禅道测试单始终创建”处，补版本自动创建行为。

## 不做的事

- 不加“自动建版本”开关（用户已明确要自动创建；旧报错文案随之移除）。
- 不改 `IntakeSubmitTestReviewService`（编排不变，自动创建封装在 ZentaoClient 内）。
- 不动数据库/前端。

## 验证

1. `mvn -pl workhub-service -am test -Dtest=IntakeSddFlowTest`（新旧用例全过）+ `mvn -pl workhub-service -am compile`。
2. 真实禅道（10.10.116.28，开源版 21.7.8）联调：部署后对需求 202611030001 分支重新确认提测，验证禅道自动出现同名版本+测试单；此步需用户部署后执行，文档中如实标注“真实链路待部署验证”。