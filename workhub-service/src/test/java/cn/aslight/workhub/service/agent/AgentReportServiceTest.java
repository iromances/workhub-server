package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentReportMapper;
import cn.aslight.workhub.model.agent.AgentRunEntity;
import org.apache.ibatis.session.Configuration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AgentReportServiceTest {
    private Connection connection;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private AgentReportService service;
    private AgentReportMapper mapper;
    @BeforeEach void setup() throws Exception {
        var ds=new JdbcDataSource(); ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL");
        connection=ds.getConnection(); jdbc=new JdbcTemplate(ds);
        ScriptUtils.executeSqlScript(connection,new FileSystemResource("../workhub-bootstrap/src/main/resources/db/schema/mysql/V20261122_150000__agent_structured_reports.sql"));
        var config=new Configuration(); config.setMapUnderscoreToCamelCase(true); config.addMapper(AgentReportMapper.class);
        var factory=new SqlSessionFactoryBean(); factory.setDataSource(ds);factory.setConfiguration(config);
        mapper=new SqlSessionTemplate(factory.getObject()).getMapper(AgentReportMapper.class);
        var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
        var advice=new TransactionInterceptor();advice.setTransactionManager(manager);advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy=new ProxyFactory(new AgentReportService(mapper,new ObjectMapper()));proxy.addAdvice(advice);service=(AgentReportService)proxy.getProxy();
    }
    @AfterEach void close() throws Exception { connection.close(); }
    private AgentRunEntity run(String id) {
        return new AgentRunEntity(id,"task","agent","manual","user","{}","{}","{}","RUNNING",1,1,false,"owner",LocalDateTime.now().plusMinutes(1),null,null,null,0,LocalDateTime.now(),LocalDateTime.now());
    }
    @Test void roundTripFiveTablesAndIdempotentSave() throws Exception {
        var result=AgentReportContractTest.fixture();
        tx.executeWithoutResult(s -> service.save(run("r"),result,"raw"));
        tx.executeWithoutResult(s -> service.save(run("r"),result,"raw"));
        assertEquals(result.path("report").path("conclusion"),service.read("r").path("conclusion"));
        for(String suffix:new String[]{"","_check","_finding","_evidence","_action"}) assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_report"+suffix,Integer.class));
        var restored=new ObjectMapper().createObjectNode().put("schemaVersion",2).put("type","RESULT");restored.set("report",service.read("r"));
        AgentReportContract.validate(restored);
        assertNull(service.read("missing"));
    }
    @Test void conflictingPayloadAndMissingTransactionAreRejected() throws Exception {
        var result=AgentReportContractTest.fixture();
        assertThrows(IllegalTransactionStateException.class,()->service.save(run("r"),result,"raw"));
        tx.executeWithoutResult(s -> service.save(run("r"),result,"raw"));
        ((ObjectNode)result.path("report")).put("conclusion","different");
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s -> service.save(run("r"),result,"raw")));
        assertNotEquals("different",service.read("r").path("conclusion").asText());
    }
    @Test void databaseFailureRollsBackAllFiveTables() throws Exception {
        var result=AgentReportContractTest.fixture();
        jdbc.execute("ALTER TABLE pm_agent_report_action ADD CONSTRAINT force_failure CHECK (ordinal < 0)");
        assertThrows(RuntimeException.class,()->tx.executeWithoutResult(s -> service.save(run("r"),result,"raw")));
        for(String suffix:new String[]{"","_check","_finding","_evidence","_action"}) assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_report"+suffix,Integer.class));
    }
    @Test void deletionCleansChildrenForRunTaskAndAgent() throws Exception {
        var result=AgentReportContractTest.fixture();
        for(String kind:new String[]{"run","task","agent"}) {
            tx.executeWithoutResult(s -> service.save(run("r"),result,"raw"));
            int removed=tx.execute(s -> switch(kind) {case "run" -> service.deleteRun("r");case "task" -> service.deleteTask("task");default -> service.deleteAgent("agent");});
            assertEquals(5,removed); assertNull(service.read("r"));
            for(String suffix:new String[]{"_check","_finding","_evidence","_action"}) assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pm_agent_report"+suffix,Integer.class));
        }
    }
}
