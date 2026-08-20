package cn.aslight.workhub.model.mcp;

import java.time.LocalDateTime;
import java.util.List;

public record McpResourceResponse(Long id,
                                  String resourceType,
                                  String targetKey,
                                  Boolean publicResource,
                                  List<String> featureTags,
                                  String businessLineCode,
                                  List<String> businessLineCodes,
                                  String environmentCode,
                                  String name,
                                  String systemName,
                                  List<String> systemNames,
                                  String host,
                                  Integer port,
                                  String databaseSchema,
                                  String username,
                                  Boolean passwordConfigured,
                                  Boolean sshPasswordConfigured,
                                  Boolean sshBastionEnabled,
                                  Long bastionId,
                                  String bastionName,
                                  String sshBastionHost,
                                  Integer sshBastionPort,
                                  String sshBastionUser,
                                  Boolean sshBastionPasswordConfigured,
                                  String sshIdentityFile,
                                  List<String> allowedServices,
                                  List<String> allowedLogPaths,
                                  List<McpResourceProfile> profiles,
                                  Boolean enabled,
                                  String remark,
                                  LocalDateTime createdAt,
                                  LocalDateTime updatedAt) {
}
