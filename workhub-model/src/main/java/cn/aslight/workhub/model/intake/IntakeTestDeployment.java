package cn.aslight.workhub.model.intake;

import java.util.List;
import java.util.Map;

/** 测试发布记录；研发分支始终保留，合测分支随同一批次重复使用。 */
public final class IntakeTestDeployment {
    private IntakeTestDeployment() { }
    public record Request(String bindingId, List<Long> intakeIds, String previousId) { }
    public record Binding(String id, String businessLineCode, String systemName, String projectPath,
                          String instanceId, String baseUrl, String jobName, String environmentCode,
                          String deployTarget, String serverAddress, String publicBranch, boolean enabled) { }
    public record Source(Long intakeId, String name, String branch, String sha) { }
    public record Plan(Binding binding, List<Source> sources, String branch, String baseSha,
                       String previousId, String expectedSha) { }
    public record Row(String id, Long intakeId, String activeKey, String planJson, String status,
                      Long queueId, Long buildNumber, String buildUrl, String actualSha, String message,
                      String requestedBy, java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt) { }
    public record View(String id, Long intakeId, Plan plan, String status, Long buildNumber,
                       String buildUrl, String actualSha, String message, String requestedBy,
                       java.time.LocalDateTime createdAt) { }
    public static Map<String, String> parameters(Plan plan) {
        return Map.of("deploy_test_env", plan.binding().deployTarget(), "project_branch", plan.branch(),
                "thctay_public_branch", plan.binding().publicBranch());
    }
}
