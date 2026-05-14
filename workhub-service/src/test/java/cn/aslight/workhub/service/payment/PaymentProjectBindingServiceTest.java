package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.dao.payment.PaymentMerchantMapper;
import cn.aslight.workhub.dao.payment.PaymentProjectBindingMapper;
import cn.aslight.workhub.model.payment.PaymentMerchantEntity;
import cn.aslight.workhub.model.payment.PaymentProjectBindingEntity;
import cn.aslight.workhub.model.payment.PaymentProjectBindingResponse;
import cn.aslight.workhub.model.payment.PaymentProjectBindingSaveRequest;
import cn.aslight.workhub.model.project.ProjectEntity;
import cn.aslight.workhub.service.project.ProjectService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentProjectBindingServiceTest {

    @Test
    void create_shouldClearPreviousDefaultBindingWhenMarkedDefault() {
        PaymentProjectBindingMapper bindingMapper = mock(PaymentProjectBindingMapper.class);
        PaymentMerchantMapper merchantMapper = mock(PaymentMerchantMapper.class);
        ProjectService projectService = mock(ProjectService.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentProjectBindingService service = new PaymentProjectBindingService(bindingMapper, merchantMapper, projectService, auditService);

        ProjectEntity projectEntity = new ProjectEntity();
        projectEntity.setId(3L);
        when(projectService.requireExisting(3L)).thenReturn(projectEntity);
        PaymentMerchantEntity merchantEntity = new PaymentMerchantEntity();
        merchantEntity.setId(8L);
        when(merchantMapper.findEntityById(8L)).thenReturn(merchantEntity);
        when(bindingMapper.findEntityByUniqueKey(3L, 8L, "WITHHOLD")).thenReturn(null);
        doAnswer(invocation -> {
            PaymentProjectBindingEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        }).when(bindingMapper).insert(any(PaymentProjectBindingEntity.class));
        when(bindingMapper.findResponseById(100L)).thenReturn(new PaymentProjectBindingResponse(
                100L,
                3L,
                "DEMO-BIZ",
                "演示业务线",
                "演示业务",
                "DEMO",
                "演示项目",
                8L,
                "M001",
                "易宝主商户",
                1L,
                "YEEPAY",
                "易宝",
                "PROD",
                "WITHHOLD",
                java.util.List.of("WITHHOLD"),
                1,
                true,
                "ACTIVE",
                null,
                java.util.List.of(),
                null,
                null
        ));

        PaymentProjectBindingSaveRequest request = new PaymentProjectBindingSaveRequest();
        request.setProjectId(3L);
        request.setMerchantId(8L);
        request.setPurposeCode("WITHHOLD");
        request.setPriority(1);
        request.setDefaultBinding(true);
        request.setStatus("ACTIVE");

        PaymentProjectBindingResponse response = service.create(request, "admin");

        verify(bindingMapper).clearDefaultBindings(3L, "WITHHOLD", null);
        assertEquals(100L, response.id());
        assertEquals("WITHHOLD", response.purposeCode());
    }
}
