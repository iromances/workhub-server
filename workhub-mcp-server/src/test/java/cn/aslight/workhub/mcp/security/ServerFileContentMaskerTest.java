package cn.aslight.workhub.mcp.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerFileContentMaskerTest {
    private final ServerFileContentMasker masker = new ServerFileContentMasker();

    @Test
    void handlesMaximumSingleLineWithoutUnboundedPatternBacktracking() {
        String source = "a".repeat(262144);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), () -> assertEquals(source, masker.mask(source)));
        String secret = "{\"password\":\"" + "s".repeat(200000) + "\",\"profile\":\"sit\"}";
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), () -> {
            String masked = masker.mask(secret);
            assertTrue(masked.length() < 100);
            assertTrue(masked.contains("sit"));
        });
    }

    @Test
    void masksShellYamlJsonPropertiesXmlAndAuthenticationWhileRetainingProfile() {
        String source = """
                export DB_PASSWORD='shell-value with spaces'
                xxl.job.accessToken=property-value
                {"password":"json-value", "profile":"sit"}
                java -Ddb.password=argument-value -Dspring.profiles.active=sit -jar app.jar
                curl --password cli-value -H 'Authorization: Bearer bearer-value'
                jdbc:mysql://user:uri-value@localhost/db
                <password>xml-value
                xml-next-line</password>
                credentials:
                  username: block-user
                  password: block-password
                spring.profiles.active=sit
                secret: |
                  block-secret-line
                profile: sit
                -----BEGIN PRIVATE KEY-----
                key-content
                -----END PRIVATE KEY-----
                """;
        String masked = masker.mask(source);
        for (String value : new String[]{"shell-value", "property-value", "json-value", "argument-value", "cli-value",
                "bearer-value", "uri-value", "xml-value", "xml-next-line", "block-user", "block-password", "block-secret-line", "key-content"}) {
            assertFalse(masked.contains(value), value);
        }
        assertTrue(masked.contains("-Dspring.profiles.active=sit"));
        assertTrue(masked.contains("spring.profiles.active=sit"));
        assertTrue(masked.contains("profile: sit"));
    }

    @Test
    void masksTruncatedKeysAndShellContinuationWithoutDroppingSafeText() {
        assertFalse(masker.mask("-----BEGIN PRIVATE KEY-----\nkey-without-end").contains("key-without-end"));
        assertFalse(masker.mask("PASSWORD=first\\\nsecond-part\nprofile=sit").contains("second-part"));
        assertFalse(masker.mask("{\"token\":\"escaped\\\"secret\"}").contains("secret"));
        assertEquals("java -jar app.jar --spring.profiles.active=sit\n", masker.mask("java -jar app.jar --spring.profiles.active=sit\n"));
    }

    @Test
    void masksMultilineQuotedShellAndInlineJsonSecrets() {
        for (String value : new String[]{"PASSWORD='first-part\nsecond-part'\nprofile=sit",
                "{\"token\":\"first-part\nsecond-part\"}\nprofile=sit"}) {
            String masked = masker.mask(value);
            assertFalse(masked.contains("first-part"));
            assertFalse(masked.contains("second-part"));
            assertTrue(masked.contains("profile=sit"));
        }
    }
}
