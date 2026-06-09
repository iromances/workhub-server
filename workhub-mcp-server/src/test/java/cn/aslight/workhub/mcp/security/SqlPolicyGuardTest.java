package cn.aslight.workhub.mcp.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlPolicyGuardTest {

    @Test
    void validateReadonly_shouldKeepSelectWithExistingLimit() {
        SqlPolicyGuard guard = new SqlPolicyGuard();

        String sql = guard.validateReadonly("select id, name from pm_project limit 20", 100);

        assertEquals("select id, name from pm_project limit 20", sql);
    }

    @Test
    void validateReadonly_shouldAppendLimitWhenMissing() {
        SqlPolicyGuard guard = new SqlPolicyGuard();

        String sql = guard.validateReadonly("select id, name from pm_project", 100);

        assertEquals("select id, name from pm_project LIMIT 100", sql);
    }

    @Test
    void validateReadonly_shouldRejectDml() {
        SqlPolicyGuard guard = new SqlPolicyGuard();

        assertThrows(IllegalArgumentException.class,
                () -> guard.validateReadonly("update pm_project set project_name = 'x'", 100));
    }

    @Test
    void validateReadonly_shouldRejectMultipleStatements() {
        SqlPolicyGuard guard = new SqlPolicyGuard();

        assertThrows(IllegalArgumentException.class,
                () -> guard.validateReadonly("select * from pm_project; select * from sys_user", 100));
    }

    @Test
    void validateReadonly_shouldRejectComments() {
        SqlPolicyGuard guard = new SqlPolicyGuard();

        assertThrows(IllegalArgumentException.class,
                () -> guard.validateReadonly("select * from pm_project -- hide", 100));
    }

    @Test
    void validateReadonly_shouldRejectOutfile() {
        SqlPolicyGuard guard = new SqlPolicyGuard();

        assertThrows(IllegalArgumentException.class,
                () -> guard.validateReadonly("select * from pm_project into outfile '/tmp/a.csv'", 100));
    }
}
