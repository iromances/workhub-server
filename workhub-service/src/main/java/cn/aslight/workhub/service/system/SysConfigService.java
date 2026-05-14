package cn.aslight.workhub.service.system;

import cn.aslight.workhub.dao.system.SysConfigMapper;
import cn.aslight.workhub.model.system.SysConfigItemEntity;
import cn.aslight.workhub.model.system.SysConfigResponse;
import cn.aslight.workhub.model.system.SysConfigSaveRequest;
import cn.aslight.workhub.service.payment.PaymentCryptoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统配置服务。
 */
@Service
public class SysConfigService {

    public static final String VALUE_TYPE_TEXT = "TEXT";
    public static final String VALUE_TYPE_SECRET = "SECRET";

    private final SysConfigMapper sysConfigMapper;
    private final PaymentCryptoService cryptoService;

    public SysConfigService(SysConfigMapper sysConfigMapper, PaymentCryptoService cryptoService) {
        this.sysConfigMapper = sysConfigMapper;
        this.cryptoService = cryptoService;
    }

    public List<SysConfigResponse> list(String configGroup, String keyword) {
        return sysConfigMapper.findAll(trimToNull(configGroup), trimToNull(keyword)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SysConfigResponse create(SysConfigSaveRequest request) {
        SysConfigItemEntity entity = toEntity(new SysConfigItemEntity(), request);
        sysConfigMapper.insert(entity);
        return toResponse(requireExisting(entity.getId()));
    }

    @Transactional
    public SysConfigResponse update(Long id, SysConfigSaveRequest request) {
        SysConfigItemEntity existing = requireExisting(id);
        SysConfigItemEntity entity = toEntity(existing, request);
        entity.setId(id);
        sysConfigMapper.update(entity);
        return toResponse(requireExisting(id));
    }

    public String requirePlainValue(String configGroup, String configKey) {
        SysConfigItemEntity entity = sysConfigMapper.findEnabledByKey(configGroup, configKey);
        if (entity == null) {
            throw new IllegalArgumentException("系统配置不存在或未启用：" + configGroup + "." + configKey);
        }
        String value = resolvePlainValue(entity);
        if (value == null) {
            throw new IllegalArgumentException("系统配置值为空：" + configGroup + "." + configKey);
        }
        return value;
    }

    public String findPlainValue(String configGroup, String configKey) {
        SysConfigItemEntity entity = sysConfigMapper.findEnabledByKey(configGroup, configKey);
        return entity == null ? null : resolvePlainValue(entity);
    }

    private SysConfigItemEntity requireExisting(Long id) {
        SysConfigItemEntity entity = sysConfigMapper.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("系统配置不存在");
        }
        return entity;
    }

    private SysConfigItemEntity toEntity(SysConfigItemEntity entity, SysConfigSaveRequest request) {
        String valueType = normalizeValueType(request.getValueType());
        String value = trimToNull(request.getValue());
        entity.setConfigGroup(requireValue(request.getConfigGroup(), "配置分组不能为空"));
        entity.setConfigKey(requireValue(request.getConfigKey(), "配置键不能为空"));
        entity.setConfigName(requireValue(request.getConfigName(), "配置名称不能为空"));
        entity.setValueType(valueType);
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setRemark(trimToNull(request.getRemark()));
        if (VALUE_TYPE_SECRET.equals(valueType)) {
            if (value != null) {
                entity.setEncryptedValue(cryptoService.encrypt(value));
                entity.setMaskedValue(cryptoService.mask(value));
            }
            entity.setPlainValue(null);
        } else {
            entity.setPlainValue(value);
            entity.setEncryptedValue(null);
            entity.setMaskedValue(null);
        }
        return entity;
    }

    private SysConfigResponse toResponse(SysConfigItemEntity entity) {
        return new SysConfigResponse(
                entity.getId(),
                entity.getConfigGroup(),
                entity.getConfigKey(),
                entity.getConfigName(),
                entity.getValueType(),
                VALUE_TYPE_SECRET.equals(entity.getValueType()) ? entity.getMaskedValue() : entity.getPlainValue(),
                entity.getEnabled(),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String resolvePlainValue(SysConfigItemEntity entity) {
        if (VALUE_TYPE_SECRET.equals(entity.getValueType())) {
            if (entity.getEncryptedValue() == null || entity.getEncryptedValue().isBlank()) {
                return null;
            }
            return cryptoService.decrypt(entity.getEncryptedValue());
        }
        return trimToNull(entity.getPlainValue());
    }

    private String normalizeValueType(String valueType) {
        String normalized = requireValue(valueType, "配置值类型不能为空").toUpperCase();
        if (!VALUE_TYPE_TEXT.equals(normalized) && !VALUE_TYPE_SECRET.equals(normalized)) {
            throw new IllegalArgumentException("配置值类型只支持 TEXT 或 SECRET");
        }
        return normalized;
    }

    private String requireValue(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
