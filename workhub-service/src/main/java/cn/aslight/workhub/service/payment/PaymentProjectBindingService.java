package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.dao.payment.PaymentMerchantMapper;
import cn.aslight.workhub.dao.payment.PaymentProjectBindingMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.payment.PaymentBindingRelationEntity;
import cn.aslight.workhub.model.payment.PaymentBindingRelationResponse;
import cn.aslight.workhub.model.payment.PaymentBindingRelationSaveRequest;
import cn.aslight.workhub.model.payment.PaymentMerchantEntity;
import cn.aslight.workhub.model.payment.PaymentProjectBindingEntity;
import cn.aslight.workhub.model.payment.PaymentProjectBindingResponse;
import cn.aslight.workhub.model.payment.PaymentProjectBindingSaveRequest;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.model.project.ProjectEntity;
import cn.aslight.workhub.service.project.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;

/**
 * 项目商户绑定服务。
 */
@Service
public class PaymentProjectBindingService {

    private final PaymentProjectBindingMapper paymentProjectBindingMapper;
    private final PaymentMerchantMapper paymentMerchantMapper;
    private final ProjectService projectService;
    private final PaymentAuditService paymentAuditService;
    private final BusinessLineMapper businessLineMapper;

    @Autowired
    public PaymentProjectBindingService(PaymentProjectBindingMapper paymentProjectBindingMapper,
                                        PaymentMerchantMapper paymentMerchantMapper,
                                        ProjectService projectService,
                                        PaymentAuditService paymentAuditService,
                                        BusinessLineMapper businessLineMapper) {
        this.paymentProjectBindingMapper = paymentProjectBindingMapper;
        this.paymentMerchantMapper = paymentMerchantMapper;
        this.projectService = projectService;
        this.paymentAuditService = paymentAuditService;
        this.businessLineMapper = businessLineMapper;
    }

    PaymentProjectBindingService(PaymentProjectBindingMapper paymentProjectBindingMapper,
                                 PaymentMerchantMapper paymentMerchantMapper,
                                 ProjectService projectService,
                                 PaymentAuditService paymentAuditService) {
        this(paymentProjectBindingMapper, paymentMerchantMapper, projectService, paymentAuditService, null);
    }

    public List<PaymentProjectBindingResponse> list(Long projectId, String businessLine, Long merchantId, String purposeCode, String status) {
        return paymentProjectBindingMapper.findAll(
                projectId,
                PaymentCatalogs.trimToNull(businessLine),
                merchantId,
                PaymentCatalogs.trimToNull(purposeCode) == null ? null : PaymentCatalogs.normalizePurpose(purposeCode),
                PaymentCatalogs.trimToNull(status) == null ? null : PaymentCatalogs.normalizeStatus(status, "status")
        ).stream().map(this::withBindingDetails).toList();
    }

    public PaymentProjectBindingResponse resolve(Long projectId, String purposeCode) {
        ProjectEntity project = projectService.requireExisting(projectId);
        String normalizedPurposeCode = PaymentCatalogs.normalizePurpose(purposeCode);
        PaymentProjectBindingResponse response = paymentProjectBindingMapper.resolveActiveBinding(
                projectId,
                normalizedPurposeCode
        );
        if (response == null) {
            response = paymentProjectBindingMapper.resolveBusinessLineActiveBinding(
                    paymentBusinessLineIdentity(project),
                    normalizedPurposeCode
            );
        }
        if (response == null) {
            throw new IllegalArgumentException("未找到可用的项目支付商户绑定");
        }
        return withBindingDetails(response);
    }

    @Transactional
    public PaymentProjectBindingResponse create(PaymentProjectBindingSaveRequest request, String operatorUserName) {
        ResolvedBusinessLine businessLine = validateReferences(request);
        List<String> purposeCodes = normalizePurposeCodes(request);
        ensureMerchantSupportsPurposes(request.getMerchantId(), purposeCodes);
        for (String purposeCode : purposeCodes) {
            ensureUniqueKey(request.getProjectId(), businessLine.identity(), request.getMerchantId(), purposeCode, null);
        }
        PaymentProjectBindingEntity entity = toEntity(request);
        entity.setBusinessLineCode(businessLine.code());
        entity.setBusinessLine(businessLine.name());
        if (Boolean.TRUE.equals(entity.getDefaultBinding())) {
            for (String purposeCode : purposeCodes) {
                paymentProjectBindingMapper.clearDefaultBindings(entity.getProjectId(), businessLine.identity(), purposeCode, null);
            }
        }
        paymentProjectBindingMapper.insert(entity);
        savePurposes(entity.getId(), purposeCodes);
        saveRelations(entity.getId(), request.getRelations());
        paymentAuditService.record(
                "PROJECT_BINDING",
                entity.getId(),
                "CREATE",
                "新增项目商户绑定",
                "businessLine=" + entity.getBusinessLine() + ",projectId=" + entity.getProjectId() + ",merchantId=" + entity.getMerchantId() + ",purpose=" + entity.getPurposeCode(),
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
        ResolvedBusinessLine businessLine = validateReferences(request);
        List<String> purposeCodes = normalizePurposeCodes(request);
        ensureMerchantSupportsPurposes(request.getMerchantId(), purposeCodes);
        for (String purposeCode : purposeCodes) {
            ensureUniqueKey(request.getProjectId(), businessLine.identity(), request.getMerchantId(), purposeCode, id);
        }
        PaymentProjectBindingEntity entity = toEntity(request);
        entity.setId(id);
        entity.setBusinessLineCode(businessLine.code());
        entity.setBusinessLine(businessLine.name());
        if (Boolean.TRUE.equals(entity.getDefaultBinding())) {
            for (String purposeCode : purposeCodes) {
                paymentProjectBindingMapper.clearDefaultBindings(entity.getProjectId(), businessLine.identity(), purposeCode, id);
            }
        }
        paymentProjectBindingMapper.update(entity);
        savePurposes(id, purposeCodes);
        saveRelations(id, request.getRelations());
        paymentAuditService.record(
                "PROJECT_BINDING",
                id,
                "UPDATE",
                "更新项目商户绑定",
                "businessLine=" + entity.getBusinessLine() + ",projectId=" + entity.getProjectId() + ",merchantId=" + entity.getMerchantId() + ",purpose=" + entity.getPurposeCode(),
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

    private PaymentProjectBindingResponse withBindingDetails(PaymentProjectBindingResponse response) {
        List<String> purposeCodes = paymentProjectBindingMapper.findPurposeCodes(response.id());
        if (purposeCodes == null || purposeCodes.isEmpty()) {
            purposeCodes = List.of(response.purposeCode());
        }
        List<PaymentBindingRelationResponse> relations = paymentProjectBindingMapper.findRelations(response.id());
        return new PaymentProjectBindingResponse(
                response.id(),
                response.projectId(),
                response.businessLineCode(),
                response.businessLine(),
                response.projectCode(),
                response.projectName(),
                response.merchantId(),
                response.merchantCode(),
                response.merchantName(),
                response.channelId(),
                response.channelCode(),
                response.channelName(),
                response.environment(),
                response.purposeCode(),
                purposeCodes,
                response.priority(),
                response.defaultBinding(),
                response.status(),
                response.remark(),
                relations == null ? List.of() : relations,
                response.createdAt(),
                response.updatedAt()
        );
    }

    private ResolvedBusinessLine validateReferences(PaymentProjectBindingSaveRequest request) {
        ResolvedBusinessLine resolvedBusinessLine = resolveBusinessLine(request.getBusinessLineCode(), request.getBusinessLine());
        if (resolvedBusinessLine == null) {
            throw new IllegalArgumentException("业务线不能为空");
        }
        if (request.getProjectId() != null) {
            ProjectEntity project = projectService.requireExisting(request.getProjectId());
            String projectBusinessLineCode = PaymentCatalogs.trimToNull(project.getBusinessLineCode());
            String projectBusinessLine = PaymentCatalogs.trimToNull(project.getBusinessLine());
            boolean codeMatches = projectBusinessLineCode != null && projectBusinessLineCode.equals(resolvedBusinessLine.identity());
            boolean nameMatches = projectBusinessLine != null && projectBusinessLine.equals(resolvedBusinessLine.name());
            if (!codeMatches && !nameMatches) {
                throw new IllegalArgumentException("项目不属于所选业务线");
            }
        }
        PaymentMerchantEntity merchantEntity = paymentMerchantMapper.findEntityById(request.getMerchantId());
        if (merchantEntity == null) {
            throw new IllegalArgumentException("支付商户不存在");
        }
        return resolvedBusinessLine;
    }

    private void validateMerchant(Long merchantId) {
        if (paymentMerchantMapper.findEntityById(merchantId) == null) {
            throw new IllegalArgumentException("支付商户不存在");
        }
    }

    private void ensureUniqueKey(Long projectId, String businessLine, Long merchantId, String purposeCode, Long currentId) {
        PaymentProjectBindingEntity existing = paymentProjectBindingMapper.findEntityByUniqueKey(
                projectId,
                businessLine,
                merchantId,
                PaymentCatalogs.normalizePurpose(purposeCode)
        );
        if (existing != null && !existing.getId().equals(currentId)) {
            throw new IllegalArgumentException(projectId == null ? "同业务线、商户、用途的通用绑定已存在" : "同项目、商户、用途的绑定已存在");
        }
    }

    private void ensureMerchantSupportsPurposes(Long merchantId, List<String> purposeCodes) {
        List<String> supportedPurposeCodes = paymentMerchantMapper.findPurposeCodes(merchantId);
        if (supportedPurposeCodes == null || supportedPurposeCodes.isEmpty()) {
            throw new IllegalArgumentException("商户号未维护支持用途");
        }
        for (String purposeCode : purposeCodes) {
            if (!supportedPurposeCodes.contains(purposeCode)) {
                throw new IllegalArgumentException("商户号不支持用途：" + purposeCode);
            }
        }
    }

    private PaymentProjectBindingEntity toEntity(PaymentProjectBindingSaveRequest request) {
        PaymentProjectBindingEntity entity = new PaymentProjectBindingEntity();
        entity.setProjectId(request.getProjectId());
        entity.setBusinessLineCode(PaymentCatalogs.trimToNull(request.getBusinessLineCode()));
        entity.setBusinessLine(PaymentCatalogs.trimToNull(request.getBusinessLine()));
        entity.setMerchantId(request.getMerchantId());
        entity.setPurposeCode(normalizePurposeCodes(request).getFirst());
        entity.setPriority(PaymentCatalogs.requirePositivePriority(request.getPriority()));
        entity.setDefaultBinding(Boolean.TRUE.equals(request.getDefaultBinding()));
        entity.setBindingStatus(PaymentCatalogs.normalizeStatus(request.getStatus(), "status"));
        entity.setRemark(PaymentCatalogs.trimToNull(request.getRemark()));
        return entity;
    }

    private ResolvedBusinessLine resolveBusinessLine(String businessLineCode, String businessLine) {
        String normalizedCode = PaymentCatalogs.trimToNull(businessLineCode);
        String normalizedBusinessLine = PaymentCatalogs.trimToNull(businessLine);
        String lookupValue = normalizedCode == null ? normalizedBusinessLine : normalizedCode;
        if (lookupValue == null) {
            return null;
        }
        if (businessLineMapper == null) {
            return new ResolvedBusinessLine(normalizedCode, normalizedBusinessLine, lookupValue);
        }
        BusinessLineEntity entity = normalizedCode == null ? null : businessLineMapper.findByCode(normalizedCode);
        if (entity == null && normalizedBusinessLine != null) {
            entity = businessLineMapper.findByCode(normalizedBusinessLine);
        }
        if (entity == null && normalizedBusinessLine != null) {
            entity = businessLineMapper.findByName(normalizedBusinessLine);
        }
        if (entity == null) {
            throw new IllegalArgumentException("业务线不存在");
        }
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            throw new IllegalArgumentException("业务线已停用");
        }
        String resolvedCode = PaymentCatalogs.trimToNull(entity.getBusinessLineCode());
        String resolvedName = PaymentCatalogs.trimToNull(entity.getBusinessLineName());
        return new ResolvedBusinessLine(resolvedCode, resolvedName, resolvedCode == null ? resolvedName : resolvedCode);
    }

    private String paymentBusinessLineIdentity(ProjectEntity project) {
        String businessLineCode = PaymentCatalogs.trimToNull(project.getBusinessLineCode());
        if (businessLineCode != null) {
            return businessLineCode;
        }
        return PaymentCatalogs.trimToNull(project.getBusinessLine());
    }

    private record ResolvedBusinessLine(String code, String name, String identity) {
    }

    private List<String> normalizePurposeCodes(PaymentProjectBindingSaveRequest request) {
        List<String> source = request.getPurposeCodes();
        if (source == null || source.isEmpty()) {
            source = List.of(request.getPurposeCode());
        }
        List<String> normalized = new ArrayList<>();
        for (String item : source) {
            String purposeCode = PaymentCatalogs.normalizePurpose(item);
            if (!normalized.contains(purposeCode)) {
                normalized.add(purposeCode);
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("purposeCodes 不能为空");
        }
        return normalized;
    }

    private void savePurposes(Long bindingId, List<String> purposeCodes) {
        paymentProjectBindingMapper.deletePurposes(bindingId);
        for (String purposeCode : purposeCodes) {
            paymentProjectBindingMapper.insertPurpose(bindingId, purposeCode);
        }
    }

    private void saveRelations(Long bindingId, List<PaymentBindingRelationSaveRequest> relations) {
        paymentProjectBindingMapper.deleteRelations(bindingId);
        if (relations == null || relations.isEmpty()) {
            return;
        }
        for (PaymentBindingRelationSaveRequest request : relations) {
            validateMerchant(request.getMerchantId());
            PaymentBindingRelationEntity entity = new PaymentBindingRelationEntity();
            entity.setBindingId(bindingId);
            entity.setMerchantId(request.getMerchantId());
            entity.setRelationRole(PaymentCatalogs.normalizeRelationRole(request.getRelationRole()));
            entity.setRelationName(PaymentCatalogs.trimToNull(request.getRelationName()));
            entity.setPriority(PaymentCatalogs.requirePositivePriority(request.getPriority()));
            entity.setRemark(PaymentCatalogs.trimToNull(request.getRemark()));
            paymentProjectBindingMapper.insertRelation(entity);
        }
    }
}
