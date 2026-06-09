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
    void listPurposes_shouldNotExposeLegacyWithholdSubMerchantPurposes() {
        assertFalse(PaymentCatalogs.listPurposes().stream()
                .anyMatch(purpose -> purpose.code().startsWith("WITHHOLD_SUB_MERCHANT")));
    }
}
