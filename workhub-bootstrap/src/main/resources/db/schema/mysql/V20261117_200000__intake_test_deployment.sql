-- 提测发布仅绑定已核实的 TEST Job，不触碰生产配置。
CREATE TABLE pm_intake_test_deployment (
 id VARCHAR(36) PRIMARY KEY, intake_id BIGINT NOT NULL,
 active_key VARCHAR(200) NULL, plan_json LONGTEXT NOT NULL,
 status VARCHAR(32) NOT NULL, queue_id BIGINT NULL, build_number BIGINT NULL,
 build_url VARCHAR(1000) NULL, actual_sha VARCHAR(64) NULL, message VARCHAR(2000) NULL,
 requested_by VARCHAR(64) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_test_deployment_active(active_key),
 KEY idx_test_deployment_intake(intake_id,created_at), KEY idx_test_deployment_status(status)
);

INSERT INTO sys_config_item(config_group,config_key,config_name,value_type,plain_value,enabled,remark,created_at,updated_at)
SELECT 'intake.testDeploy','bindings','提测测试发布绑定','TEXT',
'[{"id":"scs-saps-test","businessLineCode":"BL000011","systemName":"scs-saps","projectPath":"scs/scs-saps","instanceId":"jenkins","baseUrl":"http://10.10.116.21:10004/","jobName":"test-scs-saps","environmentCode":"TEST","deployTarget":"test-scs-server","serverAddress":"10.10.116.43","publicBranch":"master","enabled":true}]',
1,'仅限已核实的测试发布；连接凭据复用系统集成',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM sys_config_item WHERE config_group='intake.testDeploy' AND config_key='bindings');
