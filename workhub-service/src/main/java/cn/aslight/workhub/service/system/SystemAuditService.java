package cn.aslight.workhub.service.system;

import cn.aslight.workhub.dao.system.SysAuditMapper;
import cn.aslight.workhub.model.system.SysLoginLogResponse;
import cn.aslight.workhub.model.system.SysOperationLogResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统审计服务。
 */
@Service
public class SystemAuditService {

    private final SysAuditMapper auditMapper;

    public SystemAuditService(SysAuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    public void login(String userName, String result, String failReason, String ip, String userAgent) {
        auditMapper.insertLoginLog(trim(userName), result, trim(failReason), trim(ip), trim(userAgent));
    }

    public void operation(String operator,
                          String permissionCode,
                          String actionType,
                          String targetType,
                          String targetId,
                          String beforeSnapshot,
                          String afterSnapshot,
                          String result,
                          String errorMessage,
                          String ip) {
        auditMapper.insertOperationLog(
                trim(operator),
                trim(permissionCode),
                actionType,
                targetType,
                trim(targetId),
                mask(trim(beforeSnapshot)),
                mask(trim(afterSnapshot)),
                result,
                mask(trim(errorMessage)),
                trim(ip)
        );
    }

    public List<SysLoginLogResponse> loginLogs(String userName, String loginResult, int limit) {
        return auditMapper.findLoginLogs(trim(userName), trim(loginResult), normalizeLimit(limit));
    }

    public List<SysOperationLogResponse> operationLogs(String operatorUserName, String permissionCode, String result, int limit) {
        return auditMapper.findOperationLogs(trim(operatorUserName), trim(permissionCode), trim(result), normalizeLimit(limit));
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return 100;
        }
        return Math.min(limit, 500);
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String mask(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replaceAll("(?i)(password|token|secret|key|privateKey)(\"?\\s*[:=]\\s*\")?[^,}]*", "$1$2***")
                .replaceAll("(?i)(密码|秘钥|密钥|令牌)(\"?\\s*[:=]\\s*\")?[^,}]*", "$1$2***");
    }
}
