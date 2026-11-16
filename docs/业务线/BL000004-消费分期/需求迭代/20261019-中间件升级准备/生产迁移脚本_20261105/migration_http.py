"""迁移脚本共用的标准库 HTTP 客户端；不输出请求正文、凭据或响应正文。"""
import getpass
import hashlib
import http.cookiejar
import json
import os
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone


class MigrationError(Exception):
    pass


def require(ok, message):
    if not ok:
        raise MigrationError(message)


def now():
    return datetime.now(timezone.utc).isoformat()


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True,
                                     separators=(",", ":")).encode()).hexdigest()


def secret(name, label):
    value = os.environ.get(name) or getpass.getpass(label + ": ")
    require(bool(value), label + " 不能为空")
    return value


def base_url(value):
    p = urllib.parse.urlsplit(value)
    require(p.scheme in ("http", "https") and p.hostname and not p.username
            and not p.password and not p.query and not p.fragment,
            "入口必须是无凭据、无查询串和锚点的 HTTP(S) 根地址")
    require(not p.path.endswith(("/jobinfo", "/login")), "入口需去掉 /jobinfo 或 /login")
    return value.rstrip("/")


def write_private(path, value, exclusive=False):
    # 运行文件只包含摘要/标识/进度；仍使用 0600，禁止覆盖已有快照。
    flags = os.O_WRONLY | os.O_CREAT | (os.O_EXCL if exclusive else os.O_TRUNC)
    fd = os.open(str(path), flags, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        json.dump(value, f, ensure_ascii=False, indent=2)
        f.write("\n")
        f.flush()
        os.fsync(f.fileno())


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise MigrationError("入口返回重定向；请核实准确根地址或登录状态")


class Http:
    def __init__(self, url):
        self.url = base_url(url)
        self.opener = urllib.request.build_opener(
            NoRedirect(), urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def request(self, path, params=None, method="POST", headers=None, raw=False):
        data = urllib.parse.urlencode(params or {}).encode()
        url = self.url + path
        if method == "GET":
            url += "?" + data.decode()
            data = None
        req = urllib.request.Request(url, data=data, method=method, headers=headers or {})
        try:
            with self.opener.open(req, timeout=20) as response:
                body = response.read(8 * 1024 * 1024 + 1)
            require(len(body) <= 8 * 1024 * 1024, "响应超过大小限制")
            text = body.decode("utf-8")
            return text if raw else json.loads(text)
        except urllib.error.HTTPError as exc:
            raise MigrationError("HTTP 请求失败，状态码 %s；正文未输出" % exc.code) from None
        except (urllib.error.URLError, TimeoutError, OSError):
            raise MigrationError("网络请求失败或超时；写操作结果可能未知，先回读核实") from None
        except (ValueError, UnicodeError):
            raise MigrationError("响应格式不符合预期；正文未输出") from None

    def xxl_login(self, prefix):
        user = os.environ.get(prefix + "_USER") or input(prefix + " 账号: ")
        result = self.request("/login", {"userName": user,
            "password": secret(prefix + "_PASSWORD", prefix + " 密码"), "ifRemember": "off"})
        require(isinstance(result, dict) and result.get("code") == 200, "XXL-JOB 登录失败")

    def pages(self, path, params):
        rows, seen, total = [], set(), None
        while total is None or len(rows) < total:
            result = self.request(path, dict(params, start=len(rows), length=100))
            require(isinstance(result, dict) and isinstance(result.get("data"), list)
                    and isinstance(result.get("recordsFiltered"), int), "分页响应无效或登录已失效")
            count, page = result["recordsFiltered"], result["data"]
            require(0 <= count <= 10000, "分页总数超出本次迁移规模")
            require(total is None or total == count, "分页期间任务数量变化，请冻结编辑后重试")
            total = count
            require(bool(page) or len(rows) == total, "分页提前结束")
            for item in page:
                require(isinstance(item, dict) and item.get("id") not in seen,
                        "分页重复或记录无效")
                seen.add(item["id"])
                rows.append(item)
            require(len(rows) <= total, "分页数量超出声明总数")
        return rows
