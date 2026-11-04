package cn.aslight.workhub.service.agent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** 真子进程测试桩：仅访问父测试创建的 loopback MCP，不连接任何 AI。 */
public final class FakeAgentCliProcess {
    public static void main(String[] args) throws Exception {
        String prompt = new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (!prompt.contains("完整任务") && !prompt.contains("完整")) throw new IllegalStateException("缺少完整任务");
        if ("hang".equals(args[2])) { Thread.sleep(300_000); return; }
        if ("expect-new-input".equals(args[2]) && !prompt.contains("新的巡检要求")) throw new IllegalStateException("重新规划没有纳入人工补充");
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create(args[0])).timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + System.getenv("WORKHUB_AGENT_RUN_TOKEN"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"monitor_read\",\"arguments\":{}}}")).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || !response.body().contains("NEVER_RUN")) throw new IllegalStateException("MCP 工具调用未成功");
            System.out.println("{\"type\":\"item.completed\",\"item\":{\"type\":\"agent_message\",\"text\":\"已查询监测，正在整理结果\"}}");
            Files.writeString(Path.of(args[1]), "{\"type\":\"RESULT\",\"summary\":\"原生 CLI 已根据实际 MCP 回执完成巡检报告\"}");
        }
    }
}
