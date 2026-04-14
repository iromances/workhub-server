package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.dao.payment.PaymentMerchantMapper;
import cn.aslight.workhub.dao.payment.PaymentProjectBindingMapper;
import cn.aslight.workhub.model.payment.PaymentMerchantEntity;
import cn.aslight.workhub.model.payment.PaymentProjectBindingEntity;
import cn.aslight.workhub.model.payment.PaymentProjectBindingResponse;
import cn.aslight.workhub.model.payment.PaymentProjectBindingSaveRequest;
import cn.aslight.workhub.service.project.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 项目商户绑定服务。
 */
@Service
public class PaymentProjectBindingService {

    private final PaymentProjectBindingMapper paymentProjectBindingMapper;
    private final PaymentMerchantMapper paymentMerchantMapper;
    private final ProjectService projectService;
    private final PaymentAuditService paymentAuditService;

    public PaymentProjectBindingService(PaymentProjectBindingMapper paymentProjectBindingMapper,
                                        PaymentMerchantMapper paymentMerchantMapper,
                                        ProjectService projectService,
                                        PaymentAuditService paymentAuditService) {
        this.paymentProjectBindingMapper = paymentProjectBindingMapper;
        this.paymentMerchantMapper = paymentMerchantMapper;
        this.projectService = projectService;
        this.paymentAuditService = paymentAuditService;
    }

    public List<PaymentProjectBindingResponse> list(Long projectId, Long merchantId, String purposeCode, String status) {
        return paymentProjectBindingMapper.findAll(
                projectId,
                merchantId,
                PaymentCatalogs.trimToNull(purposeCode) == null ? null : PaymentCatalogs.normalizePurpose(purposeCode),
                PaymentCatalogs.trimToNull(status) == null ? null : PaymentCatalogs.normalizeStatus(status, "status")
        );
    }

    public PaymentProjectBindingResponse resolve(Long projectId, String purposeCode) {
        projectService.requireExisting(projectId);
        PaymentProjectBindingResponse response = paymentProjectBindingMapper.resolveActiveBinding(
                projectId,
                PaymentCatalogs.normalizePurpose(purposeCode)
        );
        if (response == null) {
            throw new IllegalArgumentException("未找到可用的项目支付商户绑定");
        }
        return response;
    }

    @Transactional
    public PaymentProjectBindingResponse create(PaymentProjectBindingSaveRequest request, String operatorUserName) {
        validateReferences(request.getProjectId(), request.getMerchantId());
        ensureUniqueKey(request.getProjectId(), request.getMerchantId(), request.getPurposeCode(), null);
        PaymentProjectBindingEntity entity = toEntity(request);
        if (Boolean.TRUE.equals(entity.getDefaultBinding())) {
            paymentProjectBindingMapper.clearDefaultBindings(entity.getProjectId(), entity.getPurposeCode(), null);
        }
        paymentProjectBindingMapper.insert(entity);
        paymentAuditService.record(
                "PROJECT_BINDING",
                entity.getId(),
                "CREATE",
                "新增项目商户绑定",
                "projectId=" + entity.getProjectId() + ",merchantId=" + entity.getMerchantId() + ",purpose=" + entity.getPurposeCode(),
                operatorUserName
        );
        return requireBinding(entity.getId());
    }

    @Transactional
    public PaymentProjectBindingResponse update(Long id,
                                                PaymentProjectBindingSaveRequest request,
                                                String operatorUserName) {
        PaymentProjectBindingEntity existing = paymentProjectBindingMapper.findEntityById(id);
        if (existing == null) {
            throw new IllegalArgumentException("项目商户绑定不存在");
        }
        validateReferences(request.getProjectId(), request.getMerchantId());
        ensureUniqueKey(request.getProjectId(), request.getMerchantId(), request.getPurposeCode(), id);
        PaymentProjectBindingEntity entity = toEntity(request);
        entity.setId(id);
        if (Boolean.TRUE.equals(entity.getDefaultBinding())) {
            paymentProjectBindingMapper.clearDefaultBindings(entity.getProjectId(), entity.getPurposeCode(), id);
        }
        paymentProjectBindingMapper.update(entity);
        paymentAuditService.record(
                "PROJECT_BINDING",
                id,
                "UPDATE",
                "更新项目商户绑定",
                "projectId=" + entity.getProjectId() + ",merchantId=" + entity.getMerchantId() + ",purpose=" + entity.getPurposeCode(),
                operatorUserName
        );
        return requireBinding(id);
    }

    private PaymentProjectBindingResponse requireBinding(Long id) {
        PaymentProjectBindingResponse response = paymentProjectBindingMapper.findResponseById(id);
        if (response == null) {
            throw new IllegalArgumentException("项目商户绑定不存在");
        }
        return response;
    }

    private void validateReferences(Long projectId, Long merchantId) {
        projectService.requireExisting(projectId);
        PaymentMerchantEntity merchantEntity = paymentMerchantMapper.findEntityById(merchantId);
        if (merchantEntity == null) {
            throw new IllegalArgumentException("支付商户不存在");
        }
    }

    private void ensureUniqueKey(Long projectId, Long merchantId, String purposeCode, Long currentId) {
        PaymentProjectBindingEntity existing = paymentProjectBindingMapper.findEntityByUniqueKey(
                projectId,
                merchantId,
                PaymentCatalogs.normalizePurpose(purposeCode)
        );
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new IllegalArgumentException("同项目、商户、用途的绑定已存在");
        }
    }

    private PaymentProjectBindingEntity toEntity(PaymentProjectBindingSaveRequest request) {
        PaymentProjectBindingEntity entity = new PaymentProjectBindingEntity();
        entity.setProjectId(request.getProjectId());
        entity.setMerchantId(request.getMerchantId());
        entity.setPurposeCode(PaymentCatalogs.normalizePurpose(request.getPurposeCode()));
        entity.setPriority(PaymentCatalogs.requirePositivePriority(request.getPriority()));
        entity.setDefaultBinding(Boolean.TRUE.equals(request.getDefaultBinding()));
        entity.setBindingStatus(PaymentCatalogs.normalizeStatus(request.getStatus(), "status"));
        entity.setRemark(PaymentCatalogs.trimToNull(request.getRemark()));
        return entity;
    }
}
