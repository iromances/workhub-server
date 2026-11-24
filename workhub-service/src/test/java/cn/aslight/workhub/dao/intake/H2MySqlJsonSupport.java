package cn.aslight.workhub.dao.intake;

/**
 * H2 内存库（MySQL 模式）下替代 MySQL JSON 函数的静态实现，供 CREATE ALIAS 注册。
 * 仅服务于 mapper 测试：NULL 入参走 CASE 短路，不会真正解析 JSON。
 */
public final class H2MySqlJsonSupport {

    private H2MySqlJsonSupport() {
    }

    public static boolean jsonValid(String value) {
        return value != null && !value.isBlank() && value.trim().startsWith("{");
    }

    public static String jsonRemove(String value, String path) {
        return value;
    }

    public static String jsonSet(String value, String key, Object keyValue, String key2, Object keyValue2) {
        return value;
    }
}
