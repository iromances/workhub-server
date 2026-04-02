package cn.aslight.workhub.model.auth;

/**
 * LocalUser 模型。
 */
public record LocalUser(String userName, String displayName, String encodedPassword) {
}
