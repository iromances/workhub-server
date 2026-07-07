package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.dao.payment.PaymentMerchantMapper;
import cn.aslight.workhub.dao.payment.PaymentMerchantParamMapper;
import cn.aslight.workhub.model.payment.PaymentMerchantEntity;
import cn.aslight.workhub.model.payment.PaymentMerchantParamEntity;
import cn.aslight.workhub.model.payment.PaymentMerchantParamFileUploadRequest;
import cn.aslight.workhub.model.payment.PaymentMerchantParamResponse;
import cn.aslight.workhub.model.payment.PaymentMerchantParamSaveRequest;
import cn.aslight.workhub.model.payment.PaymentSecretDownloadResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * 支付商户参数服务。
 */
@Service
public class PaymentMerchantParamService {

    private final PaymentMerchantMapper paymentMerchantMapper;
    private final PaymentMerchantParamMapper paymentMerchantParamMapper;
    private final PaymentCryptoService paymentCryptoService;
    private final PaymentAuditService paymentAuditService;

    public PaymentMerchantParamService(PaymentMerchantMapper paymentMerchantMapper,
                                       PaymentMerchantParamMapper paymentMerchantParamMapper,
                                       PaymentCryptoService paymentCryptoService,
                                       PaymentAuditService paymentAuditService) {
        this.paymentMerchantMapper = paymentMerchantMapper;
        this.paymentMerchantParamMapper = paymentMerchantParamMapper;
        this.paymentCryptoService = paymentCryptoService;
        this.paymentAuditService = paymentAuditService;
    }

    public List<PaymentMerchantParamResponse> list(Long merchantId) {
        requireMerchant(merchantId);
        return paymentMerchantParamMapper.findByMerchantId(merchantId);
    }

    @Transactional
    public List<PaymentMerchantParamResponse> save(Long merchantId,
                                                   PaymentMerchantParamSaveRequest request,
                                                   String operatorUserName) {
        requireMerchant(merchantId);
        String paramKey = PaymentCatalogs.requireText(request.getParamKey(), "paramKey");
        PaymentMerchantParamEntity existing = paymentMerchantParamMapper.findEntityByMerchantIdAndKey(merchantId, paramKey);
        PaymentMerchantParamEntity entity = toEntity(merchantId, paramKey, request);
        if (existing == null) {
            paymentMerchantParamMapper.insert(entity);
            paymentAuditService.record(
                    "MERCHANT_PARAM",
                    entity.getId(),
                    "CREATE",
                    "新增商户参数",
                    "merchantId=" + merchantId + ",paramKey=" + paramKey,
                    operatorUserName
            );
        } else {
            entity.setId(existing.getId());
            paymentMerchantParamMapper.update(entity);
            paymentAuditService.record(
                    "MERCHANT_PARAM",
                    existing.getId(),
                    "UPDATE",
                    "更新商户参数",
                    "merchantId=" + merchantId + ",paramKey=" + paramKey,
                    operatorUserName
            );
        }
        return list(merchantId);
    }

    @Transactional
    public List<PaymentMerchantParamResponse> update(Long merchantId,
                                                     Long paramId,
                                                     PaymentMerchantParamSaveRequest request,
                                                     String operatorUserName) {
        requireMerchant(merchantId);
        PaymentMerchantParamEntity existing = paymentMerchantParamMapper.findEntityById(paramId);
        if (existing == null || !merchantId.equals(existing.getMerchantId())) {
            throw new IllegalArgumentException("商户参数不存在");
        }
        String paramKey = resolveUpdatedParamKey(merchantId, existing, request.getParamKey());
        PaymentMerchantParamEntity entity = toEntity(merchantId, paramKey, request, existing);
        entity.setId(paramId);
        paymentMerchantParamMapper.update(entity);
        paymentAuditService.record(
                "MERCHANT_PARAM",
                paramId,
                "UPDATE",
                "更新商户参数",
                "merchantId=" + merchantId + ",paramKey=" + existing.getParamKey() + ",newParamKey=" + paramKey,
                operatorUserName
        );
        return list(merchantId);
    }

    @Transactional
    public List<PaymentMerchantParamResponse> saveFromFile(Long merchantId,
                                                           PaymentMerchantParamFileUploadRequest request,
                                                           MultipartFile file,
                                                           String operatorUserName) {
        requireMerchant(merchantId);
        String paramKey = PaymentCatalogs.requireText(request.getParamKey(), "paramKey");
        PaymentMerchantParamEntity existing = paymentMerchantParamMapper.findEntityByMerchantIdAndKey(merchantId, paramKey);
        PaymentMerchantParamEntity entity = fileEntity(merchantId, paramKey, request, file);
        if (existing == null) {
            paymentMerchantParamMapper.insert(entity);
            paymentAuditService.record("MERCHANT_PARAM", entity.getId(), "CREATE", "新增商户文件参数",
                    "merchantId=" + merchantId + ",paramKey=" + paramKey, operatorUserName);
        } else {
            entity.setId(existing.getId());
            paymentMerchantParamMapper.update(entity);
            paymentAuditService.record("MERCHANT_PARAM", existing.getId(), "UPDATE", "更新商户文件参数",
                    "merchantId=" + merchantId + ",paramKey=" + paramKey, operatorUserName);
        }
        return list(merchantId);
    }

    @Transactional
    public List<PaymentMerchantParamResponse> updateFromFile(Long merchantId,
                                                             Long paramId,
                                                             PaymentMerchantParamFileUploadRequest request,
                                                             MultipartFile file,
                                                             String operatorUserName) {
        requireMerchant(merchantId);
        PaymentMerchantParamEntity existing = requireParam(merchantId, paramId);
        String paramKey = resolveUpdatedParamKey(merchantId, existing, request.getParamKey());
        PaymentMerchantParamEntity entity;
        if (file == null || file.isEmpty()) {
            if (!"FILE".equals(existing.getSourceType())) {
                throw new IllegalArgumentException("参数文件不能为空");
            }
            entity = keepExistingFileEntity(existing, paramKey, request);
        } else {
            entity = fileEntity(merchantId, paramKey, request, file);
        }
        entity.setId(paramId);
        paymentMerchantParamMapper.update(entity);
        paymentAuditService.record("MERCHANT_PARAM", paramId, "UPDATE", "更新商户文件参数",
                "merchantId=" + merchantId + ",paramKey=" + existing.getParamKey() + ",newParamKey=" + paramKey, operatorUserName);
        return list(merchantId);
    }

    public PaymentSecretDownloadResponse downloadFile(Long merchantId, Long paramId) {
        requireMerchant(merchantId);
        PaymentMerchantParamEntity entity = requireParam(merchantId, paramId);
        if (!"FILE".equals(entity.getSourceType())) {
            throw new IllegalArgumentException("该参数不是文件上传");
        }
        String rawValue = paymentCryptoService.decrypt(entity.getEncryptedValue());
        byte[] content = decodeFileContent(rawValue, entity.getFileValueType());
        String fileName = PaymentCatalogs.trimToNull(entity.getFileName());
        String contentType = PaymentCatalogs.trimToNull(entity.getFileContentType());
        return new PaymentSecretDownloadResponse(
                fileName == null ? entity.getParamKey() : fileName,
                contentType == null ? "application/octet-stream" : contentType,
                content
        );
    }

    @Transactional
    public List<PaymentMerchantParamResponse> delete(Long merchantId, Long paramId, String operatorUserName) {
        requireMerchant(merchantId);
        PaymentMerchantParamEntity existing = requireParam(merchantId, paramId);
        if (paymentMerchantParamMapper.deleteByIdAndMerchantId(paramId, merchantId) == 0) {
            throw new IllegalArgumentException("商户参数不存在");
        }
        paymentAuditService.record(
                "MERCHANT_PARAM",
                paramId,
                "DELETE",
                "删除商户参数",
                "merchantId=" + merchantId + ",paramKey=" + existing.getParamKey(),
                operatorUserName
        );
        return list(merchantId);
    }

    private PaymentMerchantParamEntity toEntity(Long merchantId,
                                                String paramKey,
                                                PaymentMerchantParamSaveRequest request) {
        return toEntity(merchantId, paramKey, request, null);
    }

    private PaymentMerchantParamEntity toEntity(Long merchantId,
                                                String paramKey,
                                                PaymentMerchantParamSaveRequest request,
                                                PaymentMerchantParamEntity existing) {
        String rawValue = PaymentCatalogs.trimToNull(request.getParamValue());
        if (rawValue == null && existing == null) {
            throw new IllegalArgumentException("paramValue 不能为空");
        }
        boolean sensitive = Boolean.TRUE.equals(request.getSensitive());
        PaymentMerchantParamEntity entity = new PaymentMerchantParamEntity();
        entity.setMerchantId(merchantId);
        entity.setParamKey(paramKey);
        String valueType = PaymentCatalogs.normalizeValueType(request.getValueType());
        entity.setValueType(valueType);
        if (rawValue == null) {
            if ("FILE".equals(existing.getSourceType()) && !"FILE".equals(valueType)) {
                throw new IllegalArgumentException("paramValue 不能为空");
            }
            entity.setSensitiveFlag(existing.getSensitiveFlag());
            entity.setPlainValue(existing.getPlainValue());
            entity.setEncryptedValue(existing.getEncryptedValue());
            entity.setMaskedValue(existing.getMaskedValue());
            entity.setSourceType(existing.getSourceType() == null ? "TEXT" : existing.getSourceType());
            entity.setFileName(existing.getFileName());
            entity.setFileContentType(existing.getFileContentType());
            entity.setFileValueType(existing.getFileValueType());
        } else {
            entity.setSensitiveFlag(sensitive);
            entity.setPlainValue(sensitive ? null : rawValue);
            entity.setEncryptedValue(sensitive ? paymentCryptoService.encrypt(rawValue) : null);
            entity.setMaskedValue(sensitive ? paymentCryptoService.mask(rawValue) : rawValue);
            entity.setSourceType("TEXT");
            entity.setFileName(null);
            entity.setFileContentType(null);
            entity.setFileValueType(null);
        }
        entity.setRemark(PaymentCatalogs.trimToNull(request.getRemark()));
        return entity;
    }

    private PaymentMerchantParamEntity requireParam(Long merchantId, Long paramId) {
        PaymentMerchantParamEntity existing = paymentMerchantParamMapper.findEntityById(paramId);
        if (existing == null || !merchantId.equals(existing.getMerchantId())) {
            throw new IllegalArgumentException("商户参数不存在");
        }
        return existing;
    }

    private String resolveUpdatedParamKey(Long merchantId,
                                          PaymentMerchantParamEntity existing,
                                          String requestedParamKey) {
        String paramKey = PaymentCatalogs.trimToNull(requestedParamKey);
        if (paramKey == null) {
            return existing.getParamKey();
        }
        PaymentMerchantParamEntity duplicate = paymentMerchantParamMapper.findEntityByMerchantIdAndKey(merchantId, paramKey);
        if (duplicate != null && !existing.getId().equals(duplicate.getId())) {
            throw new IllegalArgumentException("商户参数名已存在");
        }
        return paramKey;
    }

    private PaymentMerchantParamEntity fileEntity(Long merchantId,
                                                  String paramKey,
                                                  PaymentMerchantParamFileUploadRequest request,
                                                  MultipartFile file) {
        String fileValueType = "BINARY";
        String rawValue = readFileValue(file);
        PaymentMerchantParamEntity entity = new PaymentMerchantParamEntity();
        entity.setMerchantId(merchantId);
        entity.setParamKey(paramKey);
        entity.setValueType(PaymentCatalogs.normalizeValueType(request.getValueType() == null ? "FILE" : request.getValueType()));
        entity.setSourceType("FILE");
        entity.setFileName(PaymentCatalogs.trimToNull(file.getOriginalFilename()));
        entity.setFileContentType(PaymentCatalogs.trimToNull(file.getContentType()));
        entity.setFileValueType(fileValueType);
        entity.setSensitiveFlag(true);
        entity.setPlainValue(null);
        entity.setEncryptedValue(paymentCryptoService.encrypt(rawValue));
        entity.setMaskedValue(entity.getFileName() == null ? paymentCryptoService.mask(rawValue) : entity.getFileName());
        entity.setRemark(PaymentCatalogs.trimToNull(request.getRemark()));
        return entity;
    }

    private PaymentMerchantParamEntity keepExistingFileEntity(PaymentMerchantParamEntity existing,
                                                              String paramKey,
                                                              PaymentMerchantParamFileUploadRequest request) {
        PaymentMerchantParamEntity entity = new PaymentMerchantParamEntity();
        entity.setMerchantId(existing.getMerchantId());
        entity.setParamKey(paramKey);
        entity.setValueType(PaymentCatalogs.normalizeValueType(request.getValueType() == null ? existing.getValueType() : request.getValueType()));
        entity.setSourceType(existing.getSourceType());
        entity.setFileName(existing.getFileName());
        entity.setFileContentType(existing.getFileContentType());
        entity.setFileValueType(existing.getFileValueType());
        entity.setSensitiveFlag(existing.getSensitiveFlag());
        entity.setPlainValue(existing.getPlainValue());
        entity.setEncryptedValue(existing.getEncryptedValue());
        entity.setMaskedValue(existing.getMaskedValue());
        entity.setRemark(PaymentCatalogs.trimToNull(request.getRemark()));
        return entity;
    }

    private String readFileValue(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("参数文件不能为空");
        }
        try {
            byte[] bytes = file.getBytes();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException ex) {
            throw new IllegalArgumentException("参数文件读取失败", ex);
        }
    }

    private byte[] decodeFileContent(String rawValue, String fileValueType) {
        if (!"BINARY".equals(fileValueType)) {
            return rawValue.getBytes(StandardCharsets.UTF_8);
        }
        try {
            return Base64.getDecoder().decode(rawValue);
        } catch (IllegalArgumentException ex) {
            return rawValue.getBytes(StandardCharsets.UTF_8);
        }
    }

    private PaymentMerchantEntity requireMerchant(Long merchantId) {
        PaymentMerchantEntity merchantEntity = paymentMerchantMapper.findEntityById(merchantId);
        if (merchantEntity == null) {
            throw new IllegalArgumentException("支付商户不存在");
        }
        return merchantEntity;
    }
}
