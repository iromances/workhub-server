package cn.aslight.workhub.model.ops;

public record SystemAlertScopeServiceEntity(Long id,
                                            Long scopeId,
                                            String subsystemName,
                                            String serviceName,
                                            Boolean enabled,
                                            Integer sortOrder) {
}
