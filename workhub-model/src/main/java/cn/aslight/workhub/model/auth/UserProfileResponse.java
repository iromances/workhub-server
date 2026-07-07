package cn.aslight.workhub.model.auth;

import java.util.List;

/**
 * UserProfile 响应模型。
 */
public record UserProfileResponse(String userName,
                                  String displayName,
                                  Boolean mustChangePassword,
                                  List<String> roles,
                                  List<String> permissions) {
}
