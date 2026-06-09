package cn.aslight.workhub.model.project;

/**
 * 项目实体。
 */
public class ProjectEntity {

    /**
     * 项目主键 ID。
     */
    private Long id;
    /**
     * 系统编码。
     */
    private String projectCode;
    /**
     * 系统名称。
     */
    private String projectName;
    /**
     * 项目类型。
     */
    private String projectType;
    /**
     * 业务线。
     */
    private String businessLine;
    /**
     * 项目状态。
     */
    private String projectStatus;
    /**
     * 项目负责人用户名。
     */
    private String ownerUserName;
    /**
     * 项目描述。
     */
    private String description;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public String getBusinessLine() {
        return businessLine;
    }

    public void setBusinessLine(String businessLine) {
        this.businessLine = businessLine;
    }

    public String getProjectStatus() {
        return projectStatus;
    }

    public void setProjectStatus(String projectStatus) {
        this.projectStatus = projectStatus;
    }

    public String getOwnerUserName() {
        return ownerUserName;
    }

    public void setOwnerUserName(String ownerUserName) {
        this.ownerUserName = ownerUserName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
