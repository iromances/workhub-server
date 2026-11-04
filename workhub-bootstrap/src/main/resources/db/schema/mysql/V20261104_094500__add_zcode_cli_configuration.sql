ALTER TABLE ai_provider_config
    ADD COLUMN cli_type VARCHAR(32) NULL COMMENT 'CLI 协议 CODEX/ZCODE；历史空值按命令识别',
    ADD COLUMN cli_runtime_command VARCHAR(1000) NULL COMMENT 'ZCode CJS 分发的已安装 Node 程序',
    ADD COLUMN cli_builtin_config_path VARCHAR(1000) NULL COMMENT 'ZCode 已安装内置 Provider 定义',
    ADD COLUMN cli_personal_config_path VARCHAR(1000) NULL COMMENT 'ZCode 既有个人 Provider 配置路径';
