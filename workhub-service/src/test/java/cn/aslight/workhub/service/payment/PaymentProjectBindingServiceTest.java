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
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        projectEntity.setBusinessLine("演示业务");
        when(projectService.requireExisting(3L)).thenReturn(projectEntity);
        PaymentMerchantEntity merchantEntity = new PaymentMerchantEntity();
        merchantEntity.setId(8L);
        when(merchantMapper.findEntityById(8L)).thenReturn(merchantEntity);
        when(merchantMapper.findPurposeCodes(8L)).thenReturn(java.util.List.of("WITHHOLD"));
        when(bindingMapper.findEntityByUniqueKey(3L, "演示业务", 8L, "WITHHOLD")).thenReturn(null);
        doAnswer(invocation -> {
            PaymentProjectBindingEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        }).when(bindingMapper).insert(any(PaymentProjectBindingEntity.class));
        when(bindingMapper.findResponseById(100L)).thenReturn(new PaymentProjectBindingResponse(
                100L,
                3L,
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
        request.setBusinessLine("演示业务");
        request.setMerchantId(8L);
        request.setPurposeCode("WITHHOLD");
        request.setPriority(1);
        request.setDefaultBinding(true);
        request.setStatus("ACTIVE");

        PaymentProjectBindingResponse response = service.create(request, "admin");

        verify(bindingMapper).clearDefaultBindings(3L, "演示业务", "WITHHOLD", null);
        assertEquals(100L, response.id());
        assertEquals("WITHHOLD", response.purposeCode());
    }

    @Test
    void create_shouldAllowBusinessLineBindingWithoutProject() {
        PaymentProjectBindingMapper bindingMapper = mock(PaymentProjectBindingMapper.class);
        PaymentMerchantMapper merchantMapper = mock(PaymentMerchantMapper.class);
        ProjectService projectService = mock(ProjectService.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentProjectBindingService service = new PaymentProjectBindingService(bindingMapper, merchantMapper, projectService, auditService);

        PaymentMerchantEntity merchantEntity = new PaymentMerchantEntity();
        merchantEntity.setId(8L);
        when(merchantMapper.findEntityById(8L)).thenReturn(merchantEntity);
        when(merchantMapper.findPurposeCodes(8L)).thenReturn(java.util.List.of("WITHHOLD"));
        when(bindingMapper.findEntityByUniqueKey(null, "资产业务", 8L, "WITHHOLD")).thenReturn(null);
        doAnswer(invocation -> {
            PaymentProjectBindingEntity entity = invocation.getArgument(0);
            entity.setId(101L);
            return 1;
        }).when(bindingMapper).insert(any(PaymentProjectBindingEntity.class));
        when(bindingMapper.findResponseById(101L)).thenReturn(new PaymentProjectBindingResponse(
                101L,
                null,
                "资产业务",
                null,
                null,
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
        request.setBusinessLine("资产业务");
        request.setProjectId(null);
        request.setMerchantId(8L);
        request.setPurposeCode("WITHHOLD");
        request.setPriority(1);
        request.setDefaultBinding(true);
        request.setStatus("ACTIVE");

        PaymentProjectBindingResponse response = service.create(request, "admin");

        verify(projectService, never()).requireExisting(any());
        verify(bindingMapper).clearDefaultBindings(isNull(), eq("资产业务"), eq("WITHHOLD"), isNull());
        ArgumentCaptor<PaymentProjectBindingEntity> entityCaptor = ArgumentCaptor.forClass(PaymentProjectBindingEntity.class);
        verify(bindingMapper).insert(entityCaptor.capture());
        assertEquals(null, entityCaptor.getValue().getProjectId());
        assertEquals("资产业务", entityCaptor.getValue().getBusinessLine());
        assertEquals(101L, response.id());
        assertEquals(null, response.projectId());
        assertEquals("资产业务", response.businessLine());
    }

    @Test
    void resolve_shouldFallbackToBusinessLineBindingWhenProjectBindingMissing() {
        PaymentProjectBindingMapper bindingMapper = mock(PaymentProjectBindingMapper.class);
        PaymentMerchantMapper merchantMapper = mock(PaymentMerchantMapper.class);
        ProjectService projectService = mock(ProjectService.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentProjectBindingService service = new PaymentProjectBindingService(bindingMapper, merchantMapper, projectService, auditService);

        ProjectEntity projectEntity = new ProjectEntity();
        projectEntity.setId(3L);
        projectEntity.setBusinessLine("资产业务");
        when(projectService.requireExisting(3L)).thenReturn(projectEntity);
        when(bindingMapper.resolveActiveBinding(3L, "WITHHOLD")).thenReturn(null);
        when(bindingMapper.resolveBusinessLineActiveBinding("资产业务", "WITHHOLD")).thenReturn(new PaymentProjectBindingResponse(
                102L,
                null,
                "资产业务",
                null,
                null,
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

        PaymentProjectBindingResponse response = service.resolve(3L, "WITHHOLD");

        assertEquals(102L, response.id());
        assertEquals(null, response.projectId());
        assertEquals("资产业务", response.businessLine());
    }

    @Test
    void create_shouldRejectUnsupportedMerchantPurpose() {
        PaymentProjectBindingMapper bindingMapper = mock(PaymentProjectBindingMapper.class);
        PaymentMerchantMapper merchantMapper = mock(PaymentMerchantMapper.class);
        ProjectService projectService = mock(ProjectService.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentProjectBindingService service = new PaymentProjectBindingService(bindingMapper, merchantMapper, projectService, auditService);

        ProjectEntity projectEntity = new ProjectEntity();
        projectEntity.setId(3L);
        projectEntity.setBusinessLine("演示业务");
        when(projectService.requireExisting(3L)).thenReturn(projectEntity);
        PaymentMerchantEntity merchantEntity = new PaymentMerchantEntity();
        merchantEntity.setId(8L);
        when(merchantMapper.findEntityById(8L)).thenReturn(merchantEntity);
        when(merchantMapper.findPurposeCodes(8L)).thenReturn(java.util.List.of("WITHHOLD"));

        PaymentProjectBindingSaveRequest request = new PaymentProjectBindingSaveRequest();
        request.setProjectId(3L);
        request.setBusinessLine("演示业务");
        request.setMerchantId(8L);
        request.setPurposeCode("PAY_OUT");
        request.setPriority(1);
        request.setDefaultBinding(true);
        request.setStatus("ACTIVE");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.create(request, "admin"));
        assertEquals("商户号不支持用途：TRANSFER", exception.getMessage());
    }
}
