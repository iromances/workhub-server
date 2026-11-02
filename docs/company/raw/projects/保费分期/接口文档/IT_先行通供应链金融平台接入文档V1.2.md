**先行通供应链平台对接文档**

文件更改摘要

| 日期         | 版本号  | 修订说明                                                                                                         | 修订人 | 审核人 | 批准人 |
|------------|------|--------------------------------------------------------------------------------------------------------------|-----|--------|--------|
| 2024.05.07 | V1.0 | 文档初始化                                                                                                        | 石浩  |        |        |
| 2024.06.04 | V1.2 | 增加3.8 变更相关接口                                                                                                 | 吕刘浩 | | |
| 2026.01.23 | V1.3 | 3.1.2 进件申请 customerPerson 数据集增加 companyLegalCertNo,companyLegalMobile,companyLegalIdFront,companyLegalIdBack | 林成铭 | | |
| 2026.02.03 | V1.4 | 3.6.2 退保结果查询 3.6.3 合作方退保通知  增加业务办理状态                                                                         | 林成铭 | | |
| 2026.05.08 | V1.5 | 3.4.7 对账单通知 bizType 增加对账类型 10061 代收代付对账单                                                                     | 尹军  | | |
| 2026.06.16 | V1.6 | 新增京东支付渠道                                                                                                     | 石浩  | | |


目录

[1 引言 ](#sec-1)

[1.1 概述 ](#sec-1-1)

[1.2 适用范围 ](#sec-1-2)

[2 开发规范 ](#sec-2)

[2.1 通信地址 ](#sec-2-1)

[2.2 通信协议 ](#sec-2-2)

[2.3 通信安全 ](#sec-2-3)

[2.4 通信规范 ](#sec-2-4)

[3 业务相关接口 ](#sec-3)

[3.1 进件 ](#sec-3-1)

[3.1.1 文件上传 ](#sec-3-1-1)

[3.1.2 进件申请 ](#sec-3-1-2)

[3.1.3 进件结果通知 ](#sec-3-1-3)

[3.1.4 进件结果查询 ](#sec-3-1-4)

[3.1.5 进件撤销 ](#sec-3-1-5)

[3.1.6 申请材料补充 ](#sec-3-1-6)

[3.1.7 贷后资料补充 ](#sec-3-1-7)

[3.1.8 保证金查询 ](#sec-3-1-8)

[3.2 签约 ](#sec-3-2)

[3.2.1 签约申请 ](#sec-3-2-1)

[3.2.2 签约确认 ](#sec-3-2-2)

[3.2.3 代扣协议上传 ](#sec-3-2-3)

[3.2.4 解约 ](#sec-3-2-4)

[3.2.5 解约确认 ](#sec-3-2-5)

[3.3 放款 ](#sec-3-3)

[3.3.1 放款申请 ](#sec-3-3-1)

[3.3.2 放款查询 ](#sec-3-3-2)

[3.3.3 放款结果通知 ](#sec-3-3-3)

[3.3.4 合同下载 ](#sec-3-3-4)

[3.3.5 还款计划试算 ](#sec-3-3-5)

[3.3.6 放款凭证下载 ](#sec-3-3-6)

[3.4 还款 ](#sec-3-4)

[3.4.1 还款试算 ](#sec-3-4-1)

[3.4.2 支付结算申请 ](#sec-3-4-2)

[3.4.3 支付结算结果查询 ](#sec-3-4-3)

[3.4.4 支付结算结果通知 ](#sec-3-4-4)

[3.4.5 退款 ](#sec-3-4-5)

[3.4.6 还款计划查询 ](#sec-3-4-6)

[3.4.7 对账单通知 ](#sec-3-4-7)

[3.4.8 对账单结算通知 ](#sec-3-4-8)

[3.5 退票 ](#sec-3-5)

[3.5.1 退票通知 ](#sec-3-5-1)

[3.5.2 退票结算通知 ](#sec-3-5-2)

[3.6 退保 ](#sec-3-6)

[3.6.1 申请退保 ](#sec-3-6-1)

[3.6.2 退保结果查询 ](#sec-3-6-2)

[3.6.3 合作方退保通知 ](#sec-3-6-3)

[3.6.4 退保批复通知 ](#sec-3-6-4)

[3.6.5 退保撤销 ](#sec-3-6-5)

[3.7 退费 ](#sec-3-7)

[3.7.1 退费申请 ](#sec-3-7-1)

[3.7.2 退费结果查询 ](#sec-3-7-2)

[3.7.3 退费通知 ](#sec-3-7-3)

[3.8 车险变更 ](#sec-3-8)

[3.8.1 变更申请 ](#sec-3-8-1)

[3.8.2 变更结果查询 ](#sec-3-8-2)

[3.8.3 变更结果通知 ](#sec-3-8-3)

[3.8.4 变更批复通知 ](#sec-3-8-4)

[4 附录 ](#sec-4)

[4.1 附1：验签公私钥证书生成步骤 ](#app-1)

[4.2 附2：加签验签工具类 ](#app-2)

[4.3 附3：银行代码 ](#app-3)

[4.4 附4：渠道编码 ](#app-4)

[4.5 附5：省/直辖市 ](#app-5)

[4.6 附6：支付方式编码 ](#app-6)

[4.7 附7：结算方式编码 ](#app-7)

[4.8 附8：用户标识 ](#app-8)

[4.9 附9：支付状态 ](#app-9)

[4.10 附10：账单状态 ](#app-10)

[4.11 附11：结算状态 ](#app-11)

[4.12 附12：对账单类型 ](#app-12)

[4.13 附13：对账单科目编码 ](#app-13)

[4.14 附14：文件类型 ](#app-14)

[4.15 附15：最高学历 ](#app-15)

[4.16 附16：职业类型 ](#app-16)

[4.17 附17：婚姻状态 ](#app-17)

[4.18 附18：银行卡开户证件类型 ](#app-18)

[4.19 附19：保险类型 ](#app-19)

[4.20 附20：账单科目编码 ](#app-20)


<a id="sec-1"></a>
# 1. 引言

<a id="sec-1-1"></a>
## 1.1 概述

先行通供应链金融项目接入文档

<a id="sec-1-2"></a>
## 1.2 适用范围

本文档的适用对象为接入平台的业务方技术开发人员、日常维护人员

<a id="sec-2"></a>
# 2. 开发规范

<a id="sec-2-1"></a>
## 2.1 通信地址

测试环境：https://test-scf-gateway.thchengtay.com/

生产环境：

<a id="sec-2-2"></a>
## 2.2 通信协议

由先行通供应链金融平台提供接口地址，业务接入方调用。

采用HTTPS协议进行请求和响应的处理。所有接口均采用POST请求方式，接口响应返回JSON格式的数据。

接口未注明处均使用UTF-8编码。

接口请求需进行签名，详见[2.3通信安全](#sec-2-3)。

部分通知类接口数据压缩规范：

因部分接口数据量较多时，存在耗时较长、接口超时的情况。故该类接口使用GZIP对数据内容进行压缩。当通知接口的request head 中包含 Content-Encoding=gzip时，需要对body数据进行解压缩处理。反之不需要。

<a id="sec-2-3"></a>
## 2.3 通信安全

为保证数据传输过程中的数据完整性和真实性，我们需要对数据进行数字签名，在接收签名数据之后进行签名校验，默认签名算法为MD5WithRSA，摘要算法为MD5。测试环境接入时，平台会为接入方分配唯一的平台商户号merId、对应密码secretKey以及私钥证书和证书对应密码。签名使用私钥(.pfx)，验签使用公钥(.pem)。生产环境由接入方生成密钥对，提供给平台方2048位的PKCS12格式的公钥(.pem)。

签名流程：

将完整的请求body中的数据进行排序。排序规则为字段名的字典序正序排序(a-z)，然后转为UTF-8编码的字符串，将排序之后的字符串使用数字证书进行签名，将签名后得到的字节数组以base64编码方式得到的字符串值通过signedMsg在请求头中传递。

接入方的请求服务器需配置IP白名单，不在白名单中的服务器将被拒绝请求。

文件上传相关接口的请求头中只需merId和secretKey，不需要签名。

<a id="sec-2-4"></a>
## 2.4 通信规范

除特殊约定外，所有接口的请求和响应的返回信息均以JSON字符串形式返回，HTTP请求内容类型Content-Type = application/json

除特殊约定外，所有接口的HTTP请求头中均需包含以下字段：

| 字段名称  | 字段释义   | 字段格式 | 是否必须 | 备注         |
|-----------|------------|----------|----------|--------------|
| merId     | 商户ID     | String   | 是       | 平台分配     |
| secretKey | 商户key    | String   | 是       | 平台分配     |
| signedMsg | 签名值     | String   | 是       | 请求体签名值 |
| timeStamp | 时间戳     | Long     | 是       | 当前时间戳   |
| requestId | 请求唯一ID | String   | 是       | 单次请求唯一 |

业务字段数据在请求body中以明文的JSON格式进行传递。详细业务数据字段参见[3业务相关接口](#sec-3)

HTTP请求状态码 status code 遵从行业技术规范，以下code有特殊含义

| HTTP状态码 | Body中的code | 释义             | 备注                                                         |
| ---------- | ------------ | ---------------- | ------------------------------------------------------------ |
| 401        | 000401       | 请求头不完整     | 请求头参数缺失 |
| 403        | 000403       | 服务器拒绝请求   | 请求IP非白名单                                               |
| 405        | 000405       | 请求方法不支持   | 请求method不是post                                           |
| 400        | 000600       | 请求ID已存在     | requestId不唯一                                    |
| 400        | 000601       | 商户信息不存在   | merId和secretKey无对应商户                     |
| 400        | 000602       | 签名验证失败     | signedMsg验证失败                                  |
| …          | 无           | 其他未说明状态码 |                                                              |

除特殊约定外所有接口的返回body中均含有以下字段

| 字段名称 | 字段释义                             | 字段格式 | 是否必须 |
|----------|--------------------------------------|----------|----------|
| code     | 返回code                             | String   | 是       |
| data     | 具体业务数据详见各个业务接口         | Object   | 是       |
| message  | 成功：success； 失败：具体的失败原因 | String   | 是       |

<a id="sec-3"></a>
# 3. 业务相关接口

<a id="sec-3-1"></a>
## 3.1 进件

<a id="sec-3-1-1"></a>
### 3.1.1 文件上传

<a id="heading-12"></a>
#### 接口说明

| 接口描述 | 文件上传                       |
|----------|----------------------------|
| 请求地址 | /order/api/file/fileUpload |
| 接口备注 | 文件上传，图片文件不超过2M，合同不超过5M     |

<a id="heading-13"></a>
#### 请求参数

| 字段名称 | 字段释义 | 字段格式     | 是否必须 | 备注             |
| -------- |------| ------------ | -------- |----------------|
| content  | 文件内容 | byteToBase64 | 是       | base64加密字符串 |
| filename | 文件名称 | String       | 是       | 带格式后缀的文件名称     |
| type     | 文件类型 | string       | 是       | 见附录14          |

<a id="heading-14"></a>
#### 响应参数

| 字段名称 | 字段释义     | 字段格式 | 是否必须 | 备注         |
|----------|--------------|----------|----------|--------------|
| fileKey  | 文件唯一标识 | String   | 是       | 文件唯一标识 |

<a id="heading-15"></a>
#### 请求示例

```
{
    "content":"Ly8g6Ieq5a6a5LmJ5rOo6KejDQpAUmV0ZW50aW9uKFJldGVudGlvblBvbGljeS5SVU5USU1FKQ0KQFRhcmdldChFbGVtZW50VHlwZS5NRVRIT0QpDQpwdWJsaWMgQGludGVyZmFjZSBNeUFubm90YXRpb24gew0KICAgIFN0cmluZyB2YWx1ZSgpOw0KfQ0KIA0KLy8g5a6e546wSGFuZGxlckludGVyY2VwdG9y5o6l5Y+jDQpwdWJsaWMgY2xhc3MgTXlJbnRlcmNlcHRvciBpbXBsZW1lbnRzIEhhbmRsZXJJbnRlcmNlcHRvciB7DQogICAgQE92ZXJyaWRlDQogICAgcHVibGljIGJvb2xlYW4gcHJlSGFuZGxlKEh0dHBTZXJ2bGV0UmVxdWVzdCByZXF1ZXN0LCBIdHRwU2VydmxldFJlc3BvbnNlIHJlc3BvbnNlLCBPYmplY3QgaGFuZGxlcikgdGhyb3dzIEV4Y2VwdGlvbiB7DQogICAgICAgIGlmIChoYW5kbGVyIGluc3RhbmNlb2YgSGFuZGxlck1ldGhvZCkgew0KICAgICAgICAgICAgSGFuZGxlck1ldGhvZCBoYW5kbGVyTWV0aG9kID0gKEhhbmRsZXJNZXRob2QpIGhhbmRsZXI7DQogICAgICAgICAgICBNeUFubm90YXRpb24gbXlBbm5vdGF0aW9uID0gaGFuZGxlck1ldGhvZC5nZXRNZXRob2RBbm5vdGF0aW9uKE15QW5ub3RhdGlvbi5jbGFzcyk7DQogICAgICAgICAgICBpZiAobXlBbm5vdGF0aW9uICE9IG51bGwpIHsNCiAgICAgICAgICAgICAgICAvLyDov5nph4zlj6/ku6Xojrflj5bms6jop6PnmoTlsZ7mgKflgLwNCiAgICAgICAgICAgICAgICBTdHJpbmcgdmFsdWUgPSBteUFubm90YXRpb24udmFsdWUoKTsNCiAgICAgICAgICAgICAgICAvLyDmoLnmja52YWx1Zei/m+ihjOebuOW6lOeahOWkhOeQhg0KICAgICAgICAgICAgfQ0KICAgICAgICB9DQogICAgICAgIHJldHVybiB0cnVlOw0KICAgIH0NCn0NCiANCi8vIOazqOWGjOaLpuaIquWZqA0KQENvbmZpZ3VyYXRpb24NCnB1YmxpYyBjbGFzcyBXZWJDb25maWcgaW1wbGVtZW50cyBXZWJNdmNDb25maWd1cmVyIHsNCiAgICBAT3ZlcnJpZGUNCiAgICBwdWJsaWMgdm9pZCBhZGRJbnRlcmNlcHRvcnMoSW50ZXJjZXB0b3JSZWdpc3RyeSByZWdpc3RyeSkgew0KICAgICAgICByZWdpc3RyeS5hZGRJbnRlcmNlcHRvcihuZXcgTXlJbnRlcmNlcHRvcigpKTsNCiAgICB9DQp9DQogDQovLyDkvb/nlKjms6jop6PnmoTnpLrkvosNCkBSZXN0Q29udHJvbGxlcg0KcHVibGljIGNsYXNzIE15Q29udHJvbGxlciB7DQogDQogICAgQE15QW5ub3RhdGlvbih2YWx1ZSA9ICJleGFtcGxlIikNCiAgICBAR2V0TWFwcGluZygiL2V4YW1wbGUiKQ0KICAgIHB1YmxpYyBTdHJpbmcgZXhhbXBsZSgpIHsNCiAgICAgICAgcmV0dXJuICJIZWxsbywgV29ybGQhIjsNCiAgICB9DQp9",
    "filename":"身份证人像面.jpg",
    "type":"100"
}
```

<a id="heading-16"></a>
#### 响应示例
```
{
    "code": "000000",
    "data": {
        "fileKey": "66455FA5482D373931C35492"
    },
    "message": "Success"
}
```

<a id="heading-17"></a>
#### 响应码说明

| code值 | code说明 | 备注 |
|--------|----------|------|
| 000000 | 请求成功 |      |
| 001001 | 处理失败 |      |

<a id="sec-3-1-2"></a>
### 3.1.2 进件申请

<a id="heading-19"></a>
#### 接口说明

| 接口描述 | 进件申请                                                |
| -------- | ------------------------------------------------------- |
| 请求地址 | /order/api/apply/order                                  |
| 接口备注 | 申请单中附件信息为文件上传接口上传返回的fileKey填入传递 |

<a id="heading-20"></a>
#### 请求参数

| 字段名称                  | 字段释义               | 字段格式 | 必填 | 备注                                                         |
| ------------------------- | ---------------------- | -------- | ---- | ------------------------------------------------------------ |
| orderNo               | 进件订单编号           | string   | 是   | 业务方申请编号                               |
| order       | 订单基本信息       | Object   | 是   | 详见： order 数据集 |
| customerPerson | 车主信息 | Object | 是 | 详见：customrePerson 数据集 |
|merchant              | 保险公司信息           | Object   | 是   |详见：merchant 数据集|
| moreEmergencyContact | 紧急联系人信息 | Array | 否 | 详见：moreEmergencyContact 数据集 |
| vehicles | 投保车辆信息 | Array | 是 | 详见：vehicle 数据集 |
| bankCard              | 银行卡信息             | Object   | 否   | 详见：bankCard 数据集 |

order 数据集

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注 |
| -------- | -------- | -------- | ---- | ---- |
| prodCode                | 产品编码               | string   | 是   | 资方产品编码                                            |
| sceneType               | 场景类型               | int      | 是   | 5-保费分期                                                   |
| model                   | 模式                   | int      | 是   | 2-客户付息                                          |
| loanPeriod              | 贷款期数               | int      | 是   | 例如：3，6，9                                     |
| productType             | 产品类型               | string   | 否  | 合作方的产品分类代码                                         |
| repaymentType           | 还款方式               | int      | 是   | 4 ：提前付息按月还本                                         |
| supportFullSurrender    | 是否支持全额退保       | int      | 否   | 1：支持（默认） 0：不支持                                    |
| signatureManner         | 投保签约方式           | string   | 是   | 1：线上；2：线下                                             |

customerPerson 数据集

| 字段名称                   | 字段释义        | 字段格式 | 必填 | 备注                           |
|------------------------|-------------| -------- | ---- |------------------------------|
| name                   | 姓名          | string   | 是   |                              |
| mobile                 | 注册手机号       | string   | 是   |                              |
| idCardNo               | 身份证号        | string   | 是   |                              |
| idAddress              | 身份证地址       | string   | 是   |                              |
| effectDate             | 身份证有效期      | string   | 是   | 例如：<br>2019-01-22至2029-01-21 |
| age                    | 年龄          | int      | 否   |                              |
| gender                 | 性别          | int      | 否   | 0-未知 1-男2-女                  |
| nation                 | 民族          | string   | 否  |                              |
| idFront                | 身份证照片人像面    | string   | 是   | 文件上传类型为 100                  |
| idBack                 | 身份证照片国徽面    | string   | 是   | 文件上传类型为 101                  |
| email                  | 邮箱          | string   | 否   |                              |
| highestEducation       | 最高学历        | string   | 否   | 见附录15                        |
| job                    | 职业类型        | int      | 否   | 见附录16                        |
| maritalStatus          | 婚姻状态        | int      | 否   | 见附录17                        |
| livingProvince         | 现居住省市区      | string   | 是   |                              |
| livingAddress          | 现居住地址       | string   | 是   |                              |
| communicationAddress   | 通讯地址        | string   | 否   |                              |
| zipCode                | 通讯地址邮编      | string   | 否   |                              |
| companyBusinessLicense | 工作单位营业执照    | string   | 是  | 文件上传类型150                    |
| companyName            | 工作单位名称      | string   | 是   | 挂靠的物流公司信息                    |
| companyCertNo          | 工作单位统一社会信用码 | string   | 是   | 挂靠的物流公司信息                    |
| companyLegalName       | 工作单位法人名称    | string   | 是   | 挂靠的物流公司信息                    |
| companyLegalCertNo     | 工作单位法人证件号   | string   | 是   | 挂靠的物流公司信息                    |
| companyLegalMobile     | 工作单位法人电话   | string   | 是   | 挂靠的物流公司信息                    |
| companyLegalIdFront    | 工作单位法人证件人像面 | string   | 是   | 挂靠的物流公司信息    文件上传类型151       |
| companyLegalIdBack     | 工作单位法人证件国徽面 | string   | 是   | 挂靠的物流公司信息     文件上传类型152      |
| companyTelephone       | 工作单位电话      | string   | 是   | 挂靠的物流公司信息                    |
| companyAddress         | 工作单位地址      | string   | 是   | 挂靠的物流公司信息                    |
| companyContact         | 工作单位联系人     | string   | 是   | 挂靠的物流公司信息                    |
| companyContactMobile   | 工作单位联系人电话   | string   | 是   | 挂靠的物流公司信息                    |
| companyEmail           | 工作单位邮箱      | string   | 否   | 挂靠的物流公司信息                    |
| customerSupplement     | 客户补充信息      | Array    | 否   | 详见：补充信息 数据集                  |

merchant 数据集

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注 |
| -------- | -------- | -------- | ---- | ---- |
| merchantLocation        | 所在省/直辖市          | string   | 否   | 详见：附5：省/直辖市                                         |
| merchantName            | 公司名称               | string   | 是   |                                                              |
| merchantCertNo          | 统一社会信用代码       | string   | 否   |                                                              |
| merchantRealAddress     | 实际经营地址           | String   | 否   |                                                              |
| merchantLinkman         | 联系人                 | string   | 否   |                                                              |
| merchantLinkPhone       | 联系人电话             | sting    | 否   |                                                              |
| merchantSupplement | 商户补充信息           | Array    | 否   | 详见：merchantSupplement 数据集 |

merchantSupplement 数据集

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注 |
| -------- | -------- | -------- | ---- | ---- |
| key                    | 唯一标识英文           | string   | 是   | 信息唯一标识，具体参见补充说明                               |
| value                  | 信息具体值             | string   | 是   | 信息具体值，具体参见补充说明                                 |
| valueCn                | 标识中文描述           | string   | 是   | 唯一标识字段的中文描述                                       |
| remark                 | 信息描述               | string   | 否   | 信息描述                                                     |


 moreEmergencyContact 数据集

| 字段名称      | 字段释义     | 字段格式 | 是否必须 | 备注                 |
| ------------- | ------------ | -------- | -------- | -------------------- |
|ecpName|紧急联系人姓名|string| 是||
|ecpPhone|紧急联系人联系方式|string| 是||
|ecpRelation|紧急联系人关系|string| 是||

<a id="heading-21"></a>
#### 响应参数
| 字段名称      | 字段释义     | 字段格式 | 是否必须 | 备注                 |
| ------------- | ------------ | -------- | -------- | -------------------- |
| applicationNo | 申请单号     | string   | 是       | 资方申请单唯一标识   |
| applyResult   | 申请结果     | string   | 否       | 结果描述             |
| orderNo       | 业务申请单号 | string   | 是       | 业务方唯一的申请单号 |

vehicle 数据集

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注                                 |
| -------- | -------- | -------- | ---- |------------------------------------|
|insuranceType          | 融资险种               | int      | 是   | 1：仅商业险 ；2：全部险种                     |
| insteadPay             | 是否代付其他险种       | int      | 是   | 0：不代付；1： 代付                        |
| plateNumber            | 车牌号                 | string   | 是   |                                    |
| vin                    | 车架号                 | string   | 是   |                                    |
| vehicleLicense         | 行驶证                 | string   | 是   | 文件上传类型为 170                        |
| vciAmount              | 商业险金额             | long     | 是   | 单位：分                               |
| vciFile                | 商业险投保单           | string   | 是   | 文件上传类型为 500                        |
| clivtaAmount           | 交强险金额             | long     | 否   | 单位：分                               |
| clivtaFile             | 交强险投保单           | string   | 否   | 文件上传类型为 510                        |
| vehicleVesselTax       | 车船税金额             | long     | 否   | 单位：分                               |
| additionalAmount       | 附加险金额             | long     | 否   | 单位：分                               |
| additionalFile         | 附加险投保单           | string   | 否   | 文件上传类型为 520                        |
| repaymentPlan     | 其他险种还款计划     | Array   | 否  | 融资险种为2时不可为空，  <br>详见：repaymentPlan 数据集 |

 repaymentPlan 数据集

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注 |
| -------- | -------- | -------- | ---- | ---- |
| period         | 期数           | int   | 是   ||
| otherFee         | 其他费用           | long   | 是   |  单位：分|

 bankCard 数据集

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注 |
| -------- | -------- | -------- | ---- | ---- |
| bankcardNo              | 银行卡号               | string   | 否   | 还款银行卡号                         |
| branchBankName          | 开户行支行名称         | string   | 否   | 开户行支行名称                                               |
| bankcardPhone           | 银行预留手机号         | string   | 否   |                                        |
| bankcardName            | 银行名称               | string   | 否   |                                                              |

<a id="heading-22"></a>
#### 请求示例

```
{
    "orderNo": "202405171705001",
    "order": {
        "prodCode": "PRD-JBR-A001",
        "sceneType": 5,
        "model": 2,
        "loanPeriod": "6",
        "repaymentType": 4,
        "supportFullSurrender": 1,
        "signatureManner": "1"
    },
    "customerPerson": {
        "name": "测试商业险",
        "mobile": "13120586222",
        "idCardNo": "410881199301010777",
        "idAddress": "上海市浦东新区花园路88号",
        "effectDate": "2017-06-02至2026-06-02",
        "age": 31,
        "gender": 1,
        "nation": "",
        "idFront": "66455FA5482D373931C35492",
        "idBack": "66455FA5482D373123assdfd",
        "livingProvince": "上海市",
        "highestEducation":"10",
        "livingAddress":"浦东新区",
        "companyEmail":"abc@163.com",
        "companyBusinessLicense": "",
        "companyName": "挂靠公司名",
        "companyCertNo": "浦东新区",
        "companyLegalName": "某人",
        "companyTelephone": "13120566623",
        "companyAddress": "浦东新区",
        "companyContact": "某某",
        "companyContactMobile": "62068856"
    },
    "merchant": {
        "merchantName": "保险公司123"
    },
    "vehicles": [
        {
            "insuranceType": "1",
            "insteadPay": 0,
            "plateNumber": "苏J12345",
            "vin": "vin1",
            "vehicleLicense": "66455FA5482D37a",
            "vciAmount": 600000,
            "vciFile": "66457737830B68175",
            "repaymentPlan":[]
        },
        {
            "insuranceType": "1",
            "insteadPay": 0,
            "plateNumber": "苏J54321",
            "vin": "vin2",
            "vehicleLicense": "6682D373931C35492",
            "vciAmount": 600000,
            "vciFile": "66457737537175",
            "repaymentPlan":[]
        }
    ]
}

```

<a id="heading-23"></a>
#### 响应示例

```
{
    "code": "000000",
    "message": "Success",
    "data": {
        "applicationNo": "JBR024051717330225",
        "applyResult": "申请成功！",
        "orderNo": "20240517173341"
    },
    "success": true
}
```

<a id="heading-24"></a>
#### 响应码说明

| code值 | code说明     | 备注                   |
| ------ | ------------ | ---------------------- |
| 000000 | 申请成功     |                        |
| 20440  | 接口异常     |                        |
| 001002 | 申请单已存在 |                        |
| 20001  | 必传参数为空 | 描述中具体描述哪个参数 |
| 31105  | 参数异常     |                        |


<a id="sec-3-1-3"></a>
### 3.1.3 进件结果通知

<a id="heading-26"></a>
#### 接口说明

| 接口描述 | 进件结果通知接口             |
| -------- | ---------------------------- |
| 请求地址 | 接入商提供                   |
| 接口备注 | 资方向业务方异步回推审核结果 |

<a id="heading-27"></a>
#### 请求参数

| 字段名称     | 字段释义 | 字段格式 | 是否必须 | 备注                                 |
|--------------|----------|----------|----------|--------------------------------------|
| auditResult  | 申请结果 | int      | 是       | 20-审核中<br>30-表示审批通过 <br>40-审批拒绝 |
| rejectReason | 拒绝原因 | String   | 否       |                                      |
| factoringConsideration | 保理对价  | Long   | 否       | 单位：分                            |
| firstPayment | 首笔款信息 | Object | 否       | 详见 firstPayment 数据集 |

firstPayment 数据集
| 字段名称     | 字段释义 | 字段格式 | 是否必须 | 备注                                 |
|--------------|----------|----------|----------|--------------------------------------|
| serialNo | 首笔款缴费单编号 | String   | 否       |银行足额转账备注缴费单编号|
| status | 首笔款收款状态 | String   | 否       |1：未收款；2：已收款 |
| amount | 首笔款金额 | Long  | 否       |单位：分 |
| subjects | 首笔款科目详情 | Array  | 否       |详见：subject 数据集 |

subjects 数据集
| 字段名称     | 字段释义 | 字段格式 | 是否必须 | 备注                                 |
|--------------|----------|----------|----------|--------------------------------------|
| subjectNo | 科目编号 | String   | 否       ||
| subjectName | 科目名称 | String   | 否       | |
| amount | 科目金额 | Long  | 否       |单位：分 |

<a id="heading-28"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 是否必须 | 备注     |
|----------|----------|----------|----------|----------|
| result   | 响应结果 | boolean  | 是       | 默认true |
| code     | 响应码   | string   | 是       |          |

<a id="heading-29"></a>
#### 请求示例

```
{
    "applicationNo": "JBR024051616016465",
    "orderNo": "LP4545445",
    "auditResult": 30,
    "rejectReason": "",
}
```

<a id="heading-30"></a>
#### 响应示例
```
{
    "code": "000000",
    "result": true
}
```


<a id="heading-31"></a>
#### 响应码说明

| code值 | code说明 | 备注 |
|--------|----------|------|
| 000000 | 请求成功 |      |
| 001001 | 处理失败 |      |

<a id="sec-3-1-4"></a>
### 3.1.4 进件结果查询

<a id="heading-33"></a>
#### 接口说明

| 接口描述 | 进件结果查询接口             |
|----------|------------------------------|
| 请求地址 | /order/api/apply/auditResult |
| 接口备注 | 风控审核结果查询接口         |

<a id="heading-34"></a>
#### 请求参数

| 字段名称      | 字段释义       | 字段格式 | 必填 | 备注                       |
| ------------- | -------------- | -------- | ---- | -------------------------- |
| orderNo       | 进件订单编号   | string   | 是   | 和资方申请单号不可同时为空 |
| applicationNo | 资方申请单编号 | string   | 是   | 跟进件订单编号不可同时为空 |

<a id="heading-35"></a>
#### 响应参数

| 字段名称     | 字段释义  | 字段格式   | 是否必须 | 备注                              |
|--------------|-------|--------|----------|---------------------------------|
| auditResult  | 申请结果  | int    | 是       | 20-审核中<br>30-表示审批通过 <br>40-审批拒绝 |
| rejectReason | 拒绝原因  | String | 否       |                                 |
| factoringConsideration | 保理对价  | Long   | 否       | 单位：分                            |
| firstPayment | 首笔款信息 | Object | 否       | 详见 firstPayment 数据集             |

firstPayment 数据集

| 字段名称     | 字段释义 | 字段格式 | 是否必须 | 备注                                 |
|--------------|----------|----------|----------|--------------------------------------|
| serialNo | 首笔款缴费单编号 | String   | 否       |银行足额转账备注缴费单编号|
| status | 首笔款收款状态 | String   | 否       |1：未收款；2：已收款 |
| amount | 首笔款金额 | Long  | 否       |单位：分 |
| subjects | 首笔款科目详情 | Array  | 否       |详见：subject 数据集 |

subjects 数据集

| 字段名称     | 字段释义 | 字段格式 | 是否必须 | 备注                                 |
|--------------|----------|----------|----------|--------------------------------------|
| subjectNo | 科目编号 | String   | 否       ||
| subjectName | 科目名称 | String   | 否       | |
| amount | 科目金额 | Long  | 否       |单位：分 |


<a id="heading-36"></a>
#### 请求示例
```json
{
    "applicationNo": "JBR024051713279641"
}
```
<a id="heading-37"></a>
#### 响应示例
```json
{
    "code": "000000",
    "message": "success",
    "data": {
        "applicationNo": "JBR024051713279641",
        "orderNo": "3b7712b7-5791-4b6d-b7a2-3a0dfc545da0",
        "auditResult": "30",
        "rejectReason": null,
        "firstPayment": {
            "serialNo": "02405170001",
            "status": "1",
            "amount": 14000,
            "subjects": [
                {
                    "subjectNo": "201",
                    "subjectName": "首付款",
                    "amount": 10000
                },
                {
                    "subjectNo": "10201",
                    "subjectName": "资方服务费",
                    "amount": 4000
                }
            ]
        }
    },
    "success": true
}
```

<a id="heading-38"></a>
#### 响应码说明

| code值 | code说明           | 备注 |
|--------|--------------------|------|
| 000000 | 请求成功           |      |
| 001050 | 信息查询异常       |      |
| 001051 | 未查到相关申请信息 |      |

<a id="sec-3-1-5"></a>
### 3.1.5 进件撤销

<a id="heading-40"></a>
#### 接口说明

| 接口描述 | 进件撤销                                                                       |
|----------|--------------------------------------------------------------------------------|
| 请求地址 | /order/api/apply/orderCancel                                                   |
| 接口备注 | 申请单撤销（仅在未申请放款前或"放款失败"、"放款审批拒绝"、"银行退票"可以撤销） |

<a id="heading-41"></a>
#### 请求参数

| 字段名称      | 字段释义       | 字段格式 | 是否必须 | 备注                       |
| ------------- | -------------- | -------- | -------- | -------------------------- |
| orderNo       | 进件订单编号   | string   | 是       | 跟资方申请单编号不可同时为空 |
| applicationNo | 资方申请单编号 | string   | 是       | 跟进件订单编号不可同时为空 |
| cancelReason  | 撤销原因       | string   | 否       |      |

<a id="heading-42"></a>
#### 响应参数

| 字段名称      | 字段释义       | 字段格式 | 是否必须 | 备注  |
| ------------- | -------------- | -------- | -------- | -------------------------- |
| code       | 响应码   | string   | 是       |  |
| data | 响应数据 | string   | 否       |  |
| message  | 响应码描述       | string   | 是       |      |

<a id="heading-43"></a>
#### 请求示例

```
{
    "applicationNo": "JBR024051616016465"
}
```

<a id="heading-44"></a>
#### 响应示例

```
{
    "code": "000000",
    "message": "成功"
}
```

<a id="heading-45"></a>
#### 响应码说明

| code值 | code说明 | 备注 |
|--------|----------|------|
| 000000 | 撤销成功 |      |
| 001001 | 撤销失败 |      |
| 001002 | 处理异常 |      |

<a id="sec-3-1-6"></a>
### 3.1.6 申请材料补充

<a id="heading-47"></a>
#### 接口说明

| 接口描述 | 业务申请所需材料补充推送    |
|----------|-----------------------------|
| 请求地址 | /order/api/apply/supplement |
| 接口备注 | 合作方补充材料推送          |

<a id="heading-48"></a>
#### 请求参数

| 字段名称      | 字段释义     | 字段格式 | 是否必须 | 备注                                       |
| ------------- | ------------ | -------- | -------- | ------------------------------------------ |
| applicationNo | 资方申请编号 | string   | 是       | 资方进件时申请单编号                       |
| fileUrl       | 文件访问路径 | String   | 是       | 合作方的资源地址url                        |
| filekey       | 附件fileKey  | String   | 否       | 通过调用上传接口获取到的文件<br>唯一标识。 |
| fileName      | 文件名称     | String   | 是       | 带文件格式后缀的文件名称                   |
| fileType      | 文件类型     | string   | 是       | 170:车辆行驶证照片                         |
| vin           | 车架号       | string   | 否       | 车辆维度附件必传                           |

<a id="heading-49"></a>
#### 响应参数

| 字段名称    | 字段释义 | 字段格式 | 是否必须 | 备注 |
|-------------|----------|----------|----------|------|
| auditResult | 申请结果 | String   | 是       |      |

<a id="heading-50"></a>
#### 请求示例

```
{
    "applicationNo": "APP1234235235235",
    "fileUrl": "http://fdfs.product.com//group3/M00/21/94/rBBmVWFNrIGAeiycADpdte2PTj0655.mp4",
    "fileKey": "",
    "fileName": "个人意愿视频.mp4",
    "fileType": "130"
}
```

<a id="heading-51"></a>
#### 响应示例

```
{
    "code": "000000",
    "data": {
        "applyResult": "提交成功"
    },
    "success": true,
    "message": "Success"
}
```
<a id="heading-52"></a>
#### 响应码说明

| code值 | code说明               | 备注 |
| ------ | ---------------------- | ---- |
| 000000 | 申请成功               |      |
| 20440  | 接口异常               |      |
| 001051 | 未查到申请单           |      |
| 20001  | 资方申请单编号不能为空 |      |

<a id="sec-3-1-7"></a>
### 3.1.7 贷后资料补充

<a id="heading-54"></a>
#### 接口说明

| 接口描述 | 贷后材料补充推送    |
|----------|-----------------------------|
| 请求地址 | /order/api/apply/postLoanSupplement |
| 接口备注 | 1.贷后保险相关补充材料推送<br>2.请求参数为 **数组** 格式 |

<a id="heading-55"></a>
#### 请求参数

| 字段名称      | 字段释义     | 字段格式 | 是否必须 | 备注                                                     |
| ------------- | ------------ | -------- | -------- | -------------------------------------------------------- |
| applicationNo | 资方申请编号 | string   | 是       | 资方进件时申请单编号                                     |
| fileUrl       | 文件访问路径 | String   | 是       | 合作方的资源地址url                                      |
| filekey       | 附件fileKey  | String   | 否       | 通过调用上传接口获取到的文件<br>唯一标识。（意愿视频非必传） |
| fileName      | 文件名称     | String   | 是       | 带文件格式后缀的文件名称                                 |
| fileType      | 文件类型     | string   | 是       | 600:商业险保单;<br>610:交强险保单;<br>620:附加险保单 |
| vehicleVin      | 车架号     | string   | 是       | 车辆的唯一标识    |
| businessInsuranceNo      | 商业险保单号     | string   | 是       |     |

<a id="heading-56"></a>
#### 响应参数

| 字段名称    | 字段释义 | 字段格式 | 是否必须 | 备注 |
|-------------|----------|----------|----------|------|
| auditResult | 申请结果 | String   | 是       |      |

<a id="heading-57"></a>
#### 请求示例

```
{"content":
[
    {
        "applicationNo": "APP12342352345677",
        "fileUrl": "http://fdfs.product.com//group3/M00/21/94/rBBmVWFNrIGAeiycADpdte2PTj0655.pdf",
        "fileKey": "",
        "fileName": "商业险保单",
        "fileType": "600",
        "vehicleVin": "2024051511",
        "businessInsuranceNo": "202405150000001"
    },
    {
        "applicationNo": "APP12342352345677",
        "fileUrl": "http://fdfs.product.com//group3/M00/21/94/rBBmVWFNrIGAeiycADpdte2PTj0656.pdf",
        "fileKey": "",
        "fileName": "交强险保单",
        "fileType": "610",
        "vehicleVin": "2024051511",
        "businessInsuranceNo": "202405150000001"
    },
    {
        "applicationNo": "APP12342352345677",
        "fileUrl": "http://fdfs.product.com//group3/M00/21/94/rBBmVWFNrIGAeiycADpdte2PTj0657.pdf",
        "fileKey": "",
        "fileName": "附加险保单",
        "fileType": "620",
        "vehicleVin": "2024051511",
        "businessInsuranceNo": "202405150000001"
    }
]
}
```

<a id="heading-58"></a>
#### 响应示例

```
{
    "code": "000000",
    "data": {
        "applyResult": "提交成功"
    },
    "success": true,
    "message": "Success"
}
```

<a id="heading-59"></a>
#### 响应码说明

| code值 | code说明               | 备注 |
| ------ | ---------------------- | ---- |
| 000000 | 申请成功               |      |
| 20440  | 接口异常               |      |
| 001051 | 未查到申请单           |      |
| 20001  | 资方申请单编号不能为空 |      |

<a id="sec-3-1-8"></a>
### 3.1.8 保证金查询

<a id="heading-61"></a>
#### 接口说明

| 接口描述 | 进件结果查询接口             |
|----------|------------------------------|
| 请求地址 | /order/api/projectSecurityDeposit |
| 接口备注 | 保证金查询接口         |

<a id="heading-62"></a>
#### 请求参数

| 字段名称      | 字段释义       | 字段格式 | 必填 | 备注                       |
| ------------- | -------------- | -------- | ---- | -------------------------- |
| projectCode | 项目编码 | string   | 是   | 项目的唯一标识 |

<a id="heading-63"></a>
#### 响应参数

| 字段名称     | 字段释义  | 字段格式   | 是否必须 | 备注                              |
|--------------|-------|--------|----------|---------------------------------|
| currentMarginAmount  | 保证金金额  | double    | 是       | 单位：分 |
| securityDepositUsableAmount | 保证金可用余额  | double | 是       |        单位：分                         |
| securityDepositSupportAmount | 保证金支持进件金额  | double   | 是       | 单位：分                            |


<a id="heading-64"></a>
#### 请求示例
```json
{
    "projectCode": "XXT-PRJ-JBR-A"
}
```
<a id="heading-65"></a>
#### 响应示例
```json
{
    "code": "000000",
    "message": "Success",
    "data": {
        "projectCode": "XXT-PRJ-JBR-A",
        "name": "聚保融保费分期项目",
        "currentMarginAmount": 10000000.00,
        "securityDepositFreezeAmount": 55469.4400,
        "securityDepositUsableAmount": 9944530.5600,
        "securityDepositSupportAmount": 99445305.60
    },
    "success": true
}
```

<a id="heading-66"></a>
#### 响应码说明

| code值 | code说明           | 备注 |
|--------|--------------------|------|
| 200 | 请求成功           |      |
| 400 | 信息查询异常       |      |

<a id="sec-3-2"></a>
## 3.2 签约

<a id="sec-3-2-1"></a>
### 3.2.1 签约申请

<a id="heading-69"></a>
#### 接口说明

| 接口描述 | 签约申请                                                     |
| -------- | ------------------------------------------------------------ |
| 请求地址 | /pay/api/card/binding/apply                                  |
| 接口备注 | 1.暂只支持借记卡 <br>2.渠道为“宝付”时，四要素相同的卡重复签约以**最新**一次的签约返回的协议号为准，<br>之前协议作废。 |

<a id="heading-70"></a>
#### 请求参数

| 字段名称      | 字段释义       | 字段格式 | 必填 | 备注                           |
|---------------|----------------|----------|------|--------------------------------|
| applicationNo | 申请单号       | String   | 是   | 签约申请前置场景，该参数传“\*” |
| acctNo        | 银行卡号       | String   | 是   |                                |
| acctName      | 账户名         | String   | 是   |                                |
| certType      | 证件类型       | int      | 是   | 默认1-身份证                   |
| certNo        | 证件号         | String   | 是   |                                |
| phone         | 银行预留手机号 | String   | 是   |                                |
| projectNo     | 项目编号       | String   | 否   | 签约申请前置必传               |
| riskInfo      | 风控参数       | Object   | 否   |                                |

riskInfo 风控参数

| 字段名称 | 字段释义   | 字段格式 | 必填 | 备注                     |
|----------|------------|----------|------|--------------------------|
| clientIp | 当前用户IP | String   | 否   | 渠道为“新浪支付”时，必填 |

<a id="heading-71"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注 |
|----------|----------|----------|------|------|
| code     | 响应码   | String   | 是   |      |
| data     | 响应数据 | Object   | 是   |      |
| message  | 响应描述 | String   | 是   |      |
| success  | 响应结果 | boolean  | 是   |      |

data数据集：

| 字段名称    | 字段释义     | 字段格式 | 必填 | 备注                                     |
|-------------|--------------|----------|------|------------------------------------------|
| applyNo     | 签约申请流水 | String   | 是   |                                          |
| channelCode | 渠道编码     | String   | 是   | 见附录-渠道编码                          |
| agreeId     | 协议号       | String   | 否   | 客户维度签约时返回，且无需调用“签约确认” |

<a id="heading-72"></a>
#### 响应码说明

| code值 | code说明 | 备注 |
|--------|----------|------|
| 000000 | 申请成功 |      |

<a id="sec-3-2-2"></a>
### 3.2.2 签约确认

<a id="heading-74"></a>
#### 接口说明

| 接口描述 | 签约确认                                                     |
| -------- | ------------------------------------------------------------ |
| 请求地址 | /pay/api/card/binding/confirm                                |
| 接口备注 | 1.渠道为“宝付”时，四要素相同的卡重复签约以**最新**一次的签约返回的协议号为准，<br>之前协议作废。 |

<a id="heading-75"></a>
#### 请求参数

| 字段名称 | 字段释义     | 字段格式 | 必填 | 备注 |
|----------|--------------|----------|------|------|
| applyNo  | 签约申请流水 | String   | 是   |      |
| smsCode  | 短信验证码   | String   | 是   |      |
| riskInfo | 风控参数     | Object   | 否   |      |

riskInfo 风控参数

| 字段名称 | 字段释义   | 字段格式 | 必填 | 备注                     |
|----------|------------|----------|------|--------------------------|
| clientIp | 当前用户IP | String   | 否   | 渠道为“新浪支付”时，必填 |

<a id="heading-76"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注 |
|----------|----------|----------|------|------|
| code     | 响应码   | String   | 是   |      |
| data     | 响应数据 | Object   | 是   |      |
| message  | 响应描述 | String   | 是   |      |
| success  | 响应结果 | boolean  | 是   |      |

data数据集：

| 字段名称    | 字段释义 | 字段格式 | 必填 | 备注                                                         |
| ----------- | -------- | -------- | ---- | ------------------------------------------------------------ |
| agreeId     | 协议号   | String   | 否   | code为000000时有值                                           |
| channelCode | 渠道编码 | String   | 否   | 见附录-渠道编码|

<a id="heading-77"></a>
#### 响应码说明

| code值 | code说明 | 备注 |
|--------|----------|------|
| 000000 | 成功     |      |

<a id="sec-3-2-3"></a>
### 3.2.3 代扣协议上传

<a id="heading-79"></a>
#### 接口说明

| 接口描述 | 代扣协议文件上传                   |
|----------|------------------------------------|
| 请求地址 | /pay/api/agreement/pay/file/upload |
| 接口备注 |                                    |

<a id="heading-80"></a>
#### 请求参数

| 字段名称      | 字段释义     | 字段格式 | 必填 | 备注                                           |
|---------------|--------------|----------|------|------------------------------------------------|
| applicationNo | 申请单号     | String   | 是   |                                                |
| ewfile        | 补充代扣协议 | String   | 是   | 通过3.1.1文件上传拿到fileKey后调用此接口       |
| agreeId       | 协议ID       | String   | 否   | 签约确认返回的协议号，渠道为“新浪支付”时，必填 |

<a id="heading-81"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注 |
|----------|----------|----------|------|------|
| code     | 响应码   | String   | 是   |      |
| data     | 响应数据 | Object   | 是   |      |
| message  | 响应描述 | String   | 是   |      |
| success  | 响应结果 | boolean  | 是   |      |

<a id="heading-82"></a>
#### 响应码说明

| code值 | code说明 | 备注 |
|--------|----------|------|
| 000000 | 成功     |      |

<a id="sec-3-2-4"></a>
### 3.2.4 解约

<a id="heading-84"></a>
#### 接口说明

| 接口描述 | 解约                                                         |
| -------- | ------------------------------------------------------------ |
| 请求地址 | /pay/api/card/binding/unbind                                 |
| 接口备注 | 签约渠道为“新浪支付”时，解约需要额外增加回填短信验证码确认。 |

<a id="heading-85"></a>
#### 请求参数

| 字段名称      | 字段释义 | 字段格式 | 必填 | 备注 |
|---------------|----------|----------|------|------|
| cardNo        | 银行卡号 | String   | 是   |      |
| agreeId       | 协议号   | String   | 是   |      |
| applicationNo | 申请单号 | String   | 是   |      |
| riskInfo      | 风控参数 | Object   | 否   |      |

riskInfo 风控参数

| 字段名称 | 字段释义   | 字段格式 | 必填 | 备注                     |
|----------|------------|----------|------|--------------------------|
| clientIp | 当前用户IP | String   | 否   | 渠道为“新浪支付”时，必填 |

<a id="heading-86"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注 |
|----------|----------|----------|------|------|
| code     | 响应码   | String   | 是   |      |
| data     | 响应数据 | Object   | 是   |      |
| message  | 响应描述 | String   | 是   |      |
| success  | 响应结果 | boolean  | 是   |      |

data数据集

| 字段名称    | 字段释义         | 字段格式 | 必填 | 备注                  |
| ----------- | ---------------- | -------- | ---- | --------------------- |
| applyNo     | 解约申请流水     | String   | 否   |                       |
| needConfirm | 是否需要短信验证 | boolean  | 否   | true-需要调用解约确认 |

<a id="heading-87"></a>
#### 响应码说明

| code值 | code说明 | 备注 |
|--------|----------|------|
| 000000 | 成功     |      |

<a id="sec-3-2-5"></a>
### 3.2.5 解约确认

<a id="heading-89"></a>
#### 接口说明

| 接口描述 | 解约确认                             |
|----------|--------------------------------------|
| 请求地址 | /pay/api/card/binding/unbind/confirm |
| 接口备注 |                                      |

<a id="heading-90"></a>
#### 请求参数

| 字段名称 | 字段释义   | 字段格式 | 必填 | 备注 |
|----------|------------|----------|------|------|
| applyNo  | 申请流水   | String   | 是   |      |
| smsCode  | 短信验证码 | String   | 是   |      |
| riskInfo | 风控参数   | Object   | 否   |      |

riskInfo 风控参数

| 字段名称 | 字段释义   | 字段格式 | 必填 | 备注                     |
|----------|------------|----------|------|--------------------------|
| clientIp | 当前用户IP | String   | 否   | 渠道为“新浪支付”时，必填 |

<a id="heading-91"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注 |
|----------|----------|----------|------|------|
| code     | 响应码   | String   | 是   |      |
| data     | 响应数据 | Object   | 是   |      |
| message  | 响应描述 | String   | 是   |      |
| success  | 响应结果 | boolean  | 是   |      |

<a id="heading-92"></a>
#### 响应码说明

| code值 | code说明 | 备注 |
|--------|----------|------|
| 000000 | 成功     |      |

<a id="sec-3-3"></a>
## 3.3 放款


<a id="sec-3-3-1"></a>
### 3.3.1 放款申请

<a id="heading-95"></a>
#### 接口说明

| 接口描述 | 放款申请                       |
| -------- | ------------------------------ |
| 请求地址 | /order/api/loan/confirmPayment |
| 接口备注 |                                |

<a id="heading-96"></a>
#### 请求参数

| 字段  | 描述  | 类型   | 是否必填 | 备注               |
| ---- | ------| ----- | ------ |------------------|
| loanNo | 业务方放款申请唯一编号 | String | 是       |                  |
| applicationNo | 资方进件唯一编号  | String | 是    |                  |
| custSignDate | 客户签约日期      | Date   | 是     |                  |
| factoringContractNo | 保理合同编号 | String | 否 | |
| entrustedAgentContractNo | 委托代理合同编号 | String | 否 | |
| accounts    | 收款账户        | List | 是 | 详见 accounts 数据集  |
| contracts    | 合同套系           | List | 是 | 详见 contracts 数据集 |

accounts 数据集

| 字段  | 描述  | 类型   | 是否必填 | 备注  |
| ---- | ------| ----- | ------ | ----- |
| accType                              | 账户类型               | String | 是       | 1-商户 <br>2-商户指定方 <br>3-个人                           |
| toAccName                            | 收款人姓名或商户名称   | String | 是       |                                                              |
| toAccNo                              | 收款人银行账号         | String | 是       |                                                              |
| toBankCode                           | 收款人银行代码         | String | 是       | 宝付：固定“0000” <br>通联：见[附3：银行代码](#app-3) |
| toBankName                           | 收款人银行名称         | String | 是       |                                                              |
| toProName                            | 收款人开户行省名       | String | 否       | 账户类型商户/商户指定方，必填                                |
| toCityName                           | 收款人开户行市名       | String | 否       | 账户类型商户/商户指定方，必填                                |
| toAccDept                            | 收款人开户行支行名称   | String | 否       | 账户类型商户/商户指定方，必填                                |
| toCnaps                              | 收款人联行号           | String | 否       | 账户类型商户/商户指定方，必填                                |
| toAccountProp                        | 公私标志               | String | 是       | 0私人，1公司                                                 |
| transCardType                        | 银行卡开户证件类型     | String | 否       | 见附录18                                                     |
| transCardId                          | 银行卡开户证件号       | String | 否       |                                                              |
| transMobile                          | 银行卡预留手机号       | String | 否       | 账户类型个人，必填                                           |
| transSummary                         | 打款摘要               | String | 是       | {工作单位}+车辆保费                                          |
| transReserved                        | 打款用途               | String | 否       |                                                              |
| transMoney                           | 打款金额               | Long   | 是       | 单位（分）                                                   |


contracts合同套系数据集

| 字段  | 描述  | 类型   | 是否必填 | 备注  |
| ---- | ------| ----- | ------ | ----- |
| contractType                                                                                | 合同标识     | string | 是 | 信息唯一标识，具体参见补充说明 |
| contractFileKey                                                                             | 合同fileKey  | string | 是 | 信息具体值，具体参见补充说明   |
| contractCn                                                                                  | 合同中文描述 | string | 是 | 唯一标识字段的中文描述         |
| remark                                                                                      | 信息描述     | string | 否 | 信息描述                       |

<a id="heading-97"></a>
#### 响应参数

下面是返回结果中data对象元素：

| 字段              | 描述                   | 类型   | 是否必填 | 备注         |
| ----------------- | ---------------------- | ------ | -------- | ------------ |
| loanNo            | 合作方放款申请唯一编号 | String | 否       |              |
| loanApplicationNo | 放款单号               | String | 否       | 资方放款单号 |

<a id="heading-98"></a>
#### 请求示例
```
{
    "loanNo": "HZF000025_5514939758",
    "endDate": 1660176000000,
    "applicationNo": "JK22031714447289",
    "totalGeexFee": 226,
    "repaymentType": 3,
    "odFeeRate": 0.01,
    "rate": 0.025,
    "model": 1,
    "custSignDate": 1628640000000,
    "applyAmt": 600000,
    "totalServiceFee": 10834,
    "totalGuaranteeFee": 4166,
    "accounts": [
        {
            "toCityName": "天津市",
            "transCardId": "340826198810100001",
            "transSummary": "打款摘要——商户打款摘要",
            "transMobile": "021-80298166",
            "toAccName": "XXXX有限公司",
            "toBankCode": "0105",
            "toAccDept": "中国建设银行股份有限公司天津河东支行",
            "transMoney": 585000,
            "transReserved": "打款用途——商户",
            "toAccNo": "12050162540000002402",
            "toBankName": "中国建设银行",
            "transCardType": "0",
            "toAccountProp": "1",
            "accType": 1,
            "toProName": "天津市",
            "toCnaps": "403588000002"
        }
    ],
    "loanPeriod": 3,
    "startDate": 1628640000000,
    "totalOtherFee": 0,
    "totalXxtFee": 3940
}
```
<a id="heading-99"></a>
#### 响应示例

```
{
    "code": "003101",
    "data": {
        "loanNo": "HZF000025_5514939758",
        "loanApplicationNo": "LN22031714442749"
    },
    "message": "放款审批中",
    "success": false
}
```

<a id="heading-100"></a>
#### 响应码说明

| code值                       | code说明                                                | 备注                               |
|------------------------------|---------------------------------------------------------|------------------------------------|
| 003101                       | 放款审批中                                              | 放款审批中，需调用放款查询接口查询 |
| 003104                       | 放款申请单已存在                                        | 记录已存在，不能重新发起放款申请   |
| 以下错误码为放款申请处理失败 |                                                         |                                    |
| 003105                       | 合作方放款申请唯一编号不能为空                          |                                    |
| 003106                       | 进件唯一编号不能为空                                    |                                    |
| 003107                       | 账户类型不能为空                                        |                                    |
| 003108                       | 账户类型不存在                                          |                                    |
| 003109                       | 收款人姓名或商户名称不能为空                            |                                    |
| 003110                       | 收款人银行账号不能为空                                  |                                    |
| 003111                       | 收款人银行名称不能为空                                  |                                    |
| 003112                       | 账户类型为商户/商户指定方，收款人开户行省名不能为空     |                                    |
| 003113                       | 账户类型为商户/商户指定方，收款人开户行市名不能为空     |                                    |
| 003114                       | 账户类型为商户/商户指定方，收款人开户行支行名称不能为空 |                                    |
| 003115                       | 账户类型为商户/商户指定方，收款人联行号不能为空         |                                    |
| 003116                       | 账户类型为个人，银行卡身份证号码不能为空                |                                    |
| 003117                       | 账户类型为个人，银行卡预留手机号不能为空                |                                    |
| 003118                       | 打款摘要不能为空                                        |                                    |
| 003119                       | 打款用途不能为空                                        |                                    |
| 003120                       | 打款金额不能为空                                        |                                    |
| 003121                       | 贷款期数不能为空                                        |                                    |
| 003122                       | 还款方式不能为空                                        |                                    |
| 003123                       | 利率不能为空                                            |                                    |
| 003124                       | 罚息利率不能为空                                        |                                    |
| 003125                       | 贷款起始日期不能为空                                    |                                    |
| 003126                       | 贷款终止日期不能为空                                    |                                    |
| 003127                       | 模式不能为空                                            |                                    |
| 003128                       | 客户签约日期不能为空                                    |                                    |
| 003129                       | 《分期支付协议》不能为空                                |                                    |
| 003130                       | 《保理合同》不能为空                                    |                                    |
| 003131                       | 《应收账款转让通知书》不能为空                          |                                    |
| 003132                       | 《应收账款转让通知书》回执不能为空                      |                                    |
| 003133                       | 《委托担保服务协议》不能为空                            |                                    |
| 003182                       | 《委托担保服务协议》（母公司）不能为空                  |                                    |
| 003199                       | 其它错误                                                |                                    |


<a id="sec-3-3-2"></a>
### 3.3.2 放款查询

<a id="heading-102"></a>
#### 接口说明

| 接口描述 | 放款查询                           |
| -------- | ---------------------------------- |
| 请求地址 | /order/api/loan/queryTradingStatus |
| 接口备注 |                                    |

<a id="heading-103"></a>
#### 请求参数

| 字段              | 描述                   | 类型   | 是否必填 | 备注 |
| ----------------- | ---------------------- | ------ | -------- | ---- |
| loanNo            | 业务方放款申请唯一编号 | String | 是       |      |
| loanApplicationNo | 资方放款单号           | String | 是       |      |

<a id="heading-104"></a>
#### 响应参数

下面是返回结果中data对象元素：

| 字段                | 描述                               | 类型     | 是否必填 | 备注                 |
|-------------------| ---------------------------------- | -------- | -------- |--------------------|
| loanNo            | 业务方放款申请唯一编号             | String   | 是       |                    |
| loanApplicationNo | 放款单号                           | String   | 是       | 资方放款单号             |
| loanDetail        | 放款明细                           | List     |          | 详见loanDetail数据集                 |
| repaymentPlan     | 还款计划                           | json     | 否       | 详见repaymentPlan数据集 |

loanDetail数据集：

| 字段                | 描述                               | 类型     | 是否必填 | 备注                        |
|-------------------| ---------------------------------- | -------- | -------- |---------------------------|
| fundPartner       | 资金方                             | Int      | 是       | 1-通汇 |
| paymentChannel    | 打款通道                           | Int      | 是       | 1-交行                      |
| toAccName         | 收款人姓名或商户名称               | String   | 是       |                           |
| toAccNo           | 收款人银行账号                     | String   | 是       |                           |
| accType           | 账户类型| Int      | 是       | 1-商户 <br>2-商户指定方<br> 3-个人 |
| bankSerialNo      | 放款银行流水                       | String   | 否       |                           |
| paymentResult     | 打款状态                           | Int      | 是       | 1成功 0失败                   |
| paymentAmt        | 打款金额                           | Long     | 是       | 单位（分）                     |
| paymentTime       | 放款成功时间                       | Datetime | 否       | 如2020-10-28 10:00:05      |
| reason            | 放款失败原因                       | String   | 否       |                           |

repaymentPlan数据集:

| 字段名称              | 字段释义          | 字段格式 | 是否必须 | 备注       |
|-------------------|---------------| -------- | -------- | ---------- |
| totalPrincipal    | 总本金           | Long     | 是       | 单位（分） |
| totalInterest     | 总利息           | Long     | 是       | 单位（分） |
| totalPeriod       | 总期数           | Int      | 是       |            |
| repaySchedules    | 还款计划集合        | List     | 是       |  详见repaySchedules数据集          |

repaySchedules数据集

| 字段名称              | 字段释义          | 字段格式 | 是否必须 | 备注       |
|-------------------|---------------| -------- | -------- | ---------- |
| partnerScheduleNo | 还款计划编号        | String   | 是       |            |
| period            | 当前期数          | Int      | 是       |            |
| repayTotalAmount  | 应还总金额         | Long     | 是       | 单位（分） |
| repayPrincipal    | 应还本金          | Long     | 是       | 单位（分） |
| repayInterest     | 应还利息          | Long     | 是       | 单位（分） |
| repayServiceFee   | 本期应还服务费(资金方)  | Long     | 否       | 单位（分） |
| repayXxtFee       | 本期应还服务费(技术服务方) | Long     | 否       | 单位（分） |
| otherFee          | 其他费用          | Long     | 是       | 单位（分） |
| repayDate         | 本期还款日期        | Date     | 是       |            |
| startDate         | 本期开始日期        | Date     | 是       |            |
| endDate           | 本期结束日期        | Date     | 是       |            |

<a id="heading-105"></a>
#### 请求示例

```
{
    "loanNo": "HZF000025_5514939758",
    "loanApplicationNo": "LN22031714442749"
}
```

<a id="heading-106"></a>
#### 响应示例

```
{
    "code": "000000",
    "data": {
        "loanApplicationNo": "LN22031714442749",
        "loanDetail": [
            {
                "accType": 1,
                "bankSerialNo": "5032022031714442800326747",
                "fundPartner": 2,
                "paymentAmt": 585000,
                "paymentChannel": 1,
                "paymentResult": 1,
                "paymentTime": 1647499469000,
                "reason": "处理成功",
                "toAccName": "XXXX有限公司",
                "toAccNo": "12050162540000002402"
            }
        ],
        "loanNo": "HZF000025_5514939758"
    },
    "message": "放款成功",
    "success": true
}
```

<a id="heading-107"></a>
#### 响应码说明

| code值 | code说明                           | 备注                             |
|--------|------------------------------------|----------------------------------|
| 000000 | 放款成功                           | 放款成功                         |
| 003207 | 放款失败                           | 放款失败                         |
| 003201 | 放款处理中                         | 放款处理中，需要重新发起查询     |
| 003202 | 放款审批拒绝                       | 放款失败                         |
| 003208 | 此进件申请单状态，不能进行放款申请 | 放款失败                         |
| 003204 | 合作方放款申请唯一编号不能为空     | 参数校验不通过，需要重新发起查询 |
| 003205 | 放款单号不能为空                   | 参数校验不通过，需要重新发起查询 |
| 003206 | 放款申请单不存在                   | 放款失败                         |

<a id="sec-3-3-3"></a>
### 3.3.3 放款结果通知

<a id="heading-109"></a>
#### 接口说明

| 接口描述 | 放款结果通知 |
|----------|--------------|
| 请求地址 | 接入商提供   |
| 接口备注 |              |

<a id="heading-110"></a>
#### 请求参数

下面是返回结果中data对象元素：

| 字段                                   | 描述               | 类型     | 是否必填 | 备注                 |
| -------------------------------------- | ------------------ | -------- | -------- |--------------------|
| loanNo                                 | 业务方放款申请编号 | String   | 是       | 唯一                 |
| loanApplicationNo                      | 放款单号           | String   | 是       | 资方放款单号             |
| loanDetail                             | 放款明细           | List     |          | 详见loanDetail数据集                 |
| repaymentPlan                          | 还款计划           | json     | 否       | 详见repaymentPlan数据集|

loanDetail数据集：

| 字段                                   | 描述               | 类型     | 是否必填 | 备注                 |
| -------------------------------------- | ------------------ | -------- | -------- |--------------------|
| fundPartner                            | 资金方             | Int      | 是       | 1-通汇 |
| paymentChannel                         | 打款通道           | Int      | 是       | 1-交行                            |
| toAccName                              | 收款账户名         | String   | 是       |                                   |
| toAccNo                                | 收款人银行账号     | String   | 是       |                                   |
| accType                                | 账户类型           | Int      | 是       | 1-商户<br>2-商户指定方<br> 3-个人 |
| bankSerialNo                           | 放款银行流水       | String   | 否       |                                   |
| paymentResult                          | 打款状态           | Int      | 是       | 1成功 0失败                       |
| paymentAmt                             | 打款金额           | Long     | 是       | 单位（分）                        |
| paymentTime                            | 放款成功时间       | Datetime | 否       | 如2020-10-28 10:00:05             |
| reason                                 | 放款失败原因       | String   | 否       |                                   |

repaymentPlan数据集：

| 字段名称                                                     | 字段释义           | 字段格式 | 是否必须 | 备注       |
| ------------------------------------------------------------ | ------------------ | -------- | -------- | ---------- |
| totalPrincipal                                               | 总本金             | Long     | 是       | 单位（分） |
| totalInterest                                                | 总利息             | Long     | 是       | 单位（分） |
| totalPeriod                                                  | 总期数             | Int      | 是       |            |
| repaySchedules                                               | 还款计划集合       | List     | 是       |  详见repaySchedules数据集          |

repaySchedules数据集

| 字段                                   | 描述               | 类型     | 是否必填 | 备注                 |
| -------------------------------------- | ------------------ | -------- | -------- |--------------------|
| partnerScheduleNo                                            | 还款计划编号       | String   | 是       |            |
| period                                                       | 当前期数           | Int      | 是       |            |
| repayTotalAmount                                             | 应还总金额         | Long     | 否       | 单位（分） |
| repayPrincipal                                               | 应还本金           | Long     | 是       | 单位（分） |
| repayInterest                                                | 应还利息           | Long     | 是       | 单位（分） |
| repayServiceFee                                              | 本期应还资方服务费 | Long     | 否       | 单位（分） |
| repayXxtFee                                                  | 本期应还技术服务费 | Long     | 否       | 单位（分） |
| otherFee                                                     | 其他费用           | Long     | 是       | 单位（分） |
| repayDate                                                    | 本期还款日期       | Date     | 是       |            |
| startDate                                                    | 本期开始日期       | Date     | 是       |            |
| endDate                                                      | 本期结束日期       | Date     | 是       |            |

<a id="code"></a>
#### 请求code说明

| code值 | code说明     | 备注                                                   |
|--------|--------------|--------------------------------------------------------|
| 000000 | 放款成功     | 放款成功                                               |
| 003207 | 放款失败     | 放款失败                                               |
| 003202 | 放款审批拒绝 | 放款失败                                               |
| 003209 | 银行退票     | 放款失败                                               |
| 003210 | 部分成功     | 放款处理中（业务方需将放款成功那笔（商户指定方）退回） |

<a id="heading-112"></a>
#### 响应参数

| 字段    | 描述     | 类型   | 是否必填 | 备注        |
|---------|----------|--------|----------|-------------|
| message | 响应结果 | string | 是       | 默认success |
| code    | 响应码   | string | 是       |             |

<a id="heading-113"></a>
#### 请求示例

```
{
    "code": "000000",
    "data": {
        "loanApplicationNo": "LN22031714442749",
        "loanDetail": [
            {
                "accType": 1,
                "bankSerialNo": "5032022031714442800326747",
                "fundPartner": 2,
                "paymentAmt": 585000,
                "paymentChannel": 1,
                "paymentResult": 1,
                "paymentTime": 1647499469000,
                "reason": "处理成功",
                "toAccName": "XXXX有限公司",
                "toAccNo": "12050162540000002402"
            }
        ],
        "loanNo": "HZF000025_5514939758"
    },
    "message": "放款成功",
    "success": true
}
```
<a id="heading-114"></a>
#### 响应示例

```
{
    "code": "000000",
    "message": "Success"
}
```

<a id="heading-115"></a>
#### 响应码说明

| code值           | code说明 | 备注 |
|------------------|----------|------|
| 000000           | 成功     |      |
| 其它code均为失败 |          |      |

<a id="sec-3-3-4"></a>
### 3.3.4 合同下载

<a id="heading-117"></a>
#### 接口说明

| 接口描述 | 合同下载                                                   |
| -------- | ---------------------------------------------------------- |
| 请求地址 | /order/api/contract/getContractFile                        |
| 接口备注 | 合同查询下载，同步反馈结果（未签署、已签署、合同附件地址） |

<a id="heading-118"></a>
#### 请求参数

| 字段名称          | 字段释义 | 字段格式 | 是否必须 | 备注                               |
| ----------------- | -------- | -------- | -------- | ---------------------------------- |
| applicationNo     | 申请单号 | string   | 是       | 资方申请单唯一标识                 |
| loanApplicationNo | 放款单号 | String   | 否       | 传入该参数则只查询放款申请时的附件 |
| vin               | 车架号   | String   | 否       | 传入该参数则只查询对应车辆相关附件 |
| type              | 文件类型 | String   | 否       | 同文件上传类型，传入则过滤         |

<a id="heading-119"></a>
#### 响应参数

| 字段名称             | 字段释义 | 字段格式 | 是否必须 | 备注                |
| -------------------- | -------- | -------- | -------- | ------------------- |
| data                 | 合同集合 | array    | 是       | 多份合同列表        |
| data中第二层信息如下 |          |          |          |                     |
| name                 | 合同名称 | String   | 是       | 合同名称            |
| vin                  | 车架号   | String   | 否       | 车架号              |
| url                  | 合同URL  | String   | 是       | 合同pdf文件地址     |
| type                 | 合同类型 | String   | 是       | 同文件上传时的类型  |
| status               | 签署状态 | String   | 是       | 0：未签署:1：已签署 |

<a id="heading-120"></a>
#### 请求示例
```
{
    "applicationNo": "APP1234235235235"
}
```
或

```
{

"loanApplicationNo": "XXT10003"

}
```

<a id="heading-121"></a>
#### 响应示例

```
{
    "code": "000000",
    "data": [
        {
            "name": "**合同",
            "url": "**合同.pdf",
            "status": "1"
        }
    ],
    "message": "合同查询成功"
}
```

<a id="heading-122"></a>
#### 响应码说明

| code值 | code说明           | 备注 |
| ------ | ------------------ | ---- |
| 000000 | 合同全部签署成功   |      |
| 003401 | 处理异常           |      |
| 003402 | 申请单号不能都为空 |      |

<a id="sec-3-3-5"></a>
### 3.3.5 还款计划试算

<a id="heading-124"></a>
#### 接口说明

| 接口描述 | 还款计划试算                    |
| -------- | ------------------------------- |
| 请求地址 | /order/api/loan/repayment/query |
| 接口备注 |                                 |

<a id="heading-125"></a>
#### 请求参数

| 字段                 | 描述             | 类型   | 是否必填 | 备注                               |
| -------------------- | ---------------- | ------ | -------- | ---------------------------------- |
| prodCode             | 产品编码         | string | 是       | 资方产品编码                       |
| model                | 模式             | Int    | 是       | 1-商户全额贴息 <br>2-客户付息 <br> |
| applyAmt             | 客户申请金额     | long   | 是       | 单位分                             |
| loanPeriod           | 贷款期数         | Int    | 是       | 例如：6，9，12，18，24             |
| startDate            | 开始日期         | Date   | 是       |                                    |
| loanTrialVehicleList | 车辆还款信息集合 | List   | 否       |                                    |

loanTrialVehicleList数据集

| 字段           | 描述         | 类型 | 是否必填 | 备注                     |
| -------------- | ------------ | ---- | -------- | ------------------------ |
| applyAmount    | 车辆申请金额 | long | 是       |                          |
| vinOtherFeeMap | Map          | Map  | 是       | Key=期数，Value=其它费用 |

<a id="heading-126"></a>
#### 响应参数

下面是返回结果中data对象元素：

| 字段名称       | 字段释义     | 字段格式 | 是否必须 | 备注       |
| -------------- | ------------ | -------- | -------- | ---------- |
| totalPrincipal | 总本金       | Long     | 是       | 单位（分） |
| totalInterest  | 总利息       | Long     | 否       | 单位（分） |
| totalPeriod    | 总期数       | Int      | 是       |            |
| repaySchedules | 还款计划集合 | List     | 是       |            |

repaySchedules 数据集

| 字段名称          | 字段释义     | 字段格式 | 是否必须 | 备注       |
| ----------------- | ------------ | -------- | -------- | ---------- |
| category          | 1-主还款计划 | Int      | 是       |            |
| partnerScheduleNo | 还款计划编号 | String   | 是       |            |
| period            | 当前期数     | Int      | 是       |            |
| repayTotalAmount  | 应还总金额   | Long     | 是       | 单位（分） |
| repayPrincipal    | 应还本金     | Long     | 是       | 单位（分） |
| repayInterest     | 应还利息     | Long     | 否       | 单位（分） |
| otherFee          | 其他费用     | Long     | 是       | 单位（分） |
| repayDate         | 本期还款日期 | Date     | 是       |            |
| startDate         | 本期开始日期 | Date     | 是       |            |
| endDate           | 本期结束日期 | Date     | 是       |            |

<a id="heading-127"></a>
#### 请求示例

```
{
    "prodCode": "PRD-JBR-A001",
    "model": 2,
    "applyAmt": 1000000,
    "loanPeriod": 3,
    "startDate": "2024-01-19",
    "loanTrialVehicleList": [
        {
            "applyAmount": 7141,
            "vinOtherFeeMap": {
                "1": 300,
                "2": 300
            }
        },
        {
            "applyAmount": 3860,
            "vinOtherFeeMap": {
                "1": 300,
                "2": 300
            }
        }
    ]
}
```

<a id="heading-128"></a>
#### 响应示例

```
{
    "code": "000000",
    "message": "Success",
    "data": {
        "totalPrincipal": 1800000,
        "totalInterest": null,
        "totalPeriod": 9,
        "repaySchedules": [
            {
                "category": 1,
                "partnerScheduleNo": "20240521161917248_1",
                "period": 1,
                "repayTotalAmount": 360000,
                "repayPrincipal": 300000,
                "repayInterest": null,
                "repayDate": "2024-02-04",
                "startDate": "2024-01-19",
                "endDate": "2024-02-04"
            },
            {
                "category": 1,
                "partnerScheduleNo": "20240521161917248_2",
                "period": 2,
                "repayTotalAmount": 360000,
                "repayPrincipal": 300000,
                "repayInterest": null,
                "repayDate": "2024-03-04",
                "startDate": "2024-02-05",
                "endDate": "2024-03-04"
            },
            {
                "category": 1,
                "partnerScheduleNo": "20240521161917248_3",
                "period": 3,
                "repayTotalAmount": 300000,
                "repayPrincipal": 300000,
                "repayInterest": null,
                "repayDate": "2024-04-04",
                "startDate": "2024-03-05",
                "endDate": "2024-04-04"
            }
        ]
    },
    "success": true
}
```

<a id="heading-129"></a>
#### 响应码说明

| code值 | code说明     | 备注 |
|--------|--------------|------|
| 000000 | 成功         |      |
| 003199 | 具体错误提示 |      |
|        |              |      |


<a id="sec-3-3-6"></a>
### 3.3.6 放款凭证下载

<a id="heading-131"></a>
#### 接口说明

| 接口描述 | 放款申请                             |
| -------- | ------------------------------------ |
| 请求地址 | /pay/api/download/loan/voucher       |
| 接口备注 | 如果返回data数据为空，则凭证还未生成 |

<a id="heading-132"></a>
#### 请求参数

| 字段名称          | 字段释义     | 字段格式 | 必填 | 备注 |
|-------------------|--------------|----------|------|------|
| loanApplicationNo | 放款申请单号 | String   | 是   |      |

<a id="heading-133"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式      | 必填 | 备注 |
|----------|----------|---------------|------|------|
| code     | 响应码   | String        | 是   |      |
| data     | 响应数据 | Array[Object] | 否   |      |
| message  | 响应描述 | String        | 是   |      |
| success  | 响应结果 | boolean       | 是   |      |

data数据集：

| 字段              | 描述         | 类型   | 是否必填 | 备注   |
|-------------------|--------------|--------|----------|--------|
| loanApplicationNo | 放款申请单号 | String | 是       |        |
| acctNo            | 收款账号     | String | 是       |        |
| acctName          | 收款账户     | String | 是       |        |
| transAmt          | 放款金额     | Long   | 是       | 单位分 |
| fileUrl           | 凭证文件地址 | String | 是       |        |

<a id="sec-3-4"></a>
## 3.4 还款


<a id="sec-3-4-1"></a>
### 3.4.1 还款试算

<a id="heading-136"></a>
#### 接口说明

| 接口描述 | 还款试算                                             |
| -------- | ---------------------------------------------------- |
| 请求地址 | /saps/api/trial/calc                                 |
| 接口备注 | 1. 代偿回购后不支持提前结清试算 <br>2.不支持跳期试算 |

<a id="heading-137"></a>
#### 请求参数

| 字段           | 描述         | 类型    | 是否必填 | 备注                               |
| -------------- | ------------ | ------- | -------- | ---------------------------------- |
| applicationNo  | 进件单号     | String  | 是       |                                    |
| settlementMode | 结算方式     | Integer | 是       | 100-账单还款 <br>201-提前结清 <br> |
| parterBillList | 还款计划集合 | List    | 否       |                                    |

partnerBillList数据集

| 字段                  | 描述           | 类型    | 是否必填 | 备注                                                  |
| --------------------- | -------------- | ------- | -------- | ----------------------------------------------------- |
| partnerScheduleNo     | 还款计划编号   | String  | 是       |                                                       |
| overdueFee            | 滞纳金         | Long    | 否       | 如有则传                                              |
| advanceFee            | 提前结清手续费 | Long    | 否       | 如有则传                                              |

<a id="heading-138"></a>
#### 响应参数

| 字段              | 描述         | 类型   | 是否必填 | 备注     |
| ----------------- | ------------ | ------ | -------- | -------- |
| applicationNo     | 进件单号     | String | 是       |          |
| period            | 当前期数     | int    | 是       |          |
| partnerScheduleNo | 还款计划编号 | String | 是       |          |
| payableAmount       | 应还总额     | Long   | 是       | 单位分   |
| startDate         | 起息日       | Date   | 是       |          |
| billFeeDetailList | 科目费用明细 | Aarray | 是       |          |

billFeeDetailList数据集

| 字段              | 描述         | 类型   | 是否必填 | 备注     |
| --------------- | ------------ | ------ | -------- | -------- |
| subjectNo       | 科目编号     | String | 是       |          |
| subjectName   | 科目名称     | String | 是       |          |
| feeAmount     | 费用金额     | Long   | 是       | 单位为分 |


<a id="sec-3-4-2"></a>
### 3.4.2 支付结算申请

<a id="heading-140"></a>
#### 接口说明

| 接口描述 | 支付结算申请           |
| -------- | ---------------------- |
| 请求地址 | /saps/api/settle/apply |
| 接口备注 |                        |

<a id="heading-141"></a>
#### 请求参数

| 字段名称       | 字段释义         | 字段格式 | 必填 | 备注                                    |
| -------------- | ---------------- | -------- | ---- | --------------------------------------- |
| payNo          | 业务方支付流水号 | String   | 是   | 业务方唯一                              |
| cserialNo      | 渠道流水         | String   | 否   | 线下支付为银行流水                      |
| channel        | 支付通道名称     | String   | 否   | 线上支付见附录4<br>线下取渠道名称，必填 |
| transAmount    | 交易金额         | long     | 是   | 单位分                                  |
| agreeId        | 协议号           | String   | 否   | 线上支付必传                            |
| acctNo         | 账号             | String   | 否   |                                         |
| acctName       | 账户名           | String   | 否   |                                         |
| transType      | 交易类型         | int      | 是   | 默认1-还款                              |
| paymentMode    | 支付方式         | Int      | 是   | 见附录 支付方式                         |
| settlementMode | 结算方式         | Integer  | 是   | 100-账单还款<br>201-提前结清            |
| orderDesc      | 订单摘要         | String   | 是   |                                         |
| applicationNo  | 申请单号         | String   | 是   |                                         |
| goodsList      | 商品清单         | List     | 否   |                                         |

goodsList数据集：

| 字段名称         | 字段释义       | 字段格式 | 必填 | 备注                                                         |
| ---------------- | -------------- | -------- | ---- | ------------------------------------------------------------ |
| goodsId          | 商品唯一标识   | String   | 是   | 还款计划编号                                                 |
| amount           | 总金额         | long     | 是   | 单位分                                                       |
| overdueFee       | 逾期费         | long     | 否   | 本次支付逾期费 单位分                                        |
| overdueDay       | 逾期天数       | Int      | 否   | 账单当前逾期天数                                             |
| settlementStatus | 账单结算状态   | int      | 否   | 见附录-结算状态 <br>本次支付申请可以结清账单<br>则传1-已结清，否则空 |
| settlementTime   | 结算时间       | DateTime | 是   | 线上为支付申请时间 <br>线下为实际收款时间                    |
| advanceFee       | 提前结清手续费 | Long     | 否   | 单位分                                                       |

<a id="heading-142"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注 |
|----------|----------|----------|------|------|
| code     | 响应码   | String   | 是   |      |
| data     | 响应数据 | Object   | 是   |      |
| message  | 响应描述 | String   | 是   |      |
| success  | 响应结果 | boolean  | 是   |      |

data数据集：

| 字段名称        | 字段释义         | 字段格式 | 必填 | 备注            |
| --------------- | ---------------- | -------- | ---- | --------------- |
| serialNo        | 资方支付流水号   | String   | 是   |                 |
| payNo           | 商户订单号       | String   | 是   |                 |
| channelSerialNo | 三方支付渠道流水 | String   | 否   |                 |
| status          | 支付结果         | int      | 是   | 见附录-支付状态 |
| failMsg         | 失败原因         | String   | O    |                 |
| finTime         | 交易完成时间     | time     | 是   |                 |

<a id="sec-3-4-3"></a>
### 3.4.3 支付结算结果查询

<a id="heading-144"></a>
#### 接口说明

| 接口描述 | 支付结果查询                  |
| -------- | ----------------------------- |
| 请求地址 | /pay/api/query/payment/result |
| 接口备注 |                               |

<a id="heading-145"></a>
#### 请求参数

| 字段名称 | 字段释义         | 字段格式 | 必填 | 备注                           |
| -------- | ---------------- | -------- | ---- | ------------------------------ |
| serialNo | 支付流水号       | String   | 是   | 和业务方支付流水号不可同时为空 |
| payNo    | 业务方支付流水号 | String   | 是   | 和支付流水号不可同时为空       |

<a id="heading-146"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注 |
|----------|----------|----------|------|------|
| code     | 响应码   | String   | 是   |      |
| data     | 响应数据 | Object   | 是   |      |
| message  | 响应描述 | String   | 是   |      |
| success  | 响应结果 | boolean  | 是   |      |

data数据集：

| 字段名称          | 字段释义         | 字段格式 | 必填 | 备注            |
| ----------------- | ---------------- | -------- | ---- | --------------- |
| payNo             | 业务方支付流水号 | String   | 是   |                 |
| serialNo          | 资方支付流水号   | String   | 是   |                 |
| channelSerialNo   | 三方支付渠道流水 | String   | 否   |                 |
| status            | 支付结果         | int      | 是   | 见附录 支付状态 |
| failMsg           | 失败原因         | String   | 否   |                 |
| transAmount       | 交易金额         | long     | 是   | 单位分          |
| fee               | 交易手续费       | long     | 是   | 单位分          |
| finTime           | 交易完成时间     | time     | 是   |                 |
| applicationNo     | 进件单号         | String   | 否   |                 |
| loanApplicationNo | 放款单号         | String   | 否   |                 |
| channelCode       | 渠道编号         | String   | 否   |                 |
| asinfoList        | 分账信息         | List     | 否   |                 |
| goodsList         | 商品信息         | List     | 否   |                 |

asinfoList分账信息数据集：

| 字段名称      | 字段释义     | 字段格式 | 必填 | 备注     |
|---------------|--------------|----------|------|----------|
| bizUserid     | 用户标识     | String   | 是   |          |
| astype        | 分账方式     | Int      | 是   | 1-按金额 |
| amount        | 分账金额     | Long     | 是   | 单位为分 |
| arriveAmount  | 实际到账金额 | Long     | 是   | 单位为分 |
| fee           | 支付手续费   | Long     | 是   | 单位为分 |
| remark        | 备注         | String   | 否   |          |
| asinfo        | 分账信息     | List     | 否   |          |

goodsList商品信息数据集：

| 字段名称         | 字段释义 | 字段格式 | 必填 | 备注                      |
|------------------|----------|----------|------|---------------------------|
| goodsId          | 商品ID   | String   | 是   | 还款计划编号              |
| amount           | 结算金额 | Int      | 是   | 单位分                    |
| period           | 期数     | Int      | 是   |                           |
| billStatus       | 账单状态 | Int      | 是   | 见附录 账单状态           |
| settlementStatus | 结算状态 | Int      | 是   | 见附录 结算状态           |
| settlementTime   | 结清时间 | String   | 否   | 格式：yyyy-MM-dd HH:mm:ss |

<a id="heading-147"></a>
#### 响应码说明

| code值 | code说明 | 备注 |
|--------|----------|------|
| 000000 | 成功     |      |

<a id="sec-3-4-4"></a>
### 3.4.4 支付结算结果通知

<a id="heading-149"></a>
#### 接口说明

| 接口描述 | 支付结果通知   |
| -------- | -------------- |
| 请求地址 | 业务接入方提供 |
| 接口备注 |                |

<a id="heading-150"></a>
#### 请求参数

| 字段名称          | 字段释义         | 字段格式 | 必填 | 备注            |
| ----------------- | ---------------- | -------- | ---- | --------------- |
| payNo             | 业务方支付流水号 | String   | 是   |                 |
| serialNo          | 资方支付流水号   | String   | 是   |                 |
| channelSerialNo   | 三方支付渠道流水 | String   | 否   |                 |
| status            | 支付结果         | int      | 是   | 见附录 支付状态 |
| failMsg           | 失败原因         | String   | 否   |                 |
| transAmount       | 交易金额         | long     | 是   | 单位分          |
| fee               | 交易手续费       | long     | 是   | 单位分          |
| finTime           | 交易完成时间     | time     | 是   |                 |
| applicationNo     | 进件单号         | String   | 否   |                 |
| loanApplicationNo | 放款单号         | String   | 否   |                 |
| channelCode       | 渠道编号         | String   | 否   |                 |
| asinfoList        | 分账信息         | List     | 否   |                 |
| goodsList         | 商品信息         | List     | 否   |                 |

asinfoList分账信息数据集：

| 字段名称         | 字段释义     | 字段格式 | 必填 | 备注     |
|--------------|--------------|----------|------|----------|
| bizUserid    | 用户标识     | String   | 是   |          |
| astype       | 分账方式     | Int      | 是   | 1-按金额 |
| amount       | 分账金额     | Long     | 是   | 单位为分 |
| arriveAmount | 实际到账金额 | Long     | 是   | 单位为分 |
| fee          | 支付手续费   | Long     | 是   | 单位为分 |
| remark       | 备注         | String   | 否   |          |
| asinfo       | 分账信息     | List     | 否   |          |

goodsList商品信息数据集：

| 字段名称         | 字段释义 | 字段格式 | 必填 | 备注                      |
|------------------|----------|----------|------|---------------------------|
| goodsId          | 商品ID   | String   | 是   | 还款计划编号              |
| amount           | 结算金额 | Int      | 是   | 单位分                    |
| period           | 期数     | Int      | 是   |                           |
| billStatus       | 账单状态 | Int      | 是   | 见附录 账单状态           |
| settlementStatus | 结算状态 | Int      | 是   | 见附录 结算状态           |
| settlementTime   | 结清时间 | String   | 否   | 格式：yyyy-MM-dd HH:mm:ss |

<a id="heading-151"></a>
#### 响应参数

| 字段名称   | 字段释义 | 字段格式 | 必填 | 备注                  |
|------------|----------|----------|------|-----------------------|
| syncStatus | 通知结果 | int      | 是   | 0-处理失败 1-处理成功 |

<a id="sec-3-4-5"></a>
### 3.4.5 退款

<a id="heading-153"></a>
#### 接口说明

| 接口描述 | 退款                             |
|----------|----------------------------------|
| 请求地址 | /saps/api/refund                 |
| 接口备注 |  |

<a id="heading-154"></a>
#### 请求参数

| 字段           | 描述               | 类型   | 是否必填 | 备注                    |
|----------------|--------------------|--------|----------|-------------------------|
| applicationNo  | 进件单号           | String | 是       |                         |
| settlementMode | 结算方式           | int    | 是       | 见附录-结算方式编码 4XX |
| payNo          | 合作方退费申请流水 | String | 是       |                         |

<a id="heading-155"></a>
#### 响应参数

| 字段     | 描述               | 类型   | 是否必填 | 备注 |
|----------|--------------------|--------|----------|------|
| serialNo | 平台退款流水       | String | 是       |      |
| payNo    | 合作方退款申请流水 | String | 是       |      |


<a id="sec-3-4-6"></a>
### 3.4.6 还款计划查询

<a id="heading-157"></a>
#### 接口说明

| 接口描述 | 还款计划查询                   |
| -------- | ------------------------------ |
| 请求地址 | /saps/api/repayment/plan/query |
| 接口备注 |                                |

<a id="heading-158"></a>
#### 请求参数

| 字段名称          | 字段释义     | 字段格式 | 必填 | 备注                 |
| ----------------- | ------------ | -------- | ---- | -------------------- |
| applicationNo     | 进件单号     | String   | 否   | 查询参数不可同时为空 |
| partnerScheduleNo | 还款计划编号 | String   | 否   | 查询参数不可同时为空 |

<a id="heading-159"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注 |
|----------|----------|----------|------|------|
| code     | 响应码   | String   | 是   |      |
| data     | 响应数据 | Object   | 是   |      |
| message  | 响应描述 | String   | 是   |      |
| success  | 响应结果 | boolean  | 是   |      |

data数据集：

| 字段名称                     | 字段释义   | 字段格式 | 必填 | 备注            |
|--------------------------|--------| -------- | ---- | --------------- |
| applicationNo            | 进件单号   | String   | 是   |                 |
| loanApplicationNo        | 放款单号   | String   | 是   |                 |
| billNo                   | 账单编号   | String   | 是   |                 |
| totalPeriod              | 总期数    | int      | 是   |                 |
| partnerScheduleNo        | 还款计划编号 | String   | 是   |                 |
| customerName             | 客户名称   | String   | 是   |                 |
| period                   | 当前期数   | Int      | 是   |                 |
| totalAmount              | 应还总金额  | Long     | 是   | 单位为分        |
| paidAmount               | 已还总金额  | Long     | 是   | 单位为分        |
| unpaidAmount             | 未还总金额  | Long     | 是   | 单位为分        |
| billStatus               | 账单状态   | int      | 是   | 见附录 账单状态 |
| customerSettlementStatus | 客户结算状态 | int      | 是   | 见附录 结算状态 |
| customerSettlementTime   | 客户结算时间 | time     | 是   |                 |
| settlementStatus         | 结算状态   | int      | 是   | 见附录 结算状态 |
| settlementTime           | 结算时间   | time     | 是   |                 |
| startDate                | 起息日    | time     | 是   |                 |
| lastPayDate              | 最晚还款日  | time     | 是   |                 |
| lastGraceDate            | 宽限期到期日 | time     | 是   |                 |
| feeDetailList            | 账单科目详情 | List     | 是   |                 |
| payDetailList            | 支付详情   | List     | 否   |                 |

feeDetailList账单科目数据集：

| 字段名称           | 字段释义 | 字段格式 | 必填 | 备注   |
| ------------------ |------| -------- | ---- | ------ |
| subjectNo        | 科目编码 | String   | 是   | 见附录 账单科目编码 |
| subjectName | 科目名称 | String   | 是   |        |
| totalAmount            | 应付总额 | long   | 是   | 单位分       |
| unpaidAmount        | 未付总额 | long     | 是   | 单位分 |
| paidAmount                | 已付总额 | long     | 是   | 单位分 |

payDetailList支付详情数据集：

| 字段名称           | 字段释义         | 字段格式 | 必填 | 备注            |
| ------------------ | ---------------- | -------- | ---- | --------------- |
| paySerialNo        | 资方支付流水号   | String   | 是   |                 |
| payChannelSerialNo | 三方支付渠道流水 | String   | 否   |                 |
| channel            | 支付渠道         | String   | 否   | 见附录 渠道编码 |
| transAmount        | 交易金额         | long     | 是   | 单位分          |
| fee                | 交易手续费       | long     | 否   | 单位分          |
| finTime            | 交易完成时间     | time     | 是   |                 |
| paySubjectList     | 支付科目详情     | List     | 是   |                 |

paySubjectList支付科目数据集：

| 字段名称           | 字段释义 | 字段格式 | 必填 | 备注   |
| ------------------ |------| -------- | ---- | ------ |
| subjectNo        | 科目编码 | String   | 是   | 见附录 账单科目编码 |
| subjectName | 科目名称 | String   | 是   |        |
| paidAmount                | 已付总额 | long     | 是   | 单位分 |

<a id="heading-160"></a>
#### 响应码说明

| code值 | code说明 | 备注 |
|--------|----------|------|
| 000000 | 成功     |      |


<a id="sec-3-4-7"></a>
### 3.4.7 对账单通知

<a id="heading-162"></a>
#### 接口说明

| 接口描述 | 对账单通知 |
|----------|------------|
| 请求地址 | 接入商提供 |
| 接口备注 |            |

<a id="heading-163"></a>
#### 请求参数

| 字段名称       | 字段释义   | 字段格式 | 必填 | 备注                             |
| -------------- | ---------- | -------- | ---- | -------------------------------- |
| cnNo           | 对账单流水 | String   | 是   |                                  |
| totalAmount    | 总金额     | long     | 是   | 单位分                           |
| totalRedAmount | 红冲总金额 | long     | 否   | 单位分                           |
| bizType        | 对账类型   | int      | 是   | 见附录-对账单类型                |
| settlementUnit | 结算单位   | String   | 是   | 1、平台运营方 <br>2、资金方 <br> |
| createDate     | 生成日期   | Date     | 是   |                                  |
| accountDate    | 对账日期   | Date     | 是   |                                  |
| settlementDate | 结算日期   | Date     | 是   | 对账单最后付款日                 |
| repayDetails   | 对账单明细 | List     | 是   |                                  |

repayDetails 数据集：

| 字段名称          | 字段释义     | 字段各是 | 必填 | 备注 |
| ----------------- | ------------ | -------- | ---- | ---- |
| applicationNo     | 申请单号     | String   | 是   |      |
| partnerScheduleNo | 还款计划编号 | String   | 是   |      |
| period            | 当前期数     | int      | 是   |      |
| repayDate         | 还款时间     | Date     | 是   |      |
| billFeeDetailList | 科目费用明细 | List     | 是   |      |

feeDetails数据集：

| 字段名称     | 字段释义     | 字段格式 | 必填 | 备注                  |
| ------------ | ------------ | -------- | ---- | --------------------- |
| bizNo        | 业务单号     | String   | 否   |                       |
| sourceAmount | 业务发生金额 | long     | 否   |                       |
| subjectNo    | 科目编号     | String   | 是   | 见附录 对账单科目编码 |
| subjectName  | 科目名称     | String   | 是   |                       |
| feeAmount    | 科目金额     | long     | 是   | 单位为分              |
| redFeeAmount | 科目红冲金额 | long     | 否   | 单位为分              |
| remark       | 备注         | String   | 否   |                       |

<a id="heading-164"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注                 |
|----------|----------|----------|------|----------------------|
| status   | 接收结果 | boolean  | 是   | true-成功 false-失败 |

<a id="sec-3-4-8"></a>
### 3.4.8 对账单结算通知

<a id="heading-166"></a>
#### 接口说明

| 接口描述 | 对账单结算通知                                               |
| -------- | ------------------------------------------------------------ |
| 请求地址 | /saps/api/soa/settle                                         |
| 接口备注 | 合作方收到对账单通知核对无误并打款后<br>通过调用接口更新账对账单结算信息，完成对账单结算流程 |

<a id="heading-167"></a>
#### 请求参数

| 字段名称       | 字段释义   | 字段格式 | 必填 | 备注   |
|----------------|------------|----------|------|--------|
| cnNo           | 对账单流水 | String   | 是   |        |
| transferAmount | 打款金额   | long     | 是   | 单位分 |
| bankSerialNo   | 银行流水   | String   | 是   |        |
| payDate        | 打款时间   | Date     | 是   |        |


<a id="heading-168"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注 |
|----------|----------|----------|------|------|
| code     | 响应码   | String   | 是   |      |
| data     | 响应数据 | Object   | 是   |      |
| message  | 响应描述 | String   | 是   |      |
| success  | 响应结果 | boolean  | 是   |      |

<a id="heading-169"></a>
#### 响应码说明

| code值 | code说明 | 备注 |
|--------|----------|------|
| 000000 | 成功     |      |


<a id="sec-3-5"></a>
## 3.5 退票

<a id="sec-3-5-1"></a>
### 3.5.1 退票通知

<a id="heading-172"></a>
#### 接口说明

| 接口描述 | 退票通知   |
|----------|------------|
| 请求地址 | 接入商提供 |
| 接口备注 |            |

<a id="heading-173"></a>
#### 请求参数

| 字段名称       | 字段释义   | 字段格式 | 必填 | 备注      |
|----------------|------------|----------|------|-----------|
| cnNo           | 对账单流水 | String   | 是   |           |
| count          | 退票笔数   | int      | 是   |           |
| totalAmount    | 总金额     | long     | 是   | 单位分    |
| bizType        | 对账类型   | int      | 是   | 6000-退票 |
| createDate     | 生成日期   | Date     | 是   |           |
| accountDate    | 对账日期   | Date     | 是   |           |
| settlementDate | 结算日期   | Date     | 是   |           |
| refundDetails  | 退票明细   | List     | 是   |           |

refundDetails 数据集：

| refundNo          | 退票流水             | String   | 是 |          |
|-------------------|----------------------|----------|----|----------|
| loanApplicationNo | 放款单号             | String   | 是 |          |
| applicationNo     | 申请单号             | String   | 是 |          |
| transTime         | 退票时间             | DateTime | 是 |          |
| refundAmount      | 退票金额             | long     | 是 | 单位为分 |
| toAccName         | 收款人姓名或商户名称 | String   | 是 |          |
| toAccNo           | 收款人银行账号       | String   | 是 |          |
| toBankName        | 收款人银行名称       | String   | 是 |          |

<a id="heading-174"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注                 |
|----------|----------|----------|------|----------------------|
| status   | 接收结果 | boolean  | 是   | true-成功 false-失败 |

<a id="sec-3-5-2"></a>
### 3.5.2 退票结算通知

<a id="heading-176"></a>
#### 接口说明

| 接口描述 | 对账单结算通知          |
|----------|-------------------------|
| 请求地址 | /saps/api/refund/settle |
| 接口备注 |                         |

<a id="heading-177"></a>
#### 请求参数

| 字段名称       | 字段释义   | 字段格式 | 必填 | 备注   |
|----------------|------------|----------|------|--------|
| cnNo           | 对账单流水 | String   | 是   |        |
| transferAmount | 打款金额   | long     | 是   | 单位分 |
| bankSerialNo   | 银行流水   | String   | 是   |        |
| payDate        | 打款时间   | Date     | 是   |        |
| repayDetails   | 回款明细   | List     | 否   |        |

repayDetails 数据集：

| 字段名称          | 字段释义               | 字段格式 | 必填 | 备注     |
|-------------------|------------------------|----------|------|----------|
| refundNo          | 退票流水               | String   | 是   |          |
| loanApplicationNo | 放款单号               | String   | 是   |          |
| loanNo            | 合作方放款申请唯一编号 | String   | 是   |          |
| applicationNo     | 申请单号               | String   | 是   |          |
| refundAmount      | 退票金额               | long     | 是   | 单位为分 |

<a id="heading-178"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注 |
|----------|----------|----------|------|------|
| code     | 响应码   | String   | 是   |      |
| data     | 响应数据 | Object   | 是   |      |
| message  | 响应描述 | String   | 是   |      |
| success  | 响应结果 | boolean  | 是   |      |

<a id="heading-179"></a>
#### 响应码说明

| code值 | code说明 | 备注 |
|--------|----------|------|
| 000000 | 成功     |      |



<a id="sec-3-6"></a>
## 3.6 退保

<a id="sec-3-6-1"></a>
### 3.6.1 申请退保

<a id="heading-182"></a>
#### 接口说明

| 接口描述 | 申请退保                             |
| -------- | ------------------------------------ |
| 请求地址 | /order/api/insuranceWithdrawal/apply |
| 接口备注 | 合作方申请退保                       |

<a id="heading-183"></a>
#### 请求参数

| 字段名称          | 字段释义           | 字段格式 | 必填 | 备注                                        |
| ----------------- | ------------------ | -------- | ---- | ------------------------------------------- |
| partnerNo         | 合作方退保业务单号 | String   | 是   |                                             |
| applicationNo     | 进件单号           | String   | 是   |                                             |
| vin               | 车架号             | String   | 是   |                                             |
| type              | 保险类型           | String   | 是   | 见附录19                                    |
| attachmentFileKey | 退保单附件fileKey  | String   | 是   |                                             |
| mode              | 签章模式           | String   | 是   | 枚举如下：<br/>ONLINE=线上<br/>OFFLINE=线下 |
| partnerNotifyUrl  | 合作方通知地址     | String   | 是   |                                             |

<a id="heading-184"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注                             |
| -------- | -------- | -------- | ---- | -------------------------------- |
| code     | 响应码   | String   | 是   | 000000表示成功，非000000表示异常 |
| data     | 响应数据 | Object   | 是   |                                  |
| message  | 响应描述 | String   | 是   |                                  |
| success  | 响应结果 | boolean  | 是   |                                  |

 data参数说明

| 字段名称 | 字段释义   | 字段格式 | 必填 | 备注 |
| -------- | ---------- | -------- | ---- | ---- |
| seqNo    | 退保流水号 | String   | 是   |      |

<a id="heading-185"></a>
#### 请求示例

```
{
    "partnerNo":"pa1008",
    "partnerNotifyUrl":"http://localhost:8888/test/notifyUrl",
    "applicationNo":"A0000001",
    "vin":"VIN1008",
    "type":"COMPULSORY_TRAFFIC_INSURANCE",
    "attachmentFileKey":"urewuy837787767678hh",
    "mode":"ONLINE"
}
```

<a id="heading-186"></a>
#### 响应示例

```
{
    "code": "000000",
    "message": "Success",
    "data": {
        "seqNo": "IW024051309323949"
    },
    "success": true
}
```



<a id="sec-3-6-2"></a>
### 3.6.2 退保结果查询

<a id="heading-188"></a>
#### 接口说明

| 接口描述 | 退保结果查询                                                 |
| -------- | ------------------------------------------------------------ |
| 请求地址 | /order/api/insuranceWithdrawal/query                         |
| 接口备注 | 1.合作方查询退保结果<br>2.seqNo和partnerNo不可同时为空，同时存在时优先以seqNo查询 |

<a id="heading-189"></a>
#### 请求参数

| 字段名称  | 字段释义   | 字段格式 | 必填 | 备注 |
| --------- | ---------- | -------- | ---- | ---- |
| seqNo     | 退保流水号 | String   | 否   |      |
| partnerNo | 合作方编号 | String   | 否   |      |

<a id="heading-190"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注                             |
| -------- | -------- | -------- | ---- | -------------------------------- |
| code     | 响应码   | String   | 是   | 000000表示成功，非000000表示异常 |
| data     | 响应数据 | Object   | 是   |                                  |
| message  | 响应描述 | String   | 是   |                                  |
| success  | 响应结果 | boolean  | 是   |                                  |

 data参数说明

| 字段名称                  | 字段释义      | 字段格式   | 必填 | 备注                                                          |
|-----------------------|-----------|--------|----|-------------------------------------------------------------|
| seqNo                 | 退保流水号     | String | 是  |                                                             |
| partnerNo             | 合作方退保业务单号 | String | 是  |                                                             |
| applicationNo         | 进件单号      | String | 是  |                                                             |
| vin                   | 车架号       | String | 是  |                                                             |
| type                  | 保险类型      | String | 是  | 见附录19                                                       |
| attachmentOriginUrl   | 退保单附件地址   | String | 是  |                                                             |
| mode                  | 签章模式      | String | 是  | 枚举如下：<br/>ONLINE=线上<br/>OFFLINE=线下                          |
| signStatus            | 签章状态      | String | 是  | 枚举如下：<br>SIGNED=已签署 <br>UNSIGNED=未签署                        |
| attachmentSignUrl     | 已盖章退保单附件  | String | 否  | 签章情况下返回下载地址<br>未签章为空                                        |
| paymentSeqNo          | 保司退保打款流水  | String | 否  |                                                             |
| paymentTime           | 保司退保打款时间  | Date   | 否  |                                                             |
| paymentAmount         | 保司退保打款金额  | Long   | 否  | 单位：分                                                        |
| funderPrincipalAmount | 资方应收本金    | Long   | 否  | 单位：分                                                        |
| funderRefundAmount    | 服务费退回     | Long   | 否  | 单位：分                                                        |
| partnerReceiveAmount  | 合作方应收金额   | Long   | 否  | 单位：分                                                        |
| status                | 业务办理状态    | String     | 是 | 10=审核中<br/>20=审核通过<br/>21=审核拒绝<br/>30=退保失败<br/>31=退保成功<br/> |

<a id="heading-191"></a>
#### 请求示例

```
{
    "seqNo":"IW024051018129761"
}
```

<a id="heading-192"></a>
#### 响应示例

```
{
    "code": "000000",
    "message": "Success",
    "data": {
        "seqNo": "IW024051018129761",
        "partnerNo": "pa1008",
        "applicationNo": "A1374878545111",
        "vin": "VIN1008",
        "type": "COMPULSORY_TRAFFIC_INSURANCE",
        "attachmentOriginUrl": "https://www.test.com/12abc",
        "mode": "ONLINE",
        "signStatus": "SIGNED",
        "attachmentSignUrl": "https://www.test.com/abc",
        "paymentSeqNo": "P136734673u9u8u",
        "paymentTime": "2024-05-11 13:23:01",
        "paymentAmount": 1000,
        "funderPrincipalAmount": 1000,
        "funderRefundAmount": 1000,
        "partnerReceiveAmount": 1000
    },
    "success": true
}
```



<a id="sec-3-6-3"></a>
### 3.6.3   合作方退保通知

<a id="heading-194"></a>
#### 接口说明

| 接口描述 | 合作方退保通知                               |
| -------- | -------------------------------------------- |
| 请求地址 | 申请退保时，partnerNotifyUrl字段填充通知地址 |
| 接口备注 | 退保签章，退保结果，通知给合作方             |

<a id="heading-195"></a>
#### 请求参数

| 字段名称   | 字段释义     | 字段格式 | 必填 | 备注                                                    |
| ---------- | ------------ | -------- | ---- | ------------------------------------------------------- |
| noticeType | 通知消息类型 | String   | 是   | 枚举：<br/>SIGN=退保签章通知<br/>TB_RESULT=退保结果通知 |
| data       | 通知消息     | object   | 是   | 消息内容                                                |

data消息内容参数

| 字段名称              | 字段释义             | 字段格式 | 必填 | 备注                                            |
| --------------------- | -------------------- | -------- | ---- | ----------------------------------------------- |
| seqNo                 | 退保流水号           | String   | 是   |                                                 |
| partnerNo             | 合作方退保业务单号   | String   | 是   |                                                 |
| applicationNo         | 进件单号             | String   | 是   |                                                 |
| vin                   | 车架号               | String   | 是   |                                                 |
| type                  | 保险类型             | String   | 是   | 见附录19                                        |
| attachmentOriginUrl   | 退保单附件地址       | String   | 是   |                                                 |
| mode                  | 签章模式             | String   | 是   | 枚举如下：<br/>ONLINE=线上<br/>OFFLINE=线下     |
| signStatus            | 签章状态             | String   | 是   | 枚举如下：<br>SIGNED=已签署 <br>UNSIGNED=未签署 |
| attachmentSignUrl     | 已盖章退保单附件地址 | String   | 是   | 已签章退保单附件                                |
| paymentSeqNo          | 保司退保打款流水     | String   | 否   | 签章通知时，此字段为空                          |
| paymentTime           | 保司退保打款时间     | Date     | 否   | 签章通知时，此字段为空                          |
| paymentAmount         | 保司退保打款金额     | Long     | 否   | 签章通知时，此字段为空                          |
| funderPrincipalAmount | 资方应收本金         | Long     | 否   | 签章通知时，此字段为空                          |
| funderRefundAmount    | 服务费退回           | Long     | 否   | 签章通知时，此字段为空                          |
| partnerReceiveAmount  | 合作方应收金额       | Long     | 否   | 签章通知时，此字段为空                          |
| status                | 业务办理状态    | String     | 是 | 10=审核中<br/>20=审核通过<br/>21=审核拒绝<br/>30=退保失败<br/>31=退保成功<br/>|
<a id="heading-196"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注                             |
| -------- | -------- | -------- | ---- | -------------------------------- |
| code     | 响应码   | String   | 是   | 000000表示成功，非000000表示异常 |
| data     | 响应数据 | Object   | 是   |                                  |
| message  | 响应描述 | String   | 是   |                                  |
| success  | 响应结果 | boolean  | 是   |                                  |

<a id="heading-197"></a>
#### 请求示例

```
{
    "noticeType": "SIGN",
    "data": {
        "seqNo": "IW024051018129761",
        "partnerNo": "pa1008",
        "applicationNo": "A1374878545111",
        "vin": "VIN1008",
        "type": "COMPULSORY_TRAFFIC_INSURANCE",
        "attachmentOriginUrl": "https://www.test.com/12abc",
        "mode": "ONLINE",
        "signStatus": "SIGNED",
        "attachmentSignUrl": "https://www.test.com/abc",
        "paymentSeqNo": null,
        "paymentTime": null,
        "paymentAmount": null,
        "funderPrincipalAmount": null,
        "funderRefundAmount": null,
        "partnerReceiveAmount": null
    }
}
```

<a id="heading-198"></a>
#### 响应示例

```
{
    "code": "000000",
    "message": "Success",
    "data": null,
    "success": true
}
```



<a id="sec-3-6-4"></a>
### 3.6.4 退保批复通知

<a id="heading-200"></a>
#### 接口说明

| 接口描述 | 退保批复通知                                   |
| -------- | ---------------------------------------------- |
| 请求地址 | /order/api/insuranceWithdrawal/approveNotice   |
| 接口备注 | 合作方收到保险公司的退保批复结果后，通知此接口 |

<a id="heading-201"></a>
#### 请求参数

| 字段名称                 | 字段释义              | 字段格式 | 必填 | 备注     |
| ------------------------ | --------------------- | -------- | ---- | -------- |
| seqNo                    | 退保流水号            | String   | 是   |          |
| approveAmount            | 退保批复金额          | Long     | 是   | 单位：分 |
| approveAttachmentFileKey | 退保批复单附件FileKey | String   | 是   |          |
| approveTime              | 退保批复日期          | Date     | 是   |          |

<a id="heading-202"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注                             |
| -------- | -------- | -------- | ---- | -------------------------------- |
| code     | 响应码   | String   | 是   | 000000表示成功，非000000表示异常 |
| data     | 响应数据 | Object   | 是   |                                  |
| message  | 响应描述 | String   | 是   |                                  |
| success  | 响应结果 | boolean  | 是   |                                  |

<a id="heading-203"></a>
#### 请求示例

```
{
    "seqNo":"IW7847347875487",
    "approveAmount":10000,
    "approveAttachmentUrl":"http://www.test.com/783748387.pdf",
    "approveTime":"2024-01-01 12:20:36"
}
```

<a id="heading-204"></a>
#### 响应示例

```
{
    "code": "000000",
    "message": "Success",
    "data": null,
    "success": true
}
```



<a id="sec-3-6-5"></a>
### 3.6.5 退保撤销

<a id="heading-206"></a>
#### 接口说明

| 接口描述 | 退保撤销                              |
| -------- | ------------------------------------- |
| 请求地址 | /order/api/insuranceWithdrawal/cancel |
| 接口备注 |                                       |

<a id="heading-207"></a>
#### 请求参数

| 字段名称 | 字段释义   | 字段格式 | 必填 | 备注 |
| -------- | ---------- | -------- | ---- | ---- |
| seqNo    | 退保流水号 | String   | 是   |      |

<a id="heading-208"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注                             |
| -------- | -------- | -------- | ---- | -------------------------------- |
| code     | 响应码   | String   | 是   | 000000表示成功，非000000表示异常 |
| data     | 响应数据 | Object   | 是   |                                  |
| message  | 响应描述 | String   | 是   |                                  |
| success  | 响应结果 | boolean  | 是   |                                  |

<a id="heading-209"></a>
#### 请求示例

```
{
    "seqNo":"IW7847347875487"
}
```

<a id="heading-210"></a>
#### 响应示例

```
{
    "code": "000000",
    "message": "Success",
    "data": null,
    "success": true
}
```


## 

<a id="sec-3-7"></a>
## 3.7 退费

<a id="sec-3-7-1"></a>
### 3.7.1  退费申请

<a id="heading-213"></a>
#### 接口说明

| 接口描述 | 退费申请                              |
| -------- |-----------------------------------|
| 请求地址 | /order/api/insurance/refund/apply |
| 接口备注 |         |


<a id="heading-214"></a>
#### 请求参数

| 字段名称          | 字段释义     | 字段格式   | 必填 | 备注                                               |
|---------------|----------|--------|----|--------------------------------------------------|
| orderNo       | 退费申请编号   | String | 是  | 唯一                                               |
| applicationNo | 申请单号     | String | 是  | 进件申请单号                                           |
| signatureMode | 签章模式     | String | 是  | 枚举如下：<br/>ONLINE=线上<br/>OFFLINE=线下<br/>NULL=无需签章 |   | String | 是   |        |
| attachment    | 退费单合同    | String | 否  |                                                  |
| notifyUrl     | 退费结果回调地址 | String | 是  |                                                  |

<a id="heading-215"></a>
#### 响应参数

| 字段名称  | 字段释义  | 字段格式 | 必填 | 备注                             |
|-------|-------| -------- | ---- | -------------------------------- |
| seqNo | 退费流水号 | String   | 是   |  |

<a id="heading-216"></a>
#### 请求示例

```
{
    "seqNo":"IW7847347875487",
    "approveAmount":10000,
    "approveAttachmentUrl":"http://www.test.com/783748387.pdf",
    "approveTime":"2024-01-01 12:20:36"
}
```

<a id="heading-217"></a>
#### 响应示例

```
{
    "code": "000000",
    "message": "Success",
    "data": null,
    "success": true
}
```
<a id="sec-3-7-2"></a>
### 3.7.2 退费结果查询

<a id="heading-219"></a>
#### 接口说明

| 接口描述 | 退费结果查询                                               |
| -------- |------------------------------------------------------|
| 请求地址 | /order/api/insurance/refund/query                    |
| 接口备注 | 1.合作方查询退费结果<br>2.seqNo和orderNo不可同时为空 |

<a id="heading-220"></a>
#### 请求参数

| 字段名称    | 字段释义  | 字段格式 | 必填 | 备注 |
|---------|-------| -------- | ---- | ---- |
| seqNo   | 退费流水号 | String   | 否   |      |
| orderNo |  合作方退费业务单号 | String   | 否   |      |

<a id="heading-221"></a>
#### 响应参数
| 字段名称                  | 字段释义      | 字段格式 | 必填 | 备注                                            |
|-----------------------|-----------| -------- | ---- | ----------------------------------------------- |
| seqNo                 | 退费流水号     | String   | 是   |                                                 |
| orderNo               | 合作方退费业务单号 | String   | 是   |                                                 |
| applicationNo         | 进件单号      | String   | 是   |                                                 |
| originalContractUrl   | 退费单附件原始地址 | String   | 是   |                                                 |
| signatureMode                  | 签章模式      | String   | 是   | 枚举如下：<br/>ONLINE=线上<br/>OFFLINE=线下     |
| signatureStatus            | 签章状态      | String   | 是   | 枚举如下：<br>SIGNED=已签署 <br>UNSIGNED=未签署 |
| signedContractUrl     | 已盖章退保单附件  | String   | 否   | 签章情况下返回下载地址<br>未签章为空            |
| paymentSeqNo          | 保司退费打款流水  | String   | 否   |                                                 |
| refundTime           | 保司退费打款时间  | Date     | 否   |                                                 |
| paymentAmount         | 保司退费打款金额  | Long     | 否   | 单位：分                                        |
| firstPaymentAmount  | 合作方首笔款金额   | Long     | 否   | 单位：分                                        |

<a id="heading-222"></a>
#### 请求示例

```
{
    "seqNo":"IW024051018129761"
}
```

<a id="heading-223"></a>
#### 响应示例

```
{
  "code": "000000",
  "message": "Success",
  "data": {
    "seqNo": "seqNo_ab80ff3d4ce4",
    "orderNo": "orderNo_13903c64cdb8",
    "applicationNo": "applicationNo_a48cc07b1957",
    "signatureMode": "ONLINE",
    "signatureStatus": "SIGNED",
    "originalContractUrl": "originalContractUrl_bb3cb8d69d1f",
    "signedContractUrl": "signedContractUrl_6f3e24403165",
    "paymentSeqNo": "paymentSeqNo_b9b78ad8ac80",
    "refundAmount": 0,
    "refundTime": "2024-11-18 16:53:20",
    "firstPaymentAmount": 0
  },
  "success": true
}
```
<a id="sec-3-7-3"></a>
### 3.7.3   退费通知

<a id="heading-225"></a>
#### 接口说明

| 接口描述 | 退费通知                    |
| -------- |-------------------------|
| 请求地址 | 申请退费时，notifyUrl字段填充通知地址 |
| 接口备注 | 退费签章、退费结果通知             |

<a id="heading-226"></a>
#### 请求参数

| 字段名称   | 字段释义     | 字段格式 | 必填 | 备注                                       |
| ---------- | ------------ | -------- | ---- |------------------------------------------|
| noticeType | 通知消息类型 | String   | 是   | 枚举：<br/>REFUND_SIGN=退费签章通知<br/>REFUND=退费结果通知 |
| data       | 通知消息     | object   | 是   | 消息内容                                     |

data消息内容参数

| 字段名称              | 字段释义             | 字段格式 | 必填 | 备注                                            |
| --------------------- | -------------------- | -------- | ---- | ----------------------------------------------- |
| seqNo                 | 退费流水号     | String   | 是   |                                                 |
| orderNo               | 合作方退费业务单号 | String   | 是   |                                                 |
| applicationNo         | 进件单号      | String   | 是   |                                                 |
| originalContractUrl   | 退费单附件原始地址 | String   | 是   |                                                 |
| signatureMode                  | 签章模式      | String   | 是   | 枚举如下：<br/>ONLINE=线上<br/>OFFLINE=线下     |
| signatureStatus            | 签章状态      | String   | 是   | 枚举如下：<br>SIGNED=已签署 <br>UNSIGNED=未签署 |
| signedContractUrl     | 已盖章退保单附件  | String   | 否   | 签章情况下返回下载地址<br>未签章为空            |
| paymentSeqNo          | 保司退费打款流水  | String   | 否   |                                                 |
| refundTime           | 保司退费打款时间  | Date     | 否   |                                                 |
| paymentAmount         | 保司退费打款金额  | Long     | 否   | 单位：分                                        |
| firstPaymentAmount  | 合作方首笔款金额   | Long     | 否   | 单位：分                                        |

<a id="heading-227"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注                             |
| -------- | -------- | -------- | ---- | -------------------------------- |
| code     | 响应码   | String   | 是   | 000000表示成功，非000000表示异常 |
| data     | 响应数据 | Object   | 是   |                                  |
| message  | 响应描述 | String   | 是   |                                  |
| success  | 响应结果 | boolean  | 是   |                                  |

<a id="heading-228"></a>
#### 请求示例

```
{
    "noticeType": "SIGN",
  "data": {
    "seqNo": "seqNo_ab80ff3d4ce4",
    "orderNo": "orderNo_13903c64cdb8",
    "applicationNo": "applicationNo_a48cc07b1957",
    "signatureMode": "ONLINE",
    "signatureStatus": "SIGNED",
    "originalContractUrl": "originalContractUrl_bb3cb8d69d1f",
    "signedContractUrl": "signedContractUrl_6f3e24403165",
    "paymentSeqNo": "paymentSeqNo_b9b78ad8ac80",
    "refundAmount": 0,
    "refundTime": "2024-11-18 16:53:20",
    "firstPaymentAmount": 0
  }
}
```

<a id="heading-229"></a>
#### 响应示例

```
{
    "code": "000000",
    "message": "Success",
    "data": null,
    "success": true
}
```

<a id="sec-3-8"></a>
## 3.8 车险变更

<a id="sec-3-8-1"></a>
### 3.8.1 变更申请

<a id="heading-232"></a>
#### 接口说明

| 接口描述 | 申请退保                         |
| -------- | -------------------------------- |
| 请求地址 | /order/api/insuranceChange/apply |
| 接口备注 | 合作方申请车险变更               |

<a id="heading-233"></a>
#### 请求参数

| 字段名称          | 字段释义            | 字段格式 | 必填 | 备注                                        |
| ----------------- | ------------------- | -------- | ---- | ------------------------------------------- |
| applyNo           | 合作方变更业务单号  | String   | 是   |                                             |
| applicationNo     | 进件单号            | String   | 是   |                                             |
| vin               | 车架号              | String   | 是   |                                             |
| plateNumber       | 车牌号              | String   | 是   |                                             |
| type              | 变更类型            | String   | 是   | 见附录19                                    |
| attachmentFileKey | 变更申请附件fileKey | String   | 是   |                                             |
| model             | 签章模式            | String   | 是   | 枚举如下：<br/>ONLINE=线上<br/>OFFLINE=线下 |
| remark            | 业务说明            | String   | 否   |                                             |
| partnerNotifyUrl  | 合作方通知地址      | String   | 是   |                                             |

<a id="heading-234"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注                             |
| -------- | -------- | -------- | ---- | -------------------------------- |
| code     | 响应码   | String   | 是   | 000000表示成功，非000000表示异常 |
| data     | 响应数据 | Object   | 是   |                                  |
| message  | 响应描述 | String   | 是   |                                  |
| success  | 响应结果 | boolean  | 是   |                                  |

 data参数说明

| 字段名称 | 字段释义   | 字段格式 | 必填 | 备注 |
| -------- | ---------- | -------- | ---- | ---- |
| seqNo    | 变更流水号 | String   | 是   |      |

<a id="heading-235"></a>
#### 请求示例

```
{
    "applyNo":"pa1008",
    "partnerNotifyUrl":"http://localhost:8888/test/notifyUrl",
    "applicationNo":"A0000001",
    "vin":"VIN1008",
    "plateNumber":"豫A12345",
    "type":"COMPULSORY_TRAFFIC_INSURANCE",
    "attachmentFileKey":"urewuy837787767678hh",
    "model":"ONLINE"
}
```

<a id="heading-236"></a>
#### 响应示例

```
{
    "code": "000000",
    "message": "Success",
    "data": {
        "seqNo": "IW024051309323949"
    },
    "success": true
}
```



<a id="sec-3-8-2"></a>
### 3.8.2 变更结果查询

<a id="heading-238"></a>
#### 接口说明

| 接口描述 | 退保结果查询                                                 |
| -------- | ------------------------------------------------------------ |
| 请求地址 | /order/api/insuranceChange/query                             |
| 接口备注 | 1.合作方查询变更结果<br>2.seqNo和applyNo不可同时为空，同时存在时优先以seqNo查询 |

<a id="heading-239"></a>
#### 请求参数

| 字段名称 | 字段释义   | 字段格式 | 必填 | 备注 |
| -------- | ---------- | -------- | ---- | ---- |
| seqNo    | 变更流水号 | String   | 否   |      |
| applyNo  | 合作方编号 | String   | 否   |      |

<a id="heading-240"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注                             |
| -------- | -------- | -------- | ---- | -------------------------------- |
| code     | 响应码   | String   | 是   | 000000表示成功，非000000表示异常 |
| data     | 响应数据 | Object   | 是   |                                  |
| message  | 响应描述 | String   | 是   |                                  |
| success  | 响应结果 | boolean  | 是   |                                  |

 data参数说明

| 字段名称            | 字段释义           | 字段格式 | 必填 | 备注                                            |
| ------------------- | ------------------ | -------- | ---- | ----------------------------------------------- |
| seqNo               | 变更流水号         | String   | 是   |                                                 |
| applyNo             | 合作方变更业务单号 | String   | 是   |                                                 |
| applicationNo       | 进件单号           | String   | 是   |                                                 |
| vin                 | 车架号             | String   | 是   |                                                 |
| plateNumber         | 车牌号             | String   | 是   |                                                 |
| type                | 保险类型           | String   | 是   | 见附录19                                        |
| attachmentOriginUrl | 变更单附件地址     | String   | 是   |                                                 |
| model               | 签章模式           | String   | 是   | 枚举如下：<br/>ONLINE=线上<br/>OFFLINE=线下     |
| signStatus          | 签章状态           | String   | 是   | 枚举如下：<br>SIGNED=已签署 <br>UNSIGNED=未签署 |
| attachmentSignUrl   | 已盖章变更单附件   | String   | 否   | 签章情况下返回下载地址<br>未签章为空            |

<a id="heading-241"></a>
#### 请求示例

```
{
    "seqNo":"IW024051018129761"
}
```

<a id="heading-242"></a>
#### 响应示例

```
{
    "code": "000000",
    "message": "Success",
    "data": {
        "seqNo": "IW024051018129761",
        "applyNo": "pa1008",
        "applicationNo": "A1374878545111",
        "vin": "VIN1008",
        "plateNumber":"豫A12345"
        "type": "COMPULSORY_TRAFFIC_INSURANCE",
        "attachmentOriginUrl": "https://www.test.com/12abc",
        "model": "ONLINE",
        "signStatus": "SIGNED",
        "attachmentSignUrl": "https://www.test.com/abc"
    },
    "success": true
}
```



<a id="sec-3-8-3"></a>
### 3.8.3   变更结果通知

<a id="heading-244"></a>
#### 接口说明

| 接口描述 | 合作方变更通知                               |
| -------- | -------------------------------------------- |
| 请求地址 | 申请变更时，partnerNotifyUrl字段填充通知地址 |
| 接口备注 | 变更签章，变更结果，通知给合作方             |

<a id="heading-245"></a>
#### 请求参数

| 字段名称   | 字段释义     | 字段格式 | 必填 | 备注                                |
| ---------- | ------------ | -------- | ---- | ----------------------------------- |
| noticeType | 通知消息类型 | String   | 是   | 枚举：<br/>CHANGE_SIGN=退保签章通知 |
| data       | 通知消息     | object   | 是   | 消息内容                            |

data消息内容参数

| 字段名称            | 字段释义           | 字段格式 | 必填 | 备注                                            |
| ------------------- | ------------------ | -------- | ---- | ----------------------------------------------- |
| seqNo               | 变更流水号         | String   | 是   |                                                 |
| applyNo             | 合作方变更业务单号 | String   | 是   |                                                 |
| applicationNo       | 进件单号           | String   | 是   |                                                 |
| vin                 | 车架号             | String   | 是   |                                                 |
| plateNumber         | 车牌号             | String   | 是   |                                                 |
| type                | 保险类型           | String   | 是   | 见附录19                                        |
| attachmentOriginUrl | 变更单附件地址     | String   | 是   |                                                 |
| model               | 签章模式           | String   | 是   | 枚举如下：<br/>ONLINE=线上<br/>OFFLINE=线下     |
| signStatus          | 签章状态           | String   | 是   | 枚举如下：<br>SIGNED=已签署 <br>UNSIGNED=未签署 |
| attachmentSignUrl   | 已盖章变更单附件   | String   | 否   | 签章情况下返回下载地址<br>未签章为空            |

<a id="heading-246"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注                             |
| -------- | -------- | -------- | ---- | -------------------------------- |
| code     | 响应码   | String   | 是   | 000000表示成功，非000000表示异常 |
| data     | 响应数据 | Object   | 是   |                                  |
| message  | 响应描述 | String   | 是   |                                  |
| success  | 响应结果 | boolean  | 是   |                                  |

<a id="heading-247"></a>
#### 请求示例

```
{
    "noticeType": "SIGN",
    "data": {
        "seqNo": "IW024051018129761",
        "applyNo": "pa1008",
        "applicationNo": "A1374878545111",
        "vin": "VIN1008",
        "plateNumber":"豫A12345"
        "type": "COMPULSORY_TRAFFIC_INSURANCE",
        "attachmentOriginUrl": "https://www.test.com/12abc",
        "model": "ONLINE",
        "signStatus": "SIGNED",
        "attachmentSignUrl": "https://www.test.com/abc"
    }
}
```

<a id="heading-248"></a>
#### 响应示例

```
{
    "code": "000000",
    "message": "Success",
    "data": null,
    "success": true
}
```



<a id="sec-3-8-4"></a>
### 3.8.4 变更批复通知

<a id="heading-250"></a>
#### 接口说明

| 接口描述 | 退保批复通知                                   |
| -------- | ---------------------------------------------- |
| 请求地址 | /order/api/insuranceChange/approveNotice       |
| 接口备注 | 合作方收到保险公司的变更批复结果后，通知此接口 |

<a id="heading-251"></a>
#### 请求参数

| 字段名称                 | 字段释义              | 字段格式 | 必填 | 备注 |
| ------------------------ | --------------------- | -------- | ---- | ---- |
| seqNo                    | 变更流水号            | String   | 是   |      |
| approveAttachmentFileKey | 变更批复单附件FileKey | String   | 是   |      |
| approveTime              | 变更批复时间          | String   | 是   |      |

<a id="heading-252"></a>
#### 响应参数

| 字段名称 | 字段释义 | 字段格式 | 必填 | 备注                             |
| -------- | -------- | -------- | ---- | -------------------------------- |
| code     | 响应码   | String   | 是   | 000000表示成功，非000000表示异常 |
| data     | 响应数据 | Object   | 是   |                                  |
| message  | 响应描述 | String   | 是   |                                  |
| success  | 响应结果 | boolean  | 是   |                                  |

<a id="heading-253"></a>
#### 请求示例

```
{
    "seqNo":"IW7847347875487",
    "approveAttachmentUrl":"http://www.test.com/783748387.pdf",
    "approveTime":"2024-01-01 12:20:36"
}
```

<a id="heading-254"></a>
#### 响应示例

```
{
    "code": "000000",
    "message": "Success",
    "data": null,
    "success": true
}
```



<a id="sec-4"></a>
# 4. 附录

<a id="app-1"></a>
## 附1：验签公私钥证书生成步骤

1：生成采用des3算法保护的私钥

openssl genrsa -des3 -out private-rsa.key 2048

命令执行过程中提示Enter pass phrase 时 ，输入保护私钥文件的密码。  
（私钥密码需要记住，生成公钥证书需使用）

2：生成公钥证书

openssl req -new -x509 -key private-rsa.key -days 3650 -out public-rsa.pem

（-days 参数是配置证书有效时间，单位为天，可按需调配）  
该过程除了最开始时需要输入私钥文件的保护密码之外，其他按接入商需求自行输入（不输入按回车也不影响使用）

3：生成PKCS12 格式Keystore

openssl pkcs12 -export -in public-rsa.pem -inkey private-rsa.key -out private_key.pfx

按照提示输入私钥密码使用私钥，生成加密私钥

<a id="app-2"></a>
## 附2：加签验签工具类

<a id="app-3"></a>
## 附3：银行代码

暂无

<a id="app-4"></a>
## 附4：渠道编码

| 渠道编码     | 名称   |
|----------|------|
| ALLINPAY | 通联   |
| BAOFUPAY | 宝付   |
| YEEPAY   | 易宝   |
| BCM      | 交通银行 |
| JDPAY    | 京东支付 |

<a id="app-5"></a>
## 附5：省/直辖市

| 广东省           |
|------------------|
| 浙江省           |
| 四川省           |
| 湖南省           |
| 江苏省           |
| 河南省           |
| 山东省           |
| 广西壮族自治区   |
| 湖北省           |
| 贵州省           |
| 重庆市           |
| 福建省           |
| 江西省           |
| 云南省           |
| 安徽省           |
| 陕西省           |
| 新疆维吾尔自治区 |
| 上海市           |
| 海南省           |
| 河北省           |
| 北京市           |
| 辽宁省           |
| 山西省           |
| 甘肃省           |
| 天津市           |
| 青海省           |
| 吉林省           |
| 内蒙古自治区     |
| 黑龙江省         |
| 宁夏回族自治区   |
| 西藏自治区       |
| 台湾省           |
| 香港特别行政区   |
| 澳门特别行政区   |

<a id="app-6"></a>
## 附6：支付方式编码

| 编码 | 名称 |
|------|------|
| 10   | 线上 |
| 20   | 线下 |

<a id="app-7"></a>
## 附7：结算方式编码

| 编码 | 名称           |
|------|----------------|
| 100  | 正常还款       |
| 200  | 提前还款       |
| 201  | 提前结清       |
| 400  | 退费           |
| 401  | 七天无理由退费 |
| 500  | 代偿           |
| 600  | 回购           |

<a id="app-8"></a>
## 附8：用户标识

| 编码  | 名称       |
| ----- | ---------- |
| C0001 | 资方       |
| G0001 | 担保方     |
| P0001 | 合作方     |
| X0001 | 技术服务方 |

<a id="app-9"></a>
## 附9：支付状态

| 编码 | 名称     |
|------|----------|
| 1    | 未支付   |
| 2    | 支付中   |
| 3    | 支付失败 |
| 4    | 支付成功 |
| 5    | 超时关闭 |

<a id="app-10"></a>
## 附10：账单状态

| 编码 | 名称           |
|------|----------------|
| 100  | 正常还款       |
| 201  | 提前结清       |
| 400  | 退费           |
| 401  | 七天无理由退费 |
| 500  | 待代偿         |
| 501  | 已代偿         |
| 600  | 待回购         |
| 601  | 已回购         |

<a id="app-11"></a>
## 附11：结算状态

| 编码 | 名称   |
|------|--------|
| 0    | 未结算 |
| 1    | 已结算 |
| 2    | 未结清 |

<a id="app-12"></a>
## 附12：对账单类型

| 编码  | 名称             |
| ----- | ---------------- |
| 1000  | 代偿对账单       |
| 3000  | 线下对账单       |
| 7000  | 支付手续费对账单 |
| 10010 | 退保对账单       |
| 10061 | 代收代付对账单 |

<a id="app-13"></a>
## 附13：对账单科目编码

| 编码  | 名称       |
| ----- | ---------- |
| 101   | 本金       |
| 10201 | 资方服务费 |
| 10202 | 技术服务费 |
| 103   | 其他费用   |
| 303   | 滞纳金     |
| 304   | 支付手续费 |

<a id="app-14"></a>
## 附14：文件类型

| 编码  | 名称               |
|-----|------------------|
| 100 | 客户身份证人像面         |
| 101 | 客户身份证国徽面         |
| 140 | 个人征信查询授权书        |
| 150 | 客户工作单位营业执照       |
| 151 | 客户工作单位法人身份证人像面   |
| 152 | 客户工作单位法人身份证国徽面   |
| 160 | 电子签名授权委托书        |
| 162 | 电子签名授权委托书(单位)    |
| 170 | 车辆行驶证照片          |
| 210 | 委托收付款协议          |
| 211 | 委托支付协议           |
| 220 | 委托代理服务合同         |
| 221 | 车辆委托管理合同         |
| 230 | 委托扣款服务协议及委托扣款授权书 |
| 240 | 应收账款转让通知书及回执     |
| 320 | 挂靠协议             |
| 400 | 其它附件             |
| 500 | 商业险投保单           |
| 510 | 交强险投保单           |
| 520 | 附加险投保单           |
| 600 | 商业险保单            |
| 610 | 交强险保单            |
| 620 | 附加险保单            |
| 700 | 退保单              |
| 710 | 退保批复函            |
| 720 | 退费单              |
| 730 | 变更申请单            |
| 731 | 变更申请批复函          |
| 800 | 保理合同             |

<a id="app-15"></a>
## 附15：最高学历

| 编码  |名称|
|-----|-----|
| 10 | 研究生       |
| 20 | 大学本科       |
| 30 | 大学专科和专科学校       |
| 40 | 中等专业学校或中等技术学校       |
| 50 | 技术学校       |
| 60 | 高中       |
| 70 | 初中       |
| 80 | 小学      |
| 90 | 文盲或半文盲      |
| 99 | 未知       |

<a id="app-16"></a>
## 附16：职业类型

| 编码  |名称|
|-----|-----|
| 1 | 政府部门       |
| 2 | 教科文       |
| 3 | 金融       |
| 4 | 商贸       |
| 5 | 房地产       |
| 6 | 制造业       |
| 7 | 自由职业       |
| 8 | 其他|

<a id="app-17"></a>
## 附17：婚姻状态

| 编码  |名称|
|-----|-----|
| 0 | 未婚       |
| 1 | 已婚       |
| 2 | 离异       |
| 3 | 其他       |

<a id="app-18"></a>
## 附18：银行卡开户证件类型

| 编码  |名称|
|-----|-----|
| 0 | 身份证       |
| 1 | 户口簿       |
| 2 | 护照       |
| 3 | 军官证       |
| 4 | 士兵证       |
| 5 | 港澳居民来往内地通行证       |
| 6 | 台湾同胞来往内地通行证       |
| 7 | 临时身份证       |
| 8 | 外国人居留证       |
| 9 | 警官证       |
| X | 其他证件       |
| A | 营业执照       |
| B | 组织机构代码证       |
| C | 税务登记证       |
| D | 统一社会信用代码证       |


<a id="app-19"></a>
## 附19：保险类型

| 编码  |名称|
|-----|-----|
| COMPULSORY_TRAFFIC_INSURANCE | 交强险       |
| COMMERCIAL_INSURANCE | 商业险       |
| ADDITIONAL_INSURANCE | 附加险       |

<a id="app-20"></a>
## 附20：账单科目编码

| 编码  | 名称       |
| ----- | ---------- |
| 101   | 本金       |
| 103   | 其他费用   |
| 104   | 提前结清手续费     |
| 10501   | 滞纳金     |
