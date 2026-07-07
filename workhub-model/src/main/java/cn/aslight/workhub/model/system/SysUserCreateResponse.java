package cn.aslight.workhub.model.system;

/**
 * 系统用户创建响应。
 */
public record SysUserCreateResponse(SysUserResponse user, String initialPassword) {
}
