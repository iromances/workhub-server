package cn.aslight.workhub.model.intake;

/**
 * 需求文件夹打开结果。
 */
public record IntakeRequirementFolderResponse(Long intakeId,
                                              String basePath,
                                              String folderName,
                                              String folderPath,
                                              Boolean opened) {
}
