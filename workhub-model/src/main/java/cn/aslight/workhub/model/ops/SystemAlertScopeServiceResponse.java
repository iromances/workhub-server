package cn.aslight.workhub.model.ops;

public record SystemAlertScopeServiceResponse(Long id,
                                              String subsystemName,
                                              String serviceName,
                                              Boolean enabled) {
}
