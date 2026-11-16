-- 业务线访问地址登录凭据（REQ-9d5d6044）
-- 访问地址配置增加登录账号与密码密文两列：账号明文存储，密码沿用 mcp_resource_config.password_encrypted 模式加密存储。

ALTER TABLE `pm_business_line_access_config`
    ADD COLUMN `login_username` VARCHAR(128) NULL AFTER `path_prefix`,
    ADD COLUMN `login_password_encrypted` TEXT NULL AFTER `login_username`;
