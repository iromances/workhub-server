package cn.aslight.workhub.model.auth;

/**
 * Login 响应模型。
 */
public record LoginResponse(String token, String userName) {
}
