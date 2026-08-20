-- 保费分期新渠道合同配置。
-- 母版：生产环境山西自营 XXT-PRJ-ZY-A。
-- 范围：复制全部有效合同配置及签署方，明确排除居间服务费协议 insurance.intermediary.service.agreement。
-- 执行前只需确认以下 SET 值；@contract_config_id 为脚本内部变量，无需填写。

SET @tenant_code = 'T8DGY35T935';
SET @project_code = 'XXT-PRJ-YW-A';
SET @callback_url = 'https://scf-self-opgateway.thchengtay.com/scf-order/api/contract/callback';
SET @jump_back_url = 'https://scf-self-h5.thchengtay.com/#/';
SET @contract_config_id = NULL;

-- 统一字段：除项目、回调地址和跳转地址外，其余业务值原样参照生产山西自营。

-- APPLICATION_AUTH / 个人征信查询授权书
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.credit.authorization','个人征信查询授权书','个人征信查询授权书','V1.0',NULL,1,'APPLICATION_AUTH','个人授权','TEMPLATE',NULL,0,NULL,0,'SAASAPI_SIGN','100_000001',NULL,NULL,@callback_url,@jump_back_url,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'CUSTOMER','车主','1','10',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- APPLICATION_AUTH / 电子签名授权委托书(个人)
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.electronic.signature','电子签名授权委托书(个人)','电子签名授权委托书(个人)','V1.0',NULL,1,'APPLICATION_AUTH','个人授权','TEMPLATE',NULL,0,NULL,0,'SAASAPI_SIGN','100_000002',NULL,NULL,@callback_url,@jump_back_url,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'CUSTOMER','车主','1','10',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- CHANNEL_AUTH / 电子签名授权委托书(企业)
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.electronic.signature.enterprise','电子签名授权委托书(企业)','电子签名授权委托书(企业)','V1.0',NULL,1,'CHANNEL_AUTH','后台渠道挂靠公司认证时','TEMPLATE',NULL,0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,@jump_back_url,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'ENTERPRISE','挂靠公司','1','10',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- BINDING_CARD / 委托代理服务合同(商业险)
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.agency.service.contract','委托代理服务合同(商业险)','委托代理服务合同(商业险)','V1.0',NULL,1,'BINDING_CARD','进件签约','TEMPLATE','{"insuranceType": "1,2"}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(@contract_config_id,'CUSTOMER','车主','1','10',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL),
(@contract_config_id,'ENTERPRISE','挂靠公司','1','10',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- BINDING_CARD / 委托扣款服务协议及委托扣款授权书(商业险)
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.agreement.contract','委托扣款服务协议及委托扣款授权书(商业险)','委托扣款服务协议及委托扣款授权书(商业险)','V1.0',NULL,1,'BINDING_CARD','进件签约','TEMPLATE','{"insuranceType": "1,2"}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'CUSTOMER','车主','1','10',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- BINDING_CARD / 应收账款转让通知书及回执-商业险(个人)
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.receivable.transfer','应收账款转让通知书及回执-商业险(个人)','应收账款转让通知书及回执-商业险(个人)','V1.0',NULL,1,'BINDING_CARD','进件签约','TEMPLATE','{"insuranceType": "1,2"}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'CUSTOMER','车主','1','10',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- BINDING_CARD / 应收账款转让通知书及回执-商业险(企业)
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.receivable.transfer.enterprise','应收账款转让通知书及回执-商业险(企业)','应收账款转让通知书及回执-商业险(企业)','V1.0',NULL,1,'BINDING_CARD','进件签约','TEMPLATE','{"insuranceType": "1,2"}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'ENTERPRISE','挂靠公司','1','10',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- BINDING_CARD / 委托代理服务合同(非商业险)
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.agency.service.contract.nc','委托代理服务合同(非商业险)','委托代理服务合同(非商业险)','V1.0',NULL,1,'BINDING_CARD','进件签约-非商业险','TEMPLATE','{"insuranceType": "2"}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(@contract_config_id,'CUSTOMER','车主','1','10',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL),
(@contract_config_id,'ENTERPRISE','挂靠公司','1','10',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- BINDING_CARD / 委托扣款服务协议及委托扣款授权书(非商业险)
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.agreement.contract.nc','委托扣款服务协议及委托扣款授权书(非商业险)','委托扣款服务协议及委托扣款授权书(非商业险)','V1.0',NULL,1,'BINDING_CARD','进件签约-非商业险','TEMPLATE','{"insuranceType": "2"}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'CUSTOMER','车主','1','10',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- BINDING_CARD / 应收账款转让通知书及回执-非商业险(个人)
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.receivable.transfer.nc','应收账款转让通知书及回执-非商业险(个人)','应收账款转让通知书及回执-非商业险(个人)','V1.0',NULL,1,'BINDING_CARD','进件签约-非商业险','TEMPLATE','{"insuranceType": "2"}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'CUSTOMER','车主','1','10',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- BINDING_CARD / 应收账款转让通知书及回执-非商业险(企业)
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.receivable.transfer.enterprise.nc','应收账款转让通知书及回执-非商业险(企业)','应收账款转让通知书及回执-非商业险(企业)','V1.0',NULL,1,'BINDING_CARD','进件签约-非商业险','TEMPLATE','{"insuranceType": "2"}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'ENTERPRISE','挂靠公司','1','10',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- BINDING_SUCCESS / 交强险投保单
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.compulsory.contract','交强险投保单','交强险投保单','V1.0',NULL,1,'BINDING_SUCCESS','进件-客户签约完成后','ATTACHMENT',NULL,0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'FUNDER','运营方','3','30',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- BINDING_SUCCESS / 商业险投保单
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.commercial.contract','商业险投保单','商业险投保单','V1.0',NULL,1,'BINDING_SUCCESS','进件-客户签约完成后','ATTACHMENT',NULL,0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'FUNDER','运营方','3','30',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- BINDING_SUCCESS / 附加险投保单
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.additional.contract','附加险投保单','附加险投保单','V1.0',NULL,1,'BINDING_SUCCESS','进件-客户签约完成后','ATTACHMENT',NULL,0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'FUNDER','运营方','3','30',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- LOAN_APPLICATION / 委托代理服务合同(商业险)
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.agency.service.contract.file','委托代理服务合同(商业险)','委托代理服务合同(商业险)','V1.0',NULL,1,'LOAN_APPLICATION','放款后','ATTACHMENT','{"insuranceType": "1,2"}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'FUNDER','运营方','3','30',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- LOAN_APPLICATION / 委托扣款服务协议及委托扣款授权书(商业险)
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.agreement.contract.file','委托扣款服务协议及委托扣款授权书(商业险)','委托扣款服务协议及委托扣款授权书(商业险)','V1.0',NULL,1,'LOAN_APPLICATION','放款后','ATTACHMENT','{"insuranceType": "1,2"}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'FACTORING','资金方','3','30',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- LOAN_APPLICATION / 应收账款转让通知书及回执-商业险(个人)
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.receivable.transfer.file','应收账款转让通知书及回执-商业险(个人)','应收账款转让通知书及回执-商业险(个人)','V1.0',NULL,1,'LOAN_APPLICATION','放款后','ATTACHMENT','{"insuranceType": "1,2"}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'FUNDER','运营方','3','30',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- LOAN_APPLICATION / 应收账款转让通知书及回执-商业险(企业)
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.receivable.transfer.enterprise.file','应收账款转让通知书及回执-商业险(企业)','应收账款转让通知书及回执-商业险(企业)','V1.0',NULL,1,'LOAN_APPLICATION','放款后','ATTACHMENT','{"insuranceType": "1,2"}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'FUNDER','运营方','3','30',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- LOAN_APPLICATION / 委托代理服务合同(非商业险)
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.agency.service.contract.file.nc','委托代理服务合同(非商业险)','委托代理服务合同(非商业险)','V1.0',NULL,1,'LOAN_APPLICATION','放款后-非商业险','ATTACHMENT','{"insuranceType": "2"}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'FUNDER','运营方','3','30',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- LOAN_APPLICATION / 委托扣款服务协议及委托扣款授权书(非商业险)
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.agreement.contract.file.nc','委托扣款服务协议及委托扣款授权书(非商业险)','委托扣款服务协议及委托扣款授权书(非商业险)','V1.0',NULL,1,'LOAN_APPLICATION','放款后-非商业险','ATTACHMENT','{"insuranceType": "2"}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'FACTORING','资金方','3','30',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- LOAN_APPLICATION / 应收账款转让通知书及回执-非商业险(个人)
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.receivable.transfer.file.nc','应收账款转让通知书及回执-非商业险(个人)','应收账款转让通知书及回执-非商业险(个人)','V1.0',NULL,1,'LOAN_APPLICATION','放款后-非商业险','ATTACHMENT','{"insuranceType": "2"}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'FUNDER','运营方','3','30',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- LOAN_APPLICATION / 应收账款转让通知书及回执-非商业险(企业)
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.receivable.transfer.enterprise.file.nc','应收账款转让通知书及回执-非商业险(企业)','应收账款转让通知书及回执-非商业险(企业)','V1.0',NULL,1,'LOAN_APPLICATION','放款后-非商业险','ATTACHMENT','{"insuranceType": "2"}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'FUNDER','运营方','3','30',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- FINANCING_APPLICATION / 保理融资申请书
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.financing.application','保理融资申请书','保理融资申请书','V1.0',NULL,1,'FINANCING_APPLICATION','融资申请','TEMPLATE',NULL,0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'FUNDER','运营方','3','30',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- FINANCING_APPLICATION / 保理融资审核意见书
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.audit.letter','保理融资审核意见书','保理融资审核意见书','V1.0',NULL,1,'FINANCING_APPLICATION','融资申请','TEMPLATE',NULL,0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'FACTORING','资金方','3','30',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- CAPITAL_SIGN / 保理合同
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.plateform.factoring.contract','保理合同','保理合同','V1.0',NULL,1,'CAPITAL_SIGN','全平台运营方与资金方','TEMPLATE',NULL,0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(@contract_config_id,'FUNDER','运营方','3','30',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL),
(@contract_config_id,'FACTORING','资金方','3','30',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- FINANCE_AUDIT_PASS / 保理合同
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.to.one.factoring.contract','保理合同','保理合同','V1.0',NULL,1,'FINANCE_AUDIT_PASS','审核通过','ATTACHMENT',NULL,0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'FUNDER','先行通供应链','2','20',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- EXTENDED_WARRANTY / 延保单
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.extended.warranty','延保单','延保单','V1.0',NULL,1,'EXTENDED_WARRANTY','延保申请','ATTACHMENT',NULL,0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'FUNDER','先行通供应链','2','20',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- REFUND / 退费单
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.refund.contract','退费单','退费单','V1.0',NULL,1,'REFUND','退费申请','ATTACHMENT',NULL,0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'FUNDER','先行通供应链','2','20',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- SURRENDER / 退保单：车主 + 先行通供应链
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.surrender.contract.two','退保单','退保单','V1.0',NULL,1,'SURRENDER','退保申请','ATTACHMENT','{"signatories": 1}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(@contract_config_id,'CUSTOMER','车主','1','20',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL),
(@contract_config_id,'FUNDER','先行通供应链','2','20',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- SURRENDER / 退保单：车主 + 挂靠公司 + 先行通供应链
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.surrender.contract.three','退保单','退保单','V1.0',NULL,1,'SURRENDER','退保申请','ATTACHMENT','{"signatories": 3}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(@contract_config_id,'CUSTOMER','车主','1','10',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL),
(@contract_config_id,'ENTERPRISE','挂靠公司','2','20',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL),
(@contract_config_id,'FUNDER','先行通供应链','2','20',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- SURRENDER / 退保单：先行通供应链
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.surrender.contract.one','退保单','退保单','V1.0',NULL,1,'SURRENDER','退保申请','ATTACHMENT','{"signatories": 0}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES (@contract_config_id,'FUNDER','先行通供应链','2','20',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- SURRENDER / 退保单：挂靠公司 + 先行通供应链
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.self.surrender.contract.enterprise.two','退保单','退保单','V1.0',NULL,1,'SURRENDER','退保申请','ATTACHMENT','{"signatories": 2}',0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,NULL);
SET @contract_config_id = LAST_INSERT_ID();
INSERT INTO contract_config_signers
(`contract_config_id`,`identity`,`identity_name`,`identity_type`,`sign_type`,`sort`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(@contract_config_id,'ENTERPRISE','挂靠公司','2','20',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL),
(@contract_config_id,'FUNDER','先行通供应链','2','20',NULL,NULL,NOW(),FALSE,FALSE,NULL,NULL);

-- SUPPLEMENT / 补充协议（生产山西自营该配置没有签署方记录）
INSERT INTO contract_config
(`main_id`,`tenant_code`,`project_code`,`template_code`,`template_name`,`template_name_custom`,`template_version`,`template_url`,`user_type`,`scene_code`,`scene_title`,`generateType`,`use_condition`,`silent_sign`,`contract_no_rule`,`ref_other_contract`,`sign_mode`,`biz_type`,`contract_vars`,`sort`,`callback_url`,`jump_back_url`,`hide`,`create_by`,`create_time`,`is_delete`,`is_cancel`,`version`,`remark`)
VALUES
(NULL,@tenant_code,@project_code,'insurance.add.agreement','补充协议','补充协议','V1.0',NULL,1,'SUPPLEMENT','补充','ATTACHMENT',NULL,0,NULL,0,'SAASAPI_SIGN',NULL,NULL,NULL,@callback_url,NULL,'0',NULL,NOW(),FALSE,FALSE,NULL,'线下签署或非系统触发的额外签署合同');
