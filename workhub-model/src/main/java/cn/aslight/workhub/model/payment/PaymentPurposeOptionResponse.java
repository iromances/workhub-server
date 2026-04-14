package cn.aslight.workhub.model.payment;

/**
 * 支付用途选项。
 */
public record PaymentPurposeOptionResponse(String code,
                                           String name,
                                           String description) {
}
