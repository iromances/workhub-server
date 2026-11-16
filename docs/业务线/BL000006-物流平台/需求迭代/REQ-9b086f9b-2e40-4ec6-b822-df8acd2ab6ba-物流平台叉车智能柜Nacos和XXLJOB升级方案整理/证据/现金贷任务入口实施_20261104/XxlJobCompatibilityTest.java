package com.thchengtay.cashloan.loan;

import com.thchengtay.cashloan.service.process.IProcessEngineServerJob;
import com.xxl.job.core.context.XxlJobContext;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.handler.impl.MethodJobHandler;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class XxlJobCompatibilityTest {

    @After
    public void clearContext() {
        XxlJobContext.setXxlJobContext(null);
    }

    @Test
    public void handlersKeepNamesAndUseOfficialNoArgContract() {
        Set<String> names = new HashSet<>();
        for (Class<?> type : Arrays.asList(LoanApplySchedualJob.class,
                LoanContractSchedualJob.class, LoanQuotaJob.class,
                LoanRepayPlanSchedualJob.class, LoanUnfreezeSchedualJob.class,
                ReduceTaskJob.class, RepayApplySchedualJob.class)) {
            for (Method method : type.getDeclaredMethods()) {
                XxlJob job = method.getAnnotation(XxlJob.class);
                if (job != null) {
                    assertTrue(names.add(job.value()));
                    assertEquals(0, method.getParameterCount());
                    assertEquals(void.class, method.getReturnType());
                }
            }
        }
        assertEquals(new HashSet<>(Arrays.asList("loanApplyJob", "getLoanContractFileJob",
                "loanQuotaJob", "loanRepayPlanSchedualJon", "loanUnfreezeSchedualJob",
                "reduceTaskJob", "repayApplyJob")), names);
    }

    @Test
    public void officialHandlerPassesContextParameterInOriginalOrder() throws Exception {
        RecordingEngine engine = new RecordingEngine();
        XxlJobContext context = context("test-biz-type");
        handler(engine).execute();
        assertEquals(Arrays.asList("strategy:test-biz-type", "process:test-biz-type"), engine.calls);
        assertEquals(XxlJobContext.HANDLE_CODE_SUCCESS, context.getHandleCode());
    }

    @Test
    public void blankParameterFailsWithoutRunningBusiness() throws Exception {
        for (String param : Arrays.asList(null, "", " \t ")) {
            RecordingEngine engine = new RecordingEngine();
            XxlJobContext context = context(param);
            handler(engine).execute();
            assertEquals(XxlJobContext.HANDLE_CODE_FAIL, context.getHandleCode());
            assertEquals("bizType不能为空", context.getHandleMsg());
            assertTrue(engine.calls.isEmpty());
        }
    }

    @Test
    public void businessExceptionPropagatesAndStopsRemainingSteps() throws Exception {
        RecordingEngine engine = new RecordingEngine();
        engine.failure = new IllegalStateException("synthetic failure");
        context("test-biz-type");
        try {
            handler(engine).execute();
            fail("Business failure must propagate to the executor");
        } catch (InvocationTargetException exception) {
            assertSame(engine.failure, exception.getCause());
        }
        assertEquals(Arrays.asList("strategy:test-biz-type"), engine.calls);
    }

    private XxlJobContext context(String param) {
        XxlJobContext context = new XxlJobContext(1L, param, null, 0, 1);
        XxlJobContext.setXxlJobContext(context);
        return context;
    }

    private MethodJobHandler handler(RecordingEngine engine) throws Exception {
        RepayApplySchedualJob job = new RepayApplySchedualJob();
        Field field = RepayApplySchedualJob.class.getDeclaredField("processEngineServerJob");
        field.setAccessible(true);
        field.set(job, engine);
        return new MethodJobHandler(job,
                RepayApplySchedualJob.class.getMethod("repayApplyJob"), null, null);
    }

    private static class RecordingEngine implements IProcessEngineServerJob {
        private final List<String> calls = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public void strategyJobByType(String bizType) {
            calls.add("strategy:" + bizType);
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public void processEngineJobByType(String bizType) {
            calls.add("process:" + bizType);
        }

        @Override public void strategyJob() { throw new AssertionError("Unexpected unfiltered call"); }
        @Override public void processEngineJob() { throw new AssertionError("Unexpected unfiltered call"); }
        @Override public void notifyJob() { throw new AssertionError("Unexpected notify call"); }
        @Override public void doEngineProcessByLock(String bizId) { throw new AssertionError("Unexpected process call"); }
    }
}
