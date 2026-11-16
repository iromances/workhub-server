package cn.aslight.workhub.model.intake;

import jakarta.validation.constraints.Size;

/**
 * 需求审核员更新请求。
 */
public class IntakeReviewerUpdateRequest {

    @Size(max = 64, message = "审核员账号长度不能超过 64")
    private String reviewUserName;

    public String getReviewUserName() {
        return reviewUserName;
    }

    public void setReviewUserName(String reviewUserName) {
        this.reviewUserName = reviewUserName;
    }
}
