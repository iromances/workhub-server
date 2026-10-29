package cn.aslight.workhub.model.intake;

/**
 * 需求类型更新请求。
 */
public class IntakeRequirementTypeUpdateRequest {

    /**
     * 需求类型，仅支持研发需求、数据提取/运维。
     */
    private String requirementType;

    public String getRequirementType() {
        return requirementType;
    }

    public void setRequirementType(String requirementType) {
        this.requirementType = requirementType;
    }
}
