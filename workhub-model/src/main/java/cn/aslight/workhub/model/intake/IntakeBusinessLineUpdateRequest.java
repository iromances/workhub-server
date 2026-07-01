package cn.aslight.workhub.model.intake;

/**
 * 需求业务线更新请求。
 */
public class IntakeBusinessLineUpdateRequest {

    /**
     * 业务线名称。
     */
    private String businessLine;
    /**
     * 业务线稳定编码。
     */
    private String businessLineCode;

    public String getBusinessLine() {
        return businessLine;
    }

    public void setBusinessLine(String businessLine) {
        this.businessLine = businessLine;
    }

    public String getBusinessLineCode() {
        return businessLineCode;
    }

    public void setBusinessLineCode(String businessLineCode) {
        this.businessLineCode = businessLineCode;
    }
}
