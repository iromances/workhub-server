import json
from urllib import request, parse
BASE='http://127.0.0.1:8081/api'

def call(method,path,data=None,token=None,params=None):
    url=BASE+path
    if params: url+='?'+parse.urlencode(params)
    body=None if data is None else json.dumps(data).encode()
    req=request.Request(url,data=body,method=method,headers={'Content-Type':'application/json'})
    if token: req.add_header('Authorization','Bearer '+token)
    with request.urlopen(req,timeout=30) as r:
        res=json.loads(r.read().decode())
    if res['code']!=0: raise RuntimeError(res)
    return res['data']

token=call('POST','/auth/login',{'username':'admin','password':'admin123'})['token']
channels=call('GET','/payment/channels',token=token)['items']
projects=call('GET','/projects',token=token)['items']
merchants=call('GET','/payment/merchants',token=token)['items']
bindings=call('GET','/payment/bindings',token=token)['items']
print(json.dumps({
  'channels': len(channels),
  'projects': len(projects),
  'merchants': len(merchants),
  'bindings': len(bindings),
  'sampleMerchant': merchants[0] if merchants else None,
  'sampleBinding': bindings[0] if bindings else None,
}, ensure_ascii=False, indent=2))
