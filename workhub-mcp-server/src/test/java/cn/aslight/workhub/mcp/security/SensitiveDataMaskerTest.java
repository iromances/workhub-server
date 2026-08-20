package cn.aslight.workhub.mcp.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveDataMaskerTest {

    private final SensitiveDataMasker masker = new SensitiveDataMasker();

    @Test
    void mask_shouldCoverCommonPersonalAndCredentialFields() {
        assertEquals("138****5678", masker.mask("phone", "mobile_no", "13812345678"));
        assertEquals("张**", masker.mask("customer", "customer_name", "张三"));
        assertEquals("110***********1234", masker.mask("certificate", "id_card_no", "110101199001011234"));
        assertEquals("************8888", masker.mask("bankCard", "bank_card_no", "6222021234568888"));
        assertEquals("a***@example.com", masker.mask("email", "email", "alice@example.com"));
        assertEquals("******", masker.mask("apiKey", "api_key", "sk-sensitive"));
        assertEquals("北京市***", masker.mask("homeAddress", "home_address", "北京市朝阳区某街道1号"));
    }

    @Test
    void mask_shouldUseSourceColumnNameWhenAliasHidesMeaning() {
        assertEquals("李**", masker.mask("value", "real_name", "李四"));
        assertEquals("139****0000", masker.mask("value", "contact_phone", "13900000000"));
    }

    @Test
    void mask_shouldFallbackOnlyForHighConfidenceValueFormats() {
        assertEquals("137****1234", masker.mask("value", "value", "13712341234"));
        assertEquals("b***@example.com", masker.mask("value", "value", "bob@example.com"));
        assertEquals("项目名称", masker.mask("project_name", "project_name", "项目名称"));
        assertEquals(123L, masker.mask("id", "id", 123L));
    }

    @Test
    void maskSql_shouldHideSensitivePredicatesAndHighConfidenceLiterals() {
        String masked = masker.maskSql("select * from customer where real_name in ('张三', '李四') and phone = '13812345678' and status = 'ACTIVE'");

        assertFalse(masked.contains("张三"));
        assertFalse(masked.contains("李四"));
        assertFalse(masked.contains("13812345678"));
        assertFalse(masked.contains("ACTIVE"));
        assertTrue(masked.contains("'***'"));
    }
}
