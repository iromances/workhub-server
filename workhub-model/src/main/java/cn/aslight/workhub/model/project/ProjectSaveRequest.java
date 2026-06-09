package cn.aslight.workhub.model.project;

import jakarta.validation.constraints.NotBlank;

/**
 * ProjectSave 请求模型。
 */
public class ProjectSaveRequest {

    @NotBlank(message = "不能为空")
    private String code;

    @NotBlank(message = "不能为空")
    private String name;

    @NotBlank(message = "不能为空")
    private String type;

    @NotBlank(message = "不能为空")
    private String businessLine;

    @NotBlank(message = "不能为空")
    private String ownerUserName;

    @NotBlank(message = "不能为空")
    private String status;

    private String description;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getBusinessLine() {
        return businessLine;
    }

    public void setBusinessLine(String businessLine) {
        this.businessLine = businessLine;
    }

    public String getOwnerUserName() {
        return ownerUserName;
    }

    public void setOwnerUserName(String ownerUserName) {
        this.ownerUserName = ownerUserName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
