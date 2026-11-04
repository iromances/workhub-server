package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentReleaseTargetsTest {
    private final AgentScope aTest = new AgentScope("A", "TEST"), aProd = new AgentScope("A", "PROD"),
            bTest = new AgentScope("B", "TEST"), bProd = new AgentScope("B", "PROD");

    @Test void accessToProductionDoesNotAddProductionCompletion() {
        var task = task(List.of(aTest));
        assertEquals(Set.of(aTest), AgentReleaseTargets.scopes(task, "TEST_DEPLOY"));
        assertTrue(AgentReleaseTargets.scopes(task, "PROD_DEPLOY").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> AgentReleaseTargets.requireAllowed(task, binding("server", aProd)));
        assertThrows(IllegalArgumentException.class, () -> AgentReleaseTargets.requireAllowed(task, binding("other", bTest)));
    }
    @Test void productionIncludesOnlySameBusinessLineTestPrerequisite() {
        var task = task(List.of(aProd));
        assertEquals(Set.of(aTest), AgentReleaseTargets.scopes(task, "TEST_DEPLOY"));
        assertEquals(Set.of(aProd), AgentReleaseTargets.scopes(task, "PROD_DEPLOY"));
        assertDoesNotThrow(() -> AgentReleaseTargets.requireAllowed(task, binding("server", aTest)));
        assertThrows(IllegalArgumentException.class, () -> AgentReleaseTargets.requireAllowed(task, binding("other", bTest)));
    }
    @Test void ordinaryTasksAndLegacyMissingTargetsCannotRelease() {
        for (String kind : List.of("REPORT", "CHANGE_SET", "TEST_REPORT", "DEPLOYMENT", "VERIFICATION")) {
            var task = new AgentTaskSpecification("任务", "", "验收", List.of(kind), List.of(aTest, aProd), List.of());
            assertThrows(IllegalArgumentException.class, () -> AgentReleaseTargets.requireAllowed(task, binding("server", aTest)));
        }
    }
    @Test void mixedBusinessLineTargetsRequireAllStageRepositoriesWithoutExpandingProduction() {
        var task = task(List.of(aTest, bProd)); var manifest = manifest(task, List.of());
        assertEquals(Set.of("a-server", "a-web", "b-server"), AgentReleaseTargets.repositories(task, manifest, "TEST_DEPLOY"));
        assertEquals(Set.of("b-server"), AgentReleaseTargets.repositories(task, manifest, "PROD_DEPLOY"));
        assertDoesNotThrow(() -> AgentReleaseTargets.requireCoverage(task, manifest, "TEST_DEPLOY", List.of(step("a-server", aTest), step("a-web", aTest), step("b-server", bTest))));
        assertDoesNotThrow(() -> AgentReleaseTargets.requireCoverage(task, manifest, "PROD_DEPLOY", List.of(step("b-server", bProd))));
        assertThrows(IllegalArgumentException.class, () -> AgentReleaseTargets.requireCoverage(task, manifest, "PROD_DEPLOY", List.of(step("a-server", aProd), step("b-server", bProd))));
    }
    @Test void sameStageCannotDropBusinessLineOrAnotherRepository() {
        var task = task(List.of(aTest, bProd)); var manifest = manifest(task, List.of());
        assertThrows(IllegalArgumentException.class, () -> AgentReleaseTargets.requireCoverage(task, manifest, "TEST_DEPLOY", List.of(step("a-server", aTest), step("a-web", aTest))));
        assertThrows(IllegalArgumentException.class, () -> AgentReleaseTargets.requireCoverage(task, manifest, "TEST_DEPLOY", List.of(step("a-server", aTest), step("b-server", bTest))));
        assertThrows(IllegalArgumentException.class, () -> AgentReleaseTargets.requireCoverage(task, manifest, "PROD_DEPLOY", List.of(step("b-server", aProd))));
    }
    @Test void graphOrdersScopedProductionSubsetAndKeepsDependenciesForTestGroup() {
        var task = task(List.of(aTest, bProd)); var manifest = manifest(task, List.of(new AgentChangePlan.Dependency("a-web", "a-server")));
        var change = new AgentChangeView("change", "run", "task", 1, "h", null, true, true, List.of(), manifest);
        var test = AgentReleasePlanGraph.order(change, List.of(step("a-web", aTest), step("b-server", bTest), step("a-server", aTest)), List.of(), AgentReleaseTargets.repositories(task, manifest, "TEST_DEPLOY"));
        assertEquals(List.of("a-server"), test.stream().filter(value -> "a-web".equals(value.bindingId())).findFirst().orElseThrow().dependsOn());
        var prod = AgentReleasePlanGraph.order(change, List.of(step("b-server", bProd)), List.of(), AgentReleaseTargets.repositories(task, manifest, "PROD_DEPLOY"));
        assertEquals(1, prod.size()); assertEquals("b-server", prod.getFirst().bindingId());
    }
    @Test void dependencyIntoBusinessLineWithoutProductionTargetMustNotBeSilentlyDropped() {
        var task = task(List.of(aTest, bProd)); var manifest = manifest(task, List.of(new AgentChangePlan.Dependency("b-server", "a-server")));
        assertDoesNotThrow(() -> AgentReleaseTargets.repositories(task, manifest, "TEST_DEPLOY"));
        assertThrows(IllegalArgumentException.class, () -> AgentReleaseTargets.repositories(task, manifest, "PROD_DEPLOY"));
    }
    @Test void targetWithNoCandidateAndStageEnvironmentMismatchFailClosed() {
        var task = task(List.of(new AgentScope("C", "TEST"))); var manifest = manifest(task, List.of());
        assertThrows(IllegalArgumentException.class, () -> AgentReleaseTargets.repositories(task, manifest, "TEST_DEPLOY"));
        var original = binding("a-server", aProd);
        var mismatched = new AgentJenkinsPolicy.JobBinding(original.id(), original.name(), original.instanceId(), aTest, original.systemName(), original.stage(), original.jobSegments(),
                original.commitParameters(), original.correlationParameter(), original.manifestParameter(), original.environmentParameter(), original.fixedParameters(), original.receiptPath(), true);
        assertThrows(IllegalArgumentException.class, () -> AgentReleaseTargets.requireAllowed(task(List.of(aTest, aProd)), mismatched));
    }
    private AgentTaskSpecification task(List<AgentScope> targets) {
        return new AgentTaskSpecification("分环境发布", "", "目标全部完成", List.of("DEPLOYMENT", "VERIFICATION"), List.of(aTest, aProd, bTest, bProd, new AgentScope("C", "TEST")), List.of(), targets);
    }
    private AgentChangeManifest manifest(AgentTaskSpecification task, List<AgentChangePlan.Dependency> dependencies) {
        return new AgentChangeManifest(1, "run", "task", task, new AgentChangePlan("修复", "影响", "验收", "回退", dependencies), null,
                List.of(repository("a-server", aTest), repository("a-web", aTest), repository("b-server", bTest)), List.of("a-server", "a-web", "b-server"));
    }
    private AgentRepositoryChange repository(String id, AgentScope scope) {
        return new AgentRepositoryChange(id, id, scope, "ws-"+id, 1, "p".repeat(64), "development", "a".repeat(40), "candidate", "b".repeat(40), List.of("src/Main.java"), "d".repeat(64), "diff", false);
    }
    private AgentJenkinsPolicy.JobBinding binding(String repository, AgentScope scope) {
        return new AgentJenkinsPolicy.JobBinding(repository, repository, "jenkins", scope, repository, "TEST".equals(scope.environmentCode())?"TEST_DEPLOY":"PROD_DEPLOY", List.of(repository),
                Map.of(repository, "COMMIT"), "REQUEST", "MANIFEST", "ENV", Map.of(), "receipt.json", true);
    }
    private AgentReleasePlanSnapshot.Step step(String repository, AgentScope scope) {
        var instance = new AgentJenkinsPolicy.Instance("jenkins", "Jenkins", "https://jenkins.invalid/", "user", "credentials", "token", true);
        return new AgentReleasePlanSnapshot.Step("request-"+repository, "approval-"+repository, new AgentJenkinsPolicy.BindingSnapshot(instance, binding(repository, scope), "hash"), Map.of(), List.of());
    }
}
