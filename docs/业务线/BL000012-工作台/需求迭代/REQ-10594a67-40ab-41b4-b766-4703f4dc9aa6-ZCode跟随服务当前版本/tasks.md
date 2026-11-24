# Tasks：ZCode跟随服务当前版本

需求编码：REQ-10594a67-40ab-41b4-b766-4703f4dc9aa6

需求名称：ZCode跟随服务当前版本

唯一目录：docs/业务线/BL000012-工作台/需求迭代/REQ-10594a67-40ab-41b4-b766-4703f4dc9aa6-ZCode跟随服务当前版本

所属业务线：BL000012-工作台；涉及仓库：workhub-server；分支：codex/development-20261102；目标：本地代码及已有 ZCode 0.18.0 验证。

## 范围来源
用户 2026-11-22 指示“直接用服务的最新版本”，承接上一轮取消版本硬限制的讨论。本轮 v1 限于执行上述指示；无软件安装、配置修改或业务重跑。实施人：Codex；需求确认人：用户（会话指示）。

## 任务与验收
- T-01：核对配置命令和现有版本，完成：当前 0.18.0 提供 app-server。
- T-02：移除版本硬限制并补充失败场景测试，完成（AC-01/02）。
- T-03：定向单测及本机 0.18.0 协议验证，完成（AC-03）。
- T-04：文档同步与部署状态说明，完成。

## 实际验证
2026-11-22 验证完成：46 项测试通过，0 失败、0 错误、0 跳过。包括预检/隔离单测 13 项、会话协议单测 27 项，以及已安装 ZCode 0.18.0 本机协议集成测试 6 项（含原生 Agent 文件与 Shell 工具、MCP 调用、结果读取、格式校正、超时清理）。报告见 [验证目录](验证/)。

命令：`mvn -o -pl workhub-service -am test -Dtest=ZCodeCliClientTest,ZCodeTaskConversationTest,ZCodeCliLocalProtocolTest -Dsurefire.failIfNoSpecifiedTests=false -Dworkhub.test.zcode.command=/Applications/ZCode.app/Contents/Resources/glm/zcode.cjs -Dworkhub.test.zcode.node=/opt/homebrew/opt/node@22/bin/node`。

首次沙箱验证因回环端口绑定被拒失败；经执行审批后主机离线重跑通过。未安装依赖，未调用真实模型或业务工具。`git diff --check` 通过；工作区其他修改保持原样。

部署状态：源码及本地编译测试完成，未打包部署、重启运行服务或重跑业务巡检。服务重载新代码后生效。0.18.0 为服务当前已安装版本，未核实互联网发行最新版，也未改成自动更新机制。
