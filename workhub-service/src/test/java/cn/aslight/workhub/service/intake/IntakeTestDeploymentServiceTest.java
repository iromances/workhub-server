package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.*;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.intake.*;
import cn.aslight.workhub.model.intake.IntakeTestDeployment.*;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.service.agent.*;
import cn.aslight.workhub.service.system.SysConfigService;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 真实发布入口的环境边界、参数、分支复用与一次提交语义。 */
class IntakeTestDeploymentServiceTest {
    final ObjectMapper json = new ObjectMapper();
    final IntakeTestDeploymentMapper mapper = mock(IntakeTestDeploymentMapper.class);
    final IntakeMapper intakes = mock(IntakeMapper.class);
    final DevelopmentAnalysisMapper analyses = mock(DevelopmentAnalysisMapper.class);
    final BusinessLineMapper lines = mock(BusinessLineMapper.class);
    final SysConfigService configs = mock(SysConfigService.class);
    final AgentJenkinsPolicy policies = mock(AgentJenkinsPolicy.class);
    final AgentJenkinsClient jenkins = mock(AgentJenkinsClient.class);
    final AgentGitLabClient gitlab = mock(AgentGitLabClient.class);
    final Map<String, Row> rows = new LinkedHashMap<>();
    final String sha = "a".repeat(40), base = "b".repeat(40), merged = "c".repeat(40);
    final Binding binding = new Binding("test", "BL000011", "scs-saps", "scs/scs-saps", "jenkins",
            "http://localhost:1234/", "test-scs-saps", "TEST", "test-scs-server", "10.10.116.43", "master", true);
    final AgentGitLabClient.Target repo = new AgentGitLabClient.Target(URI.create("http://localhost:9876/"),1,"scs/scs-saps","fake");
    IntakeTestDeploymentService service;

    @BeforeEach void setup() {
        service = new IntakeTestDeploymentService(mapper,intakes,analyses,lines,configs,policies,jenkins,gitlab,json);
        for (long id : List.of(277L,278L)) {
            var intake = new IntakeRecordEntity(); intake.setId(id); intake.setBusinessLineCode("BL000011");
            intake.setDevelopmentBranchName("feature/req-"+id); intake.setDemandStatus("测试中"); intake.setRequirementName("需求"+id);
            when(intakes.findById(id)).thenReturn(intake);
            var analysis = new DevelopmentAnalysisEntity();
            analysis.setDraftJson("{\"workItems\":[{\"systemTags\":[\"scs-saps\"]}]}");
            when(analyses.findLatestByIntakeId(id)).thenReturn(analysis);
            when(gitlab.branch(repo,"feature/req-"+id)).thenReturn(new AgentGitLabClient.Branch("feature/req-"+id,sha,false,false));
        }
        var line = new BusinessLineEntity(); line.setEnabled(true); line.setGitlabGroupName("scs");
        when(lines.findByCode("BL000011")).thenReturn(line);
        when(configs.findPlainValue("intake.testDeploy","bindings")).thenReturn(json.writeValueAsString(List.of(binding)));
        when(configs.requirePlainValue("gitlab.global","webApiUrl")).thenReturn("http://localhost:9876/");
        when(configs.requirePlainValue("gitlab.global","accessToken")).thenReturn("fake");
        when(configs.requireSecretValue("jenkins.credentials","token")).thenReturn("fake");
        when(policies.current()).thenReturn(new AgentJenkinsPolicy.Policy(List.of(new AgentJenkinsPolicy.Instance(
                "jenkins","Jenkins",binding.baseUrl(),"tester","jenkins.credentials","token",true)),List.of()));
        when(gitlab.resolve(any(),eq("scs/scs-saps"),eq("fake"))).thenReturn(repo);
        when(gitlab.branch(repo,"master")).thenReturn(new AgentGitLabClient.Branch("master",base,true,true));
        when(jenkins.testJobDefinition(any())).thenReturn(json.readTree("""
            {"fullName":"test-scs-saps","buildable":true,"property":[{"parameterDefinitions":[
             {"name":"deploy_test_env","choices":["test-scs-server"]},
             {"name":"project_branch"},{"name":"thctay_public_branch"}]}]}
            """));
        when(mapper.insert(any())).thenAnswer(call -> {
            Row row=call.getArgument(0);
            if(rows.values().stream().anyMatch(r -> row.activeKey().equals(r.activeKey()))) throw new DuplicateKeyException("active");
            rows.put(row.id(),row); return 1;
        });
        when(mapper.find(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        when(mapper.update(any())).thenAnswer(call -> { Row row=call.getArgument(0);rows.put(row.id(),row);return 1; });
        when(mapper.claim(anyString())).thenAnswer(call -> {
            Row r=rows.get(call.getArgument(0));
            if(!"PENDING".equals(r.status()))return 0;
            rows.put(r.id(),new Row(r.id(),r.intakeId(),r.activeKey(),r.planJson(),"PREPARING",null,null,null,null,null,r.requestedBy(),null,null));
            return 1;
        });
        when(mapper.pending()).thenAnswer(call -> rows.values().stream().filter(r -> Set.of("PENDING","QUEUED","RUNNING").contains(r.status())).toList());
        when(jenkins.submit(any(),any())).thenReturn(45L);
    }
    Request single() { return new Request("test",List.of(277L),null); }

    @Test void singleUsesRemoteDevelopmentBranchAndExplicitTestParameters() {
        var release=service.submit(277L,single(),"tester");
        assertEquals(sha,release.plan().expectedSha());
        verify(jenkins,never()).submit(any(),any());
        service.watch(); service.watch();
        verify(jenkins,times(1)).submit(any(),eq(Map.of("project_branch","feature/req-277",
                "deploy_test_env","test-scs-server","thctay_public_branch","master")));
        verify(gitlab,never()).createTestBranch(any(),any(),any());
    }

    @Test void rejectsProductionEnvironmentAndJob() {
        for (Binding bad : List.of(
                new Binding("test","BL000011","scs-saps","scs/scs-saps","jenkins",binding.baseUrl(),"prod-scs-saps","TEST","test-scs-server","host","master",true),
                new Binding("test","BL000011","scs-saps","scs/scs-saps","jenkins",binding.baseUrl(),"test-scs-saps","PROD","test-scs-server","host","master",true),
                // 非测试环境的 Job 与发布目标前缀必须与环境编码一致，防止借 SIT 名义触发 test 目标
                new Binding("sit","BL000011","scs-saps","scs/scs-saps","jenkins",binding.baseUrl(),"test-scs-saps","SIT","test-scs-server","host","master",true),
                new Binding("sit","BL000011","scs-saps","scs/scs-saps","jenkins",binding.baseUrl(),"sit-scs-saps","SIT","test-scs-server","host","master",true))) {
            when(configs.findPlainValue("intake.testDeploy","bindings")).thenReturn(json.writeValueAsString(List.of(bad)));
            assertThrows(IllegalArgumentException.class,()->service.submit(277L,single(),"tester"));
        }
        verifyNoInteractions(jenkins); verify(mapper,never()).insert(any());
    }

    @Test void sitBindingWithMatchingPrefixSubmits() {
        Binding sit = new Binding("sit","BL000011","scs-saps","scs/scs-saps","jenkins",binding.baseUrl(),
                "sit-scs-saps","SIT","sit-scs-server","10.10.116.43","master",true);
        when(configs.findPlainValue("intake.testDeploy","bindings")).thenReturn(json.writeValueAsString(List.of(sit)));
        when(jenkins.testJobDefinition(any())).thenReturn(json.readTree("""
            {"fullName":"sit-scs-saps","buildable":true,"property":[{"parameterDefinitions":[
             {"name":"deploy_test_env","choices":["sit-scs-server"]},
             {"name":"project_branch"},{"name":"thctay_public_branch"}]}]}
            """));
        var release=service.submit(277L,new Request("sit",List.of(277L),null),"tester");
        service.watch(); service.watch();
        verify(jenkins,times(1)).submit(any(),eq(Map.of("project_branch","feature/req-277",
                "deploy_test_env","sit-scs-server","thctay_public_branch","master")));
        assertEquals("SIT",release.plan().binding().environmentCode());
    }

    @Test void blocksMixedProductionChoiceInJenkins() {
        when(jenkins.testJobDefinition(any())).thenReturn(json.readTree("""
            {"fullName":"test-scs-saps","buildable":true,"property":[{"parameterDefinitions":[
             {"name":"deploy_test_env","choices":["test-scs-server","prod-scs-server"]},
             {"name":"project_branch"},{"name":"thctay_public_branch"}]}]}
            """));
        assertThrows(IllegalArgumentException.class,()->service.submit(277L,single(),"tester"));
        verify(mapper,never()).insert(any());
    }

    @Test void rejectsMasterAsRequirementBranch() {
        intakes.findById(277L).setDevelopmentBranchName("master");
        assertThrows(IllegalArgumentException.class,()->service.submit(277L,single(),"tester"));
        verifyNoInteractions(jenkins);
    }

    @Test void duplicateSubmissionDoesNotBuildTwice() {
        service.submit(277L,single(),"tester");
        assertThrows(IllegalArgumentException.class,()->service.submit(277L,single(),"tester"));
        verify(jenkins,never()).submit(any(),any());
    }

    @Test void unknownSubmissionKeepsLockAndNeverResends() {
        when(jenkins.submit(any(),any())).thenThrow(new IllegalStateException("timeout"));
        var release=service.submit(277L,single(),"tester"); service.watch(); service.watch();
        assertEquals("UNKNOWN",rows.get(release.id()).status());
        assertNotNull(rows.get(release.id()).activeKey());
        verify(jenkins,times(1)).submit(any(),any());
    }

    @Test void mergesFromMasterAndBugFixReusesSameTestBranch() {
        var first=service.submit(277L,new Request("test",List.of(278L),null),"tester");
        assertTrue(first.plan().branch().startsWith("test/workhub-")); assertEquals(base,first.plan().baseSha());
        String branch=first.plan().branch();
        when(gitlab.branch(repo,branch)).thenReturn(null,new AgentGitLabClient.Branch(branch,merged,false,false));
        service.watch();
        verify(gitlab).createTestBranch(repo,branch,base);
        verify(gitlab).mergeIntoTestBranch(repo,"feature/req-277",branch,sha);
        verify(gitlab).mergeIntoTestBranch(repo,"feature/req-278",branch,sha);
        Row old=rows.get(first.id());
        rows.put(old.id(),new Row(old.id(),old.intakeId(),null,old.planJson(),"SUCCESS",45L,10L,null,merged,null,"tester",null,null));
        String fixed="d".repeat(40);
        when(gitlab.branch(repo,"feature/req-277")).thenReturn(new AgentGitLabClient.Branch("feature/req-277",fixed,false,false));
        var retry=service.submit(277L,new Request("test",List.of(278L),first.id()),"tester");
        assertEquals(branch,retry.plan().branch()); service.watch();
        verify(gitlab,times(1)).createTestBranch(any(),any(),any());
        verify(gitlab).mergeIntoTestBranch(repo,"feature/req-277",branch,fixed);
    }

    @Test void conflictStopsBeforeJenkinsAndDoesNotTouchMaster() {
        var release=service.submit(277L,new Request("test",List.of(278L),null),"tester");
        doThrow(new IllegalArgumentException("合并冲突")).when(gitlab).mergeIntoTestBranch(any(),any(),any(),any());
        service.watch(); assertEquals("FAILED",rows.get(release.id()).status());
        verify(jenkins,never()).submit(any(),any());
        verify(gitlab,never()).mergeIntoTestBranch(any(),any(),eq("master"),any());
    }

    @Test void queueAndActualRepositoryVersionAreVerified() {
        var release=service.submit(277L,single(),"tester");service.watch();
        when(jenkins.queue(any(),eq(45L),any())).thenReturn(new AgentJenkinsClient.QueueState("STARTED",45L,124L,true));
        when(jenkins.build(any(),eq(124L),any())).thenReturn(new AgentJenkinsClient.BuildState("FINISHED",124L,"SUCCESS",true,45L));
        when(jenkins.repositoryRevision(any(),eq(124L),eq("scs/scs-saps"))).thenReturn(base);
        service.watch(); assertEquals("VERSION_MISMATCH",rows.get(release.id()).status());
        assertEquals(base,rows.get(release.id()).actualSha());
    }
}
