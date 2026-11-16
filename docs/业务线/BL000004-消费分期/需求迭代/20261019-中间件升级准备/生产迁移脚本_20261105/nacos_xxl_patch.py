#!/usr/bin/env python3
"""按明确清单替换新 Nacos 的两个 XXL-JOB 属性，默认只预览。"""
import argparse
import hashlib
import json
import os
import re
import sys
import time
from pathlib import Path

from migration_http import Http, MigrationError, base_url, now, require, secret, write_private

NEW_NACOS = "https://amp-nacos.xxingtong.com/nacos"
NEW_ADMIN = "https://amp-xxl-job.xxingtong.com/xxl-job-admin"
ADDRESS = "xxl.job.admin.addresses"
TOKEN = "xxl.job.accessToken"
KEYS = {ADDRESS, TOKEN}


def md5(text):
    return hashlib.md5(text.encode("utf-8")).hexdigest()


def scalar_parts(value):
    """只接受一行标量；保留尾部注释，拒绝折行/集合/别名等不明确结构。"""
    require(not value.startswith(("|", ">", "&", "*", "!", "[", "{")),
            "XXL-JOB 属性使用复杂 YAML 结构，请先单独核对")
    if value.startswith("#"):
        return "", " " + value
    if value.startswith(('"', "'")):
        pattern = r'("(?:[^"\\]|\\.)*"|\'(?:[^\']|\'\')*\')(\s*(?:#.*)?)$'
        m = re.fullmatch(pattern, value)
        require(m is not None, "XXL-JOB 属性不是完整的单行标量")
        return m.group(1), m.group(2)
    m = re.match(r"(.*?)(\s+#.*|\s*)$", value)
    return m.group(1), m.group(2)


def locate_yaml(text):
    # 这是有明确输入约束的行定位器，不是通用 YAML 解析器；不重序列化整份配置。
    found, seen, stack = {}, set(), []
    block_indent, doc_seen, content_seen = None, False, False
    for index, line in enumerate(text.splitlines(keepends=True)):
        body = line.rstrip("\r\n")
        indent = len(body) - len(body.lstrip(" "))
        stripped = body.lstrip(" ")
        if block_indent is not None:
            if not stripped or indent > block_indent:
                continue
            block_indent = None
        if not stripped or stripped.startswith("#"):
            continue
        require(not stripped.startswith("\t"), "YAML 缩进含 Tab，拒绝自动修改")
        if re.fullmatch(r"---(?:\s+#.*)?", stripped):
            require(not doc_seen and not content_seen, "多文档 YAML 不支持自动替换")
            doc_seen = True
            continue
        require(not re.fullmatch(r"\.\.\.(?:\s+#.*)?", stripped)
                and not stripped.startswith("%"), "YAML 文档指令不支持自动替换")
        content_seen = True
        while stack and indent <= stack[-1][0]:
            stack.pop()
        parent = stack[-1][1] if stack else ()
        # 序列中的相同键不是根级 Spring 属性，隔离其路径。
        if stripped.startswith("- ") or stripped == "-":
            require(parent and parent[0] != "xxl", "根序列或 XXL-JOB 序列不支持自动替换")
            stack.append((indent, parent + ("<sequence>",)))
            continue
        m = re.match(r'^( *)([A-Za-z0-9_.-]+|"[A-Za-z0-9_.-]+"|\'[A-Za-z0-9_.-]+\'):(?:[ \t]+|$)(.*)$', body)
        require(not re.match(r"<<\s*:", stripped), "YAML 合并键可能覆盖目标属性，拒绝自动修改")
        if not m:
            require(not (parent and parent[0] == "xxl") and not stripped.startswith(("xxl", "'xxl", '"xxl')),
                    "XXL-JOB YAML 结构无法明确定位")
            continue
        key = m.group(2).strip("\"'")
        path = parent + tuple(key.split("."))
        value = m.group(3)
        if value.startswith(('"', "'")):
            scalar_parts(value)  # 排除其他字段的跨行引号，避免误把其内容识别为配置键。
        if path and path[0] == "xxl":
            require(path not in seen, "XXL-JOB YAML 存在重复键或等价路径")
            seen.add(path)
            require(not value.startswith(("&", "*", "!", "[", "{")), "XXL-JOB 使用别名或流式结构")
            canonical = ".".join(path)
            # Spring 可识别的其他拼写不能与本脚本的键静默混用。
            normalized = canonical.replace("-", "").replace("_", "").lower()
            require(normalized not in {k.lower() for k in KEYS} or canonical in KEYS,
                    "XXL-JOB 属性拼写不是约定格式，请核对后处理")
            if canonical in KEYS:
                scalar, suffix = scalar_parts(value)
                prefix = body[:m.start(3)]
                # 空值行的正则可能没有分隔空格，写回时补一个。
                if not prefix.endswith((" ", "\t")):
                    prefix += " "
                found[canonical] = (index, prefix, scalar, suffix, line[len(body):])
        if not value or value.startswith("#"):
            stack.append((indent, path))
        elif value.startswith(("|", ">")):
            block_indent = indent
    return found


def locate_properties(text):
    found = {}
    continued = False
    for index, line in enumerate(text.splitlines(keepends=True)):
        body = line.rstrip("\r\n")
        if continued:
            require("xxl" not in body.lower(), "properties 续行中出现 XXL-JOB，拒绝歧义替换")
            continued = len(body) - len(body.rstrip("\\")) & 1
            continue
        if not body.strip() or body.lstrip().startswith(("#", "!")):
            continue
        continued = len(body) - len(body.rstrip("\\")) & 1
        property_key = re.match(r"^\s*([^=:\s]+)", body)
        require(property_key is None or "\\" not in property_key.group(1),
                "properties 含转义键，可能存在等价的 XXL-JOB 属性，拒绝自动修改")
        m = re.match(r"^\s*(xxl\.job\.(?:admin\.addresses|accessToken))\s*(?:=|:|\s)\s*(.*)$", body)
        if m:
            key = m.group(1)
            require(not continued and key not in found, "XXL-JOB properties 使用续行或重复键")
            found[key] = (index, body[:m.start(2)], m.group(2), "", line[len(body):])
        elif body.lstrip().lower().startswith("xxl"):
            require("\\" not in body and not re.search(r"access[-_]?token|admin.*addresses", body, re.I),
                    "XXL-JOB properties 属性存在非约定拼写或转义")
    return found


def rewrite(text, fmt, keys, admin, token):
    require(fmt in ("yaml", "properties") and keys and set(keys) <= KEYS
            and len(keys) == len(set(keys)), "配置格式或替换键清单无效")
    require(re.fullmatch(r"[A-Za-z0-9_.~-]{16,256}", token), "token 需为 16～256 位安全单行字符")
    admin = base_url(admin)
    lines = text.splitlines(keepends=True)
    located = locate_yaml(text) if fmt == "yaml" else locate_properties(text)
    require(ADDRESS not in keys or ADDRESS in located, "未找到明确的 admin.addresses，拒绝猜测或新增地址")
    require(located, "配置没有可定位的 XXL-JOB 属性")
    values = {ADDRESS: admin, TOKEN: token}
    for key in keys:
        value = json.dumps(values[key], ensure_ascii=False) if fmt == "yaml" else values[key]
        if key in located:
            index, prefix, _, suffix, newline = located[key]
            lines[index] = prefix + value + suffix + newline
        else:
            # 已有 XXL 配置但缺少 Token 时，新增一个明确的平铺属性，不修改 executor 等其他键。
            newline = "\r\n" if "\r\n" in text else "\n"
            if lines and not lines[-1].endswith(("\r", "\n")):
                lines[-1] += newline
            lines.append(key + (": " if fmt == "yaml" else "=") + value + newline)
    updated = "".join(lines)
    check = locate_yaml(updated) if fmt == "yaml" else locate_properties(updated)
    for key in keys:
        scalar = check[key][2]
        actual = json.loads(scalar) if fmt == "yaml" else scalar
        require(actual == values[key], "替换后属性校验失败")
    return updated


def load_scope(path):
    scope = json.loads(Path(path).read_text(encoding="utf-8"))
    require(isinstance(scope.get("namespace"), str) and scope["namespace"], "须填写明确的生产 namespace ID")
    targets = scope.get("targets")
    require(isinstance(targets, list) and 0 < len(targets) <= 100, "须提供明确的 DataId / Group 清单，不能留空或全量扫描")
    seen = set()
    for t in targets:
        require(isinstance(t, dict) and isinstance(t.get("dataId"), str) and isinstance(t.get("group"), str),
                "DataId / Group 缺失")
        pair = (t["group"], t["dataId"])
        require(all(v and not any(c in v for c in "*?\r\n") for v in pair)
                and pair not in seen and "wolease" not in t["dataId"].lower(), "清单重复、越界或含通配符")
        seen.add(pair)
        require(t.get("format") in ("yaml", "properties") and isinstance(t.get("keys"), list)
                and t["keys"] and set(t["keys"]) <= KEYS and len(t["keys"]) == len(set(t["keys"])),
                "每项须声明 format 和 XXL-JOB keys")
    return scope


def prepare(client, scope, auth, admin, token):
    plans = []
    for item in scope["targets"]:
        identity = {"tenant": scope["namespace"], "group": item["group"], "dataId": item["dataId"]}
        info = client.request("/v1/cs/configs", dict(identity, accessToken=auth, show="all"), method="GET")
        require(isinstance(info, dict) and isinstance(info.get("content"), str), "目标配置不存在或详情读取失败")
        require(info.get("dataId") == item["dataId"] and info.get("group") == item["group"]
                and info.get("tenant") == scope["namespace"], "配置身份与限定清单不符")
        require(not info.get("encryptedDataKey") and not item["dataId"].startswith("cipher-"),
                "加密插件配置不能按普通文本处理")
        require(info.get("type") in (("yaml", "yml") if item["format"] == "yaml" else ("properties",)),
                "Nacos 配置类型与清单不符")
        old = info["content"]
        require(info.get("md5") == md5(old), "配置正文与数据库摘要不一致")
        content = rewrite(old, item["format"], item["keys"], admin, token)
        form = dict(identity, content=content, type=info["type"], accessToken=auth)
        for request_key, response_key in (("appName", "appName"), ("desc", "desc"), ("use", "use"),
                ("effect", "effect"), ("schema", "schema"), ("config_tags", "configTags")):
            if info.get(response_key) is not None:
                form[request_key] = info[response_key]
        plans.append({"identity": identity, "form": form, "beforeMd5": md5(old),
                      "afterMd5": md5(content), "changed": old != content, "keys": item["keys"]})
    return plans


def publish(client, plans, auth, record):
    for plan in plans:
        info = {**plan["identity"], "keys": plan["keys"], "beforeMd5": plan["beforeMd5"],
                "afterMd5": plan["afterMd5"]}
        record(dict(info, state="requested" if plan["changed"] else "unchanged", at=now()))
        if plan["changed"]:
            result = client.request("/v1/cs/configs", plan["form"], headers={"casMd5": plan["beforeMd5"]})
            require(result is True, "发布失败或 CAS 冲突；停止，已发布项保留")
        # 客户端 GET 路径验证缓存，不能以控制台详情/数据库查询代替。
        for attempt in range(10):
            current = client.request("/v1/cs/configs", dict(plan["identity"], accessToken=auth), method="GET", raw=True)
            if md5(current) == plan["afterMd5"]:
                break
            time.sleep(1)
        else:
            raise MigrationError("发布后客户端取配置摘要未一致；停止并核实缓存/并发修改")
        record(dict(info, state="verified", at=now()))
        print("OK " + plan["identity"]["group"] + "/" + plan["identity"]["dataId"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--scope", required=True)
    parser.add_argument("--new-nacos", default=NEW_NACOS)
    parser.add_argument("--new-admin", default=NEW_ADMIN)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--new-center-confirmed", action="store_true",
                        help="声明已核实域名连接新 Nacos，且本范围新应用尚未启动")
    parser.add_argument("--report", default="nacos-xxl-result.json")
    args = parser.parse_args()
    scope = load_scope(args.scope)  # 清单不完整时先失败，不登录或取配置。
    require(not args.apply or args.new_center_confirmed,
            "发布须明确 --new-center-confirmed；核实新库归属并在新应用启动前执行")
    client = Http(args.new_nacos)
    user = os.environ.get("NACOS_USER") or input("新 Nacos 账号: ")
    login = client.request("/v1/auth/login", {"username": user,
        "password": secret("NACOS_PASSWORD", "新 Nacos 密码")})
    require(isinstance(login, dict) and login.get("accessToken"), "新 Nacos 登录失败")
    auth = login["accessToken"]
    token = secret("XXL_NEW_ACCESS_TOKEN", "新 XXL-JOB accessToken（与新 admin 一致）")
    plans = prepare(client, scope, auth, args.new_admin, token)
    for plan in plans:
        print("%s/%s keys=%s changed=%s" % (plan["identity"]["group"], plan["identity"]["dataId"],
              ",".join(plan["keys"]), plan["changed"]))
    if not args.apply:
        print("预览完成，未发布；正文和 token 未输出")
        return
    report_path = Path(args.report)
    require(not report_path.exists(), "报告文件已存在；重跑请使用新的 --report 文件名")
    report = {"createdAt": now(), "targetNacos": client.url, "events": []}
    write_private(report_path, report, exclusive=True)
    def record(event):
        report["events"].append(event)
        temp = Path(str(report_path) + ".tmp")
        write_private(temp, report)
        os.replace(temp, report_path)
    publish(client, plans, auth, record)
    print("全部限定配置已发布并从客户端接口回读；应用 SDK/gRPC 连通仍须从部署节点验收")


if __name__ == "__main__":
    try:
        main()
    except (MigrationError, OSError, ValueError, KeyError, TypeError) as exc:
        message = str(exc) if isinstance(exc, MigrationError) else type(exc).__name__
        print("失败：" + message, file=sys.stderr)
        sys.exit(1)
