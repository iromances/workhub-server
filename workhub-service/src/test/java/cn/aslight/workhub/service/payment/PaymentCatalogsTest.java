package cn.aslight.workhub.service.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PaymentCatalogsTest {

    @Test
    void normalizePurpose_shouldMergeWithholdSubMerchantPurposesIntoWithhold() {
        assertEquals("WITHHOLD", PaymentCatalogs.normalizePurpose("WITHHOLD_SUB_MERCHANT"));
        assertEquals("WITHHOLD", PaymentCatalogs.normalizePurpose("WITHHOLD_SUB_MERCHANT_BEIJING"));
        assertEquals("WITHHOLD", PaymentCatalogs.normalizePurpose("WITHHOLD_SUB_MERCHANT_TIANJIN"));
        assertEquals("WITHHOLD", PaymentCatalogs.normalizePurpose("WITHHOLD_SUB_MERCHANT_PROD_TEST"));
    }

    @Test
    void normalizePurpose_shouldMergePayOutIntoTransfer() {
        assertEquals("TRANSFER", PaymentCatalogs.normalizePurpose("PAY_OUT"));
    }

    @Test
    void normalizePurpose_shouldMergeSplitPurposesIntoWithholdSplitSettlement() {
        assertEquals("WITHHOLD_SPLIT_SETTLEMENT", PaymentCatalogs.normalizePurpose("SPLIT_SETTLEMENT"));
        assertEquals("WITHHOLD_SPLIT_SETTLEMENT", PaymentCatalogs.normalizePurpose("SPLIT_RECEIVER_XXT"));
        assertEquals("WITHHOLD_SPLIT_SETTLEMENT", PaymentCatalogs.normalizePurpose("SPLIT_RECEIVER_LIYI"));
    }

    @Test
    void normalizePurpose_shouldMergeRefundMainAccountIntoRefund() {
        assertEquals("REFUND", PaymentCatalogs.normalizePurpose("REFUND_MAIN_ACCOUNT"));
    }

    @Test
    void normalizePurpose_shouldMergeSignAgreementIntoBindCard() {
        assertEquals("BIND_CARD", PaymentCatalogs.normalizePurpose("SIGN_AGREEMENT"));
    }

    @Test
    void listPurposes_shouldNotExposeLegacyWithholdSubMerchantPurposes() {
        assertFalse(PaymentCatalogs.listPurposes().stream()
                .anyMatch(purpose -> purpose.code().startsWith("WITHHOLD_SUB_MERCHANT")));
    }

    @Test
    void listPurposes_shouldNotExposeMergedOrRemovedPurposes() {
        assertEquals("银行卡签约", PaymentCatalogs.listPurposes().stream()
                .filter(purpose -> "BIND_CARD".equals(purpose.code()))
                .findFirst()
                .orElseThrow()
                .name());
        assertFalse(PaymentCatalogs.listPurposes().stream()
                .anyMatch(purpose -> java.util.Set.of(
                        "SIGN_AGREEMENT",
                        "PAY_OUT",
                        "SPLIT_SETTLEMENT",
                        "SPLIT_RECEIVER_XXT",
                        "SPLIT_RECEIVER_LIYI",
                        "QUERY_ORDER",
                        "VERIFY_ACCOUNT",
                        "UNBIND_CARD",
                        "RECHARGE",
                        "DOWNLOAD_RECON",
                        "CALLBACK_VERIFY",
                        "BALANCE_QUERY",
                        "REFUND_MAIN_ACCOUNT"
                ).contains(purpose.code())));
    }
}
