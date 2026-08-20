-- 将 asset 的 AI 配置数据模型、字典和默认继承语义平移到 WorkHub。
-- V20 只允许从已由 Flyway 校验通过的 V19 基线升级，避免依赖 CREATE ROUTINE 权限。
ALTER TABLE ai_provider_config
  ADD COLUMN default_model varchar(128) NULL COMMENT '通道默认模型' AFTER model_provider,
  ADD COLUMN default_reasoning_level varchar(32) NULL COMMENT '通道默认推理强度' AFTER default_model,
  ADD COLUMN default_speed_mode varchar(32) NULL COMMENT '通道默认速度模式' AFTER default_reasoning_level,
  ADD COLUMN create_time datetime NULL AFTER remark,
  ADD COLUMN create_by varchar(64) NULL AFTER create_time,
  ADD COLUMN modify_time datetime NULL AFTER create_by,
  ADD COLUMN modify_by varchar(64) NULL AFTER modify_time,
  ADD COLUMN delete_time datetime NULL AFTER modify_by,
  ADD COLUMN delete_by varchar(64) NULL AFTER delete_time,
  ADD COLUMN is_delete tinyint(1) NOT NULL DEFAULT 0 AFTER delete_by,
  ADD COLUMN version int NOT NULL DEFAULT 0 AFTER is_delete;

ALTER TABLE ai_use_case_config
  ADD COLUMN create_time datetime NULL AFTER remark,
  ADD COLUMN create_by varchar(64) NULL AFTER create_time,
  ADD COLUMN modify_time datetime NULL AFTER create_by,
  ADD COLUMN modify_by varchar(64) NULL AFTER modify_time,
  ADD COLUMN delete_time datetime NULL AFTER modify_by,
  ADD COLUMN delete_by varchar(64) NULL AFTER delete_time,
  ADD COLUMN is_delete tinyint(1) NOT NULL DEFAULT 0 AFTER delete_by,
  ADD COLUMN version int NOT NULL DEFAULT 0 AFTER is_delete;

UPDATE ai_provider_config SET create_time=COALESCE(create_time,created_at), modify_time=COALESCE(modify_time,updated_at);
UPDATE ai_use_case_config SET create_time=COALESCE(create_time,created_at), modify_time=COALESCE(modify_time,updated_at);

CREATE TABLE IF NOT EXISTS ai_use_case_config_bak_20260715_000000 LIKE ai_use_case_config;
INSERT IGNORE INTO ai_use_case_config_bak_20260715_000000 SELECT * FROM ai_use_case_config;

UPDATE ai_provider_config p
LEFT JOIN (
  SELECT provider_config_id, model FROM (
    SELECT provider_config_id, model, ROW_NUMBER() OVER(PARTITION BY provider_config_id ORDER BY COUNT(*) DESC, MAX(updated_at) DESC, model ASC) rn
    FROM ai_use_case_config WHERE is_delete=0 AND model IS NOT NULL AND model<>'' GROUP BY provider_config_id,model
  ) ranked WHERE rn=1
) m ON m.provider_config_id=p.id
LEFT JOIN (
  SELECT provider_config_id, reasoning_level FROM (
    SELECT provider_config_id, reasoning_level, ROW_NUMBER() OVER(PARTITION BY provider_config_id ORDER BY COUNT(*) DESC, MAX(updated_at) DESC, reasoning_level ASC) rn
    FROM ai_use_case_config WHERE is_delete=0 AND reasoning_level IS NOT NULL AND reasoning_level<>'' GROUP BY provider_config_id,reasoning_level
  ) ranked WHERE rn=1
) r ON r.provider_config_id=p.id
LEFT JOIN (
  SELECT provider_config_id, speed_mode FROM (
    SELECT provider_config_id, speed_mode, ROW_NUMBER() OVER(PARTITION BY provider_config_id ORDER BY COUNT(*) DESC, MAX(updated_at) DESC, speed_mode ASC) rn
    FROM ai_use_case_config WHERE is_delete=0 AND speed_mode IS NOT NULL AND speed_mode<>'' GROUP BY provider_config_id,speed_mode
  ) ranked WHERE rn=1
) s ON s.provider_config_id=p.id
LEFT JOIN (
  SELECT provider_config_id, timeout_seconds FROM (
    SELECT provider_config_id, timeout_seconds, ROW_NUMBER() OVER(PARTITION BY provider_config_id ORDER BY COUNT(*) DESC, MAX(updated_at) DESC, timeout_seconds ASC) rn
    FROM ai_use_case_config WHERE is_delete=0 AND timeout_seconds>0 GROUP BY provider_config_id,timeout_seconds
  ) ranked WHERE rn=1
) t ON t.provider_config_id=p.id
SET p.default_model=COALESCE(NULLIF(p.default_model,''),m.model),
    p.default_reasoning_level=COALESCE(NULLIF(p.default_reasoning_level,''),r.reasoning_level),
    p.default_speed_mode=COALESCE(NULLIF(p.default_speed_mode,''),s.speed_mode),
    p.call_timeout_seconds=COALESCE(p.call_timeout_seconds,t.timeout_seconds), p.modify_time=CURRENT_TIMESTAMP,
    p.modify_by=COALESCE(p.modify_by,'flyway-v20');

UPDATE ai_use_case_config SET model=NULL, reasoning_level=NULL, speed_mode=NULL, timeout_seconds=NULL,
  modify_time=CURRENT_TIMESTAMP, modify_by=COALESCE(modify_by,'flyway-v20'), version=version+1 WHERE is_delete=0;

CREATE TABLE IF NOT EXISTS dict (
  id bigint NOT NULL AUTO_INCREMENT, dict_type varchar(64) NOT NULL, dict_desc varchar(128) NULL,
  enum_code varchar(128) NOT NULL, enum_desc varchar(256) NOT NULL, parent_code varchar(128) NULL,
  root_code varchar(128) NULL, sort_order int NOT NULL DEFAULT 0, create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_by varchar(64) NULL, modify_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  modify_by varchar(64) NULL, delete_time datetime NULL, delete_by varchar(64) NULL,
  is_delete tinyint(1) NOT NULL DEFAULT 0, version int NOT NULL DEFAULT 0, remark varchar(512) NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_dict_type_code(dict_type,enum_code), KEY idx_dict_type(dict_type,is_delete,sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典';

INSERT INTO dict(dict_type,dict_desc,enum_code,enum_desc,sort_order) VALUES
('aiReasoningLevel','AI推理强度','LOW','低',10),('aiReasoningLevel','AI推理强度','MEDIUM','中',20),
('aiReasoningLevel','AI推理强度','HIGH','高',30),('aiReasoningLevel','AI推理强度','NONE','无推理',40),
('aiReasoningLevel','AI推理强度','XHIGH','极高',50),('aiReasoningLevel','AI推理强度','MAX','最大',60),
('aiSpeedMode','AI速度模式','FAST','快速',10),('aiSpeedMode','AI速度模式','STANDARD','标准',20),
('aiVendor','AI供应商','OPENROUTER','OpenRouter',10),('aiVendor','AI供应商','XIANXINGTONG','先行通',20),
('aiModelProvider','模型厂商','OPENAI','OpenAI',10),('aiModelProvider','模型厂商','ANTHROPIC','Anthropic',20),
('aiModelProvider','模型厂商','ZHIPU','智谱',30),('aiModelProvider','模型厂商','DEEPSEEK','DeepSeek',40),('aiModelProvider','模型厂商','QWEN','通义千问',50),
('aiChannelType','AI通道类型','API','API通道',10),('aiChannelType','AI通道类型','CLI','CLI通道',20),
('aiApiProtocol','AI API协议','CUSTOM','自定义',10),('aiApiProtocol','AI API协议','OPENAI_COMPATIBLE','OpenAI兼容',20),
('aiApiProtocol','AI API协议','OPENAI_RESPONSES','OpenAI Responses',30),('aiApiProtocol','AI API协议','OPENROUTER','OpenRouter',40),
('aiUseCaseDomain','AI任务领域','MARKET','行情',10),('aiUseCaseDomain','AI任务领域','NEWS','资讯',20),
('aiUseCaseDomain','AI任务领域','PORTFOLIO','组合',30),('aiUseCaseDomain','AI任务领域','STRATEGY','策略',40),
('aiUseCaseDomain','AI任务领域','SYSTEM','系统',50),('aiUseCaseDomain','AI任务领域','TRADE','交易',60),
('aiUseCaseDomain','AI任务领域','INTAKE','需求整理',70),('aiUseCaseDomain','AI任务领域','MCP','MCP',80),('aiUseCaseDomain','AI任务领域','OPS','运维',90),
('aiModel','AI模型','openai/gpt-5','openai/gpt-5',10),('aiModel','AI模型','openai/gpt-5-mini','openai/gpt-5-mini',20),
('aiModel','AI模型','openai/gpt-5-nano','openai/gpt-5-nano',30),('aiModel','AI模型','anthropic/claude-sonnet-4','anthropic/claude-sonnet-4',40),
('aiModel','AI模型','anthropic/claude-opus-4','anthropic/claude-opus-4',50),('aiModel','AI模型','deepseek/deepseek-chat','deepseek/deepseek-chat',60),
('aiModel','AI模型','deepseek/deepseek-reasoner','deepseek/deepseek-reasoner',70),('aiModel','AI模型','qwen/qwen-plus','qwen/qwen-plus',80),
('aiModel','AI模型','zhipu/glm-4.5','zhipu/glm-4.5',90),('aiModel','AI模型','gpt-5.6-sol','gpt-5.6-sol',100),
('aiModel','AI模型','gpt-5.6-terra','gpt-5.6-terra',110),('aiModel','AI模型','gpt-5.6-luna','gpt-5.6-luna',120)
ON DUPLICATE KEY UPDATE dict_desc=VALUES(dict_desc),enum_desc=VALUES(enum_desc),sort_order=VALUES(sort_order),is_delete=0;

INSERT INTO ai_provider_config(provider_code,provider_name,channel_type,vendor,model_provider,default_model,
 default_reasoning_level,default_speed_mode,api_protocol,api_base_url,api_key,connect_timeout_seconds,
 read_timeout_seconds,call_timeout_seconds,site_url,enabled,remark,create_time,modify_time,is_delete,version)
SELECT 'xianxingtong-openai-api','先行通-OpenAI-API API通道','API','XIANXINGTONG','OPENAI',
 NULL,NULL,NULL,'OPENAI_COMPATIBLE','https://aiserver.thchengtay.com/v1',NULL,60,300,600,
 'https://aiserver.thchengtay.com/keys',0,'从 asset 平移的先行通模板；填写密钥和默认调用参数后启用',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,0
WHERE NOT EXISTS(SELECT 1 FROM ai_provider_config WHERE provider_code='xianxingtong-openai-api');
