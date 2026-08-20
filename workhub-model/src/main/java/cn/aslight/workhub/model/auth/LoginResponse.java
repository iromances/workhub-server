package cn.aslight.workhub.model.auth;

import java.util.List;

/**
 * Login 响应模型。
 */
public record LoginResponse(String token,
                            String userName,
                            String displayName,
                            String avatarUrl,
                            Boolean mustChangePassword,
                            List<String> roles,
                            List<String> permissions) {
}
