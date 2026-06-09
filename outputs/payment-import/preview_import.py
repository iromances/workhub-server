from __future__ import annotations
from openpyxl import load_workbook
from pathlib import Path
import re, json, hashlib
from collections import defaultdict, Counter

path=Path('/Users/aslight/Library/CloudStorage/OneDrive-个人/公司/支付渠道/三方渠道生产环境.xlsx')
INCLUDE=['资产平台','保费分期','设备','交通银行','账单管理','嘉泰']
EXCLUDE=['微信小程序、公众号','通联全民测试商户号']
BUS_CODES={
    '资产平台':'ASSET_PLATFORM',
    '保费分期':'PREMIUM_INSTALLMENT',
    '嘉泰资产平台':'JIATAI_ASSET_PLATFORM',
    '可信资产交易平台':'TRUSTED_ASSET_TRADING',
    '可信--租赁保理':'TRUSTED_LEASING_FACTORING',
    '可信--卡尔':'TRUSTED_CARL',
    '可信--账单管理':'TRUSTED_BILLING',
    '叉车':'FORKLIFT',
    '智能柜':'SMART_CABINET',
    '汇浦设备金融':'HUIPU_EQUIPMENT_FINANCE',
}
SHEET_DEFAULT_BIZ={'设备':'设备','账单管理':'可信资产交易平台'}
CHANNELS={
    '宝付':('BAOFOO','宝付','宝付'),
    '易宝':('YEEPAY','易宝','易宝'),
    '交通银行':('BOCOM','交通银行','交通银行'),
    '企业微信':('WECOM_PAY','企业微信','企业微信'),
    '企业支付宝':('ALIPAY_ENTERPRISE','企业支付宝','支付宝'),
    '通联（通联通）':('ALLINPAY_TLT','通联通','通联'),
    '通联（收银宝）':('ALLINPAY_SYB','通联收银宝','通联'),
    '通联通':('ALLINPAY_TLT','通联通','通联'),
    '通联':('ALLINPAY','通联','通联'),
}
PURPOSES={
    '代收':['WITHHOLD'],
    '代付':['PAY_OUT'],
    '签约':['SIGN_AGREEMENT'],
    '绑卡':['BIND_CARD'],
    '分账':['SPLIT_SETTLEMENT'],
    '代收分账':['WITHHOLD_SPLIT_SETTLEMENT'],
    '退款主户':['REFUND_MAIN_ACCOUNT'],
    '代收子商户（生产测试用）':['WITHHOLD_SUB_MERCHANT_PROD_TEST'],
    '代收子商户（北京）':['WITHHOLD_SUB_MERCHANT_BEIJING'],
    '代收子商户（天津）':['WITHHOLD_SUB_MERCHANT_TIANJIN'],
    '代收子商户':['WITHHOLD_SUB_MERCHANT'],
    '先行通被分账商户':['SPLIT_RECEIVER_XXT'],
    '里易被分账商户':['SPLIT_RECEIVER_LIYI'],
    '账户体系':['ACCOUNT_SYSTEM'],
    '签约&代收':['SIGN_AGREEMENT','WITHHOLD'],
}
RELATION_COLS={
    '共享协议商户':'SHARED_AGREEMENT',
    '正常分账商户':'NORMAL_SPLIT',
    '代偿回购':'COMPENSATION_REPURCHASE',
    '分账商户':'SPLIT_RECEIVER',
}
SENSITIVE_MAP={
    '私钥密码':'PRIVATE_KEY_PASSWORD',
    '私钥 / 密码':'PRIVATE_KEY_PASSWORD',
    '用户名（接口用）':'USERNAME',
    '用户名':'USERNAME',
    '用户密码（接口用）':'PASSWORD',
    '用户密码':'PASSWORD',
    'AESKEY':'AES_KEY',
    '密钥':'SIGN_KEY',
    '账户名':'USERNAME',
    '账户密码':'PASSWORD',
    '交易密码':'TRADE_PASSWORD',
    'pin码':'PASSWORD',
    '安全密码':'PASSWORD',
}
PARAM_NAMES={'手续费','APPID','业务代码','终端号','备注','密钥类型','企业代码','用户号','ip'}

def clean(v):
    if v is None: return ''
    s=str(v).replace('\xa0',' ').strip()
    if s.endswith('.0') and re.fullmatch(r'\d+\.0', s):
        s=s[:-2]
    return s.strip()

def code_from_name(name):
    if name in BUS_CODES: return BUS_CODES[name]
    return 'BIZ_'+hashlib.md5(name.encode()).hexdigest()[:8].upper()

def parse_project(text,biz):
    text=clean(text)
    if not text:
        return None
    if '/' in text:
        code,name=text.split('/',1)
        return clean(code), clean(name)
    slug=re.sub(r'[^A-Za-z0-9]+','_',text).strip('_').upper()
    if slug and re.search(r'[A-Za-z]', slug):
        return slug, text
    return code_from_name(biz)+'_'+hashlib.md5(text.encode()).hexdigest()[:6].upper(), text

def parse_merchant(text):
    text=clean(text)
    if not text: return None
    if text in {'同上','复用强云','未配置'}: return None
    m=re.search(r'([A-Za-z0-9][A-Za-z0-9_\-]{3,})', text)
    if not m: return None
    code=m.group(1).strip()
    name=text[m.end():].strip(' /\t-') or code
    if code in {'同上'}: return None
    return code,name,text

def extract_merchants_from_text(text):
    text=clean(text)
    if not text: return []
    parts=re.split(r'\s*/\s*|\n|；|;', text)
    out=[]
    for p in parts:
        pm=parse_merchant(p)
        if pm: out.append(pm)
    return out

def normalize_channel(raw, last=''):
    raw=clean(raw)
    if raw in {'同上'}: raw=last
    if not raw: raw=last
    return CHANNELS.get(raw, (re.sub(r'\W+','_',raw).strip('_').upper() or 'UNKNOWN', raw or '未知渠道', raw or '未知'))

def normalize_purpose(raw):
    raw=clean(raw)
    if raw in PURPOSES: return PURPOSES[raw]
    if '代付' in raw and '收款' in raw: return ['PAY_OUT','WITHHOLD']
    return []

def header_map(ws, rownum):
    rows=list(ws.iter_rows(min_row=rownum, max_row=rownum+1, values_only=True))
    h=[clean(v) for v in rows[0]]
    sub=[clean(v) for v in rows[1]] if len(rows)>1 else []
    mp={}
    last=''
    for i,x in enumerate(h):
        name=x or (sub[i] if i < len(sub) else '')
        if not name and i < len(sub): name=sub[i]
        if x: last=x
        if name in {'','渠道公钥','商户私钥'}:
            continue
        # special duplicate names: keep first; continuation relation handled separately
        if name not in mp:
            mp[name]=i
    return mp

def rowval(row,mp,key):
    i=mp.get(key)
    return clean(row[i]) if i is not None and i < len(row) else ''

wb=load_workbook(path, data_only=True, read_only=True)
records=[]; skipped=[]; projects={}; groups={}; channels={}; merchants={}; credentials=[]; params=[]; bindings=[]; relation_items=[]
unknown_purposes=Counter(); ambiguous=[]
for s in INCLUDE:
    ws=wb[s]
    if s=='交通银行':
        mp=header_map(ws,1)
        for rn,row in enumerate(ws.iter_rows(min_row=2, values_only=True),2):
            acct=rowval(row,mp,'账户号') or rowval(row,mp,'pin码')
            acct=re.sub(r'\s+','',acct)
            name=rowval(row,mp,'账户名')
            if not acct and not name: continue
            biz=rowval(row,mp,'备注') or '交通银行'
            biz=biz.split('（')[0].strip() or '交通银行'
            if biz not in BUS_CODES and biz not in {'保费分期','趣学呗'}: biz='资产平台'
            project_text=rowval(row,mp,'备注') or '交通银行账户'
            p=parse_project(project_text,biz) or (code_from_name(biz)+'_BOCOM', project_text)
            groups[biz]=code_from_name(biz); projects[p[0]]=(p[1],biz)
            ch=CHANNELS['交通银行']; channels[ch[0]]=ch
            mcode,mname,raw=acct,name or acct,acct+'/'+(name or acct)
            merchants[(ch[0],mcode)]=(mname,'PROD')
            bindings.append((p[0],ch[0],mcode,('PAY_OUT','WITHHOLD'),rn,s))
            for key in ['pin码','安全密码']:
                val=rowval(row,mp,key)
                if val: credentials.append((ch[0],mcode,key,SENSITIVE_MAP[key],val,s,rn))
            for key in ['企业代码','用户号','ip','备注']:
                val=rowval(row,mp,key)
                if val: params.append((ch[0],mcode,key,val,s,rn))
        continue
    header=1 if s not in {'嘉泰'} else 3
    mp=header_map(ws,header)
    last_biz=''; last_project=''; last_channel=''; last_purpose=''
    for rn,row in enumerate(ws.iter_rows(min_row=header+1, values_only=True), header+1):
        vals=[clean(x) for x in row]
        if not any(vals): continue
        biz=rowval(row,mp,'业务') or rowval(row,mp,'业务线')
        project_text=rowval(row,mp,'项目')
        purpose=rowval(row,mp,'用途')
        channel_raw=rowval(row,mp,'渠道')
        merchant_raw=rowval(row,mp,'渠道商户')
        # fix shifted row in 资产平台 row 24
        if s=='资产平台' and not purpose and project_text in PURPOSES:
            biz=vals[1]; project_text=vals[2]; purpose=vals[3]; channel_raw=vals[4]; merchant_raw=vals[5]
        if biz: last_biz=biz
        elif s in SHEET_DEFAULT_BIZ and not last_biz: last_biz=SHEET_DEFAULT_BIZ[s]
        biz=last_biz or SHEET_DEFAULT_BIZ.get(s,'')
        if project_text: last_project=project_text
        project_text=project_text or last_project
        if purpose: last_purpose=purpose
        purpose=purpose or last_purpose
        ch=normalize_channel(channel_raw,last_channel)
        if ch[1] != '未知渠道': last_channel=channel_raw or last_channel
        merchant=parse_merchant(merchant_raw)
        # continuation relation rows without merchant are attached to previous binding only
        if not merchant and not any(rowval(row,mp,k) for k in RELATION_COLS) and merchant_raw:
            skipped.append({'sheet':s,'row':rn,'reason':'渠道商户无法解析','value':merchant_raw})
            continue
        if not biz or not project_text or not purpose or not merchant:
            if any([merchant_raw, purpose, project_text]) and not any(rowval(row,mp,k) for k in RELATION_COLS):
                skipped.append({'sheet':s,'row':rn,'reason':'缺少业务/项目/用途/商户之一','business':biz,'project':project_text,'purpose':purpose,'merchant':merchant_raw})
            continue
        pcodes=normalize_purpose(purpose)
        if not pcodes:
            unknown_purposes[purpose]+=1
            skipped.append({'sheet':s,'row':rn,'reason':'用途无法映射','purpose':purpose})
            continue
        p=parse_project(project_text,biz)
        if not p:
            skipped.append({'sheet':s,'row':rn,'reason':'项目无法解析','project':project_text})
            continue
        pcode,pname=p; mcode,mname,raw=merchant
        groups[biz]=code_from_name(biz); projects[pcode]=(pname,biz); channels[ch[0]]=ch; merchants[(ch[0],mcode)]=(mname,'PROD')
        bindings.append((pcode,ch[0],mcode,tuple(pcodes),rn,s))
        for key in PARAM_NAMES:
            val=rowval(row,mp,key)
            if val and key not in {'备注'}:
                params.append((ch[0],mcode,key,val,s,rn))
        rem=rowval(row,mp,'备注')
        if rem: params.append((ch[0],mcode,'原始备注',rem,s,rn))
        for key,typ in SENSITIVE_MAP.items():
            val=rowval(row,mp,key)
            if val:
                credentials.append((ch[0],mcode,key,typ,val,s,rn))
        for key,role in RELATION_COLS.items():
            val=rowval(row,mp,key)
            for rm in extract_merchants_from_text(val):
                relation_items.append((pcode,ch[0],mcode,rm[0],rm[1],role,key,s,rn))
                merchants[(ch[0],rm[0])]=(rm[1],'PROD')

summary={
    'includedSheets': INCLUDE,
    'excludedSheets': EXCLUDE,
    'businessLines': len(groups),
    'projects': len(projects),
    'channels': len(channels),
    'merchantAccounts': len(merchants),
    'bindings': len(set(bindings)),
    'credentialItems': len(credentials),
    'paramItems': len(params),
    'relationItems': len(relation_items),
    'skippedRows': len(skipped),
    'unknownPurposes': dict(unknown_purposes),
}
print(json.dumps(summary, ensure_ascii=False, indent=2))
print('\n业务线编码:')
for b,c in sorted(groups.items()): print(f'- {b}: {c}')
print('\n渠道:')
for c in sorted(channels.values()): print('-',c)
print('\n跳过/需确认前30条:')
for x in skipped[:30]: print(x)
# save preview json
out={'summary':summary,'groups':groups,'projects':projects,'channels':channels,'merchants':{f'{k[0]}::{k[1]}':v for k,v in merchants.items()},'bindings':list(map(list,set(bindings))),'credentials':credentials,'params':params,'relations':relation_items,'skipped':skipped}
Path('outputs/payment-import/preview.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
