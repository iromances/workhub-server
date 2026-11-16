#!/usr/bin/env python3
"""REQ-e11b477a 三系统集成官方API接口目录——种子数据生成脚本。

从官方对接文档抓取并解析接口条目，产出种子 SQL 与来源清单：
- GitLab：https://docs.gitlab.com/ee/api/api_resources.html 索引 → 各资源页端点表。
- 禅道：https://www.zentao.net/book/api/{id}.html 官方手册逐接口章节。
- Jenkins：官方 Remote Access API 文档为叙述式（无统一枚举清单），
  按文档条目 + Jenkins 核心端点整理，source_note 注明口径。

仅使用标准库；缓存与产出可重跑（除抓取时间外两次产出一致）。
用法：python3 generate_api_catalog.py [--no-cache]
"""

import hashlib
import json
import re
import sys
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

BASE = Path(__file__).resolve().parent
SEED_DIR = BASE.parent / "种子"
CACHE = Path("/tmp/apidocs-cache")
USER_AGENT = "WorkHub-ApiCatalog-Generator/1.0 (+local seed generation)"
FETCH_DELAY = 0.25

GITLAB_INDEX = "https://docs.gitlab.com/ee/api/api_resources.html"
GITLAB_PAGE = "https://docs.gitlab.com/ee/api/{slug}.html"
# 索引中的导航/规范页，不算 REST 资源组（记录于来源清单）
GITLAB_EXCLUDED = {"api_resources", "rest", "get_started", "openapi", "graphql", "metadata"}
ZENTAO_SITEMAP = "https://www.zentao.net/sitemap.xml"
ZENTAO_CHAPTER = "https://www.zentao.net/book/api/{id}.html"
JENKINS_DOC = "https://www.jenkins.io/doc/book/using/remote-access-api/"

# 禅道资源组中文名（未命中时用原始段名）
ZENTAO_GROUP_NAMES = {
    "tokens": "认证Token", "users": "用户", "departments": "部门", "programs": "项目集",
    "products": "产品", "product": "产品", "productlines": "产品线", "stories": "需求",
    "requirements": "需求", "productplans": "计划", "plans": "计划", "releases": "发布",
    "builds": "版本", "projects": "项目", "executions": "执行", "tasks": "任务",
    "bugs": "Bug", "cases": "测试用例", "testtasks": "测试单", "testreports": "测试报告",
    "caseresults": "用例结果", "todos": "待办", "feedbacks": "反馈", "tickets": "工单",
    "apps": "应用", "issues": "问题", "entry": "应用入口", "executions-statistics": "执行统计",
}

# Jenkins 整理口径：官方 Remote Access API 文档条目 + Jenkins 核心广泛文档化端点
JENKINS_ENDPOINTS = [
    ("remote-access", "Remote Access API", "实例信息（JSON，支持 tree 参数裁剪字段）", "GET", "/api/json"),
    ("remote-access", "Remote Access API", "实例信息（XML）", "GET", "/api/xml"),
    ("remote-access", "Remote Access API", "CSRF Crumb 获取", "GET", "/crumbIssuer/api/json"),
    ("job", "Job 管理", "Job 详情（可构建性、构建列表）", "GET", "/job/{name}/api/json"),
    ("job", "Job 管理", "Job 下文件夹遍历", "GET", "/job/{folder}/job/{name}/api/json"),
    ("job", "Job 管理", "启用 Job", "POST", "/job/{name}/enable"),
    ("job", "Job 管理", "停用 Job", "POST", "/job/{name}/disable"),
    ("build", "构建", "触发自由风格构建", "POST", "/job/{name}/build"),
    ("build", "构建", "触发参数化构建", "POST", "/job/{name}/buildWithParameters"),
    ("build", "构建", "构建详情（结果、参数、耗时）", "GET", "/job/{name}/{number}/api/json"),
    ("build", "构建", "控制台日志全文", "GET", "/job/{name}/{number}/consoleText"),
    ("build", "构建", "控制台日志渐进尾读", "GET", "/job/{name}/{number}/logText/progressiveText"),
    ("build", "构建", "中断进行中的构建", "POST", "/job/{name}/{number}/stop"),
    ("build", "构建", "强制作业空间上的构建中断", "POST", "/job/{name}/{number}/term"),
    ("build", "构建", "归档产物读取", "GET", "/job/{name}/{number}/artifact/{path}"),
    ("queue", "队列", "队列快照", "GET", "/queue/api/json"),
    ("queue", "队列", "队列条目详情", "GET", "/queue/item/{number}/api/json"),
    ("queue", "队列", "取消队列条目", "POST", "/queue/item/{number}/cancelQueue"),
    ("computer", "节点", "节点（agent）列表与状态", "GET", "/computer/api/json"),
    ("view", "视图", "视图详情", "GET", "/view/{name}/api/json"),
    ("system", "系统管理", "安全退出（等待构建完成后停机）", "POST", "/safeExit"),
    ("system", "系统管理", "立即重启", "POST", "/restart"),
    ("system", "系统管理", "进入静默模式", "POST", "/quietDown"),
    ("system", "系统管理", "取消静默模式", "POST", "/cancelQuietDown"),
    ("plugin", "插件管理", "插件列表", "GET", "/pluginManager/api/json"),
]
JENKINS_NOTE = "官方 Remote Access API 文档为叙述式、无统一端点枚举清单；本组按官方文档条目 + Jenkins 核心广泛文档化端点整理"

# 平台已调用标记（调用点：ZentaoClient/IntegrationQueryService、AgentJenkinsClient、
# GitlabRepositoryService/AgentGitLabClient/IntegrationProbeService）；
# 按 HTTP 方法 + 逐段精确匹配，不做子树前缀泛配
PLATFORM_USED = {
    "ZENTAO": [
        ("POST", r"^/api\.php/v1/tokens$"),
        ("GET", r"^/api\.php/v1/projects/\{id\}/builds$"),
        ("POST", r"^/api\.php/v1/projects/\{id\}/builds$"),
        ("POST", r"^/api\.php/v2/testtasks$"),
        ("GET", r"^/api\.php/v1/stories/\{id\}$"),
        ("GET", r"^/api\.php/v1/tasks/\{id\}$"),
        ("GET", r"^/api\.php/v1/executions/\{id\}$"),
        ("GET", r"^/api\.php/v1/products/\{id\}/projects$"),
        ("GET", r"^/api\.php/v1/projects/\{id\}/executions$"),
        ("GET", r"^/api\.php/v1/products/\{id\}/bugs$"),
        ("GET", r"^/api\.php/v1/executions/\{id\}/bugs$"),
        ("GET", r"^/api\.php/v1/bugs/\{id\}$"),
    ],
    "JENKINS": [
        ("GET", r"^/api/json$"),
        ("GET", r"^/job(/[^/]+)+/api/json$"),
        ("POST", r"^/job(/[^/]+)+/buildWithParameters$"),
        ("GET", r"^/job(/[^/]+)+/\{number\}/api/json$"),
        ("GET", r"^/job(/[^/]+)+/\{number\}/logText/progressiveText$"),
        ("GET", r"^/job(/[^/]+)+/\{number\}/artifact/"),
        ("GET", r"^/queue/api/json$"),
        ("GET", r"^/queue/item/\{number\}/api/json$"),
    ],
    "GITLAB": [
        ("GET", r"^/user$"),
        ("GET", r"^/projects$"),
        ("GET", r"^/groups/[^/]+/projects$"),
        ("GET", r"^/projects/[^/]+$"),
        ("GET", r"^/projects/[^/]+/repository/branches$"),
        ("GET", r"^/projects/[^/]+/repository/branches/[^/]+$"),
        ("GET", r"^/projects/[^/]+/merge_requests$"),
        ("POST", r"^/projects/[^/]+/merge_requests$"),
        ("GET", r"^/projects/[^/]+/merge_requests/[^/]+$"),
    ],
}

use_cache = "--no-cache" not in sys.argv


def looks_like_bot_challenge(body: str) -> bool:
    """zentao.net 反爬挑战页特征：EO_Bot_Ssid 挑战脚本（约 1KB，无正文）。"""
    return "EO_Bot" in body[:4000] or ("_0x" in body[:1000] and len(body) < 5000)


def fetch(url: str) -> str:
    CACHE.mkdir(parents=True, exist_ok=True)
    key = hashlib.sha1(url.encode()).hexdigest()
    cached = CACHE / key
    if use_cache and cached.exists():
        body = cached.read_text(encoding="utf-8", errors="ignore")
        if not looks_like_bot_challenge(body):
            return body
    body = ""
    for attempt in range(4):
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = resp.read().decode("utf-8", errors="ignore")
        if not looks_like_bot_challenge(body):
            break
        time.sleep(1.5 + attempt)  # 挑战页：退避后重试
    cached.write_text(body, encoding="utf-8")
    time.sleep(FETCH_DELAY)
    return body


def strip_tags(html: str) -> str:
    html = re.sub(r"<script.*?</script>|<style.*?</style>", " ", html, flags=re.S)
    return re.sub(r"<[^>]+>", "\n", html)


def normalize_path(path: str) -> str:
    path = path.strip().replace("&nbsp;", "").replace("\xa0", "")
    if not path.startswith("/"):
        path = "/" + path
    path = re.sub(r"\?.*$", "", path)  # 查询串不入目录路径（tree= 等按说明处理）
    # 官方路径占位统一为 {name} 形式（GitLab :id、禅道 {id} 统一）
    path = re.sub(r":([A-Za-z_][A-Za-z0-9_]*)", r"{\1}", path)
    return path


def platform_used(system: str, method: str, path: str) -> bool:
    return any(m == method and re.match(p, path) for m, p in PLATFORM_USED.get(system, []))


def sql_escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace("'", "''")


def parse_gitlab():
    index = fetch(GITLAB_INDEX)
    slugs = {}
    for slug in re.findall(r"href=/api/([a-z0-9_-]+)/", index):
        slugs.setdefault(slug)
    # 资源组显示名取索引链接文本
    names = dict(re.findall(r'href=/api/([a-z0-9_-]+)/[^>]*>([^<]{2,80})</a>', index))
    rows, degraded, failed = [], [], []
    for slug in sorted(slugs):
        if slug in GITLAB_EXCLUDED:
            continue
        try:
            page = fetch(GITLAB_PAGE.format(slug=slug))
        except Exception as ex:
            degraded.append({"group": slug, "reason": f"抓取失败: {ex}"})
            continue
        # 端点签名：<span class=cl>GET /projects/:id</span>（属性引号可有可无）
        found = 0
        matches = list(re.finditer(
            r'<span class=["\']?cl["\']?>\s*(GET|POST|PUT|DELETE|PATCH)\s+(/\S*?)\s*</span>', page))
        for m in matches:
            method, path = m.group(1), normalize_path(m.group(2))
            head = page[:m.start()]
            hs = list(re.finditer(r"<h[1-4][^>]*>(.*?)</h[1-4]>", head, flags=re.S))
            name = re.sub(r"<[^>]+>", "", hs[-1].group(1)).strip() if hs else slug
            name = re.sub(r"\s+", " ", name)[:200]
            rows.append({"group": slug, "group_name": names.get(slug, slug), "name": name,
                         "method": method, "path": path})
            found += 1
        if not found:
            degraded.append({"group": slug, "reason": "页面未解析出端点签名（降级为组名+文档链接）"})
        elif found < 2:
            # 单端点页也可能正常（小资源组），不视为降级
            pass
    stats = {"groups": len([s for s in slugs if s not in GITLAB_EXCLUDED]),
             "endpoints": len(rows), "degraded": degraded, "failed_fetch": failed}
    return rows, stats


def parse_zentao():
    sitemap = fetch(ZENTAO_SITEMAP)
    ids = sorted(set(re.findall(r"https://www\.zentao\.net/book/api/(\d+)\.html", sitemap)),
                 key=int)
    rows, degraded = [], []
    for cid in ids:
        url = ZENTAO_CHAPTER.format(id=cid)
        try:
            page = fetch(url)
        except Exception as ex:
            degraded.append({"group": f"chapter-{cid}", "reason": f"抓取失败: {ex}"})
            continue
        title_m = re.search(r"<title>([^<]*)</title>", page)
        title = title_m.group(1).split("-")[0].strip() if title_m else ""
        text = strip_tags(page).replace("&nbsp;", " ").replace("\xa0", " ")
        # 接口章节签名有两种排版：①独立方法行紧跟 / 开头相对路径行（POST
        # /bugs/id/confirm）；②方法行后先出现“请求URL”再给完整地址（POST →
        # https://xxx.com/api.php/v2/testtasks）。教程/导航章节无此结构。
        sig = re.search(r"^\s*(GET|POST|PUT|DELETE|PATCH)\s*\n\s*(/[^\s]+)\s*$",
                        text, flags=re.M)
        url_m = re.search(r"api\.php/(v1|v2)/[^\s\n\"<]+", text)
        if sig:
            method, rel = sig.group(1), sig.group(2)
        else:
            m = re.search(r"^\s*(GET|POST|PUT|DELETE|PATCH)\s*$"
                          r"(?:\s*\n[^\n]*){0,3}?\s*\n[^\n]*api\.php/(v1|v2)/[^\s\n\"<]+",
                          text, flags=re.M)
            if not m or not url_m:
                degraded.append({"group": f"chapter-{cid}", "title": title,
                                 "reason": "非接口章节（无方法+路径签名块，如教程/配置/导航）"})
                continue
            method = m.group(1)
            rel = "/" + url_m.group(0).split(".php/" + url_m.group(1) + "/", 1)[1].split()[0]
        if not url_m:
            degraded.append({"group": f"chapter-{cid}", "title": title,
                             "reason": "非接口章节（未找到 api.php 请求地址）"})
            continue
        version = url_m.group(1)
        # 官方手册占位符为字面 id 段（/bugs/id），统一归一为 {id}
        rel = re.sub(r"(^|/)id(/|$)", r"\1{id}\2", rel)
        path = "/api.php/" + version + "/" + normalize_path(rel).lstrip("/")
        seg = rel.strip("/").split("/")[0].split("?")[0]
        group = re.sub(r"[^a-z-]", "", seg.lower()) or "misc"
        rows.append({"group": group, "group_name": ZENTAO_GROUP_NAMES.get(group, group),
                     "name": title or (group + " 接口"), "method": method,
                     "path": path, "chapter": url})
    stats = {"chapters": len(ids), "endpoints": len(rows), "degraded": degraded}
    return rows, stats


def build_jenkins():
    rows = []
    for group, group_name, name, method, path in JENKINS_ENDPOINTS:
        rows.append({"group": group, "group_name": group_name, "name": name,
                     "method": method, "path": normalize_path(path)})
    return rows, {"chapters": len(JENKINS_ENDPOINTS), "endpoints": len(rows), "degraded": []}


def render_sql(system_code: str, rows, source_note_default: str) -> str:
    rows = sorted(rows, key=lambda r: (r["group"], r["method"], r["path"]))
    out = [f"-- {system_code} 官方 API 接口目录种子（REQ-e11b477a，由 generate_api_catalog.py 生成）",
           f"-- 生成时间：{datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}",
           "-- 幂等键：system_code + resource_group + http_method + api_path", ""]
    CHUNK = 100
    for i in range(0, len(rows), CHUNK):
        chunk = rows[i:i + CHUNK]
        selects = []
        for order, r in enumerate(chunk):
            used = 1 if platform_used(system_code, r["method"], r["path"]) else 0
            selects.append(
                "SELECT '{sc}' AS system_code, '{g}' AS resource_group, '{gn}' AS resource_group_name, "
                "'{n}' AS api_name, '{m}' AS http_method, '{p}' AS api_path, NULL AS summary, "
                "{u} AS platform_used, '{su}' AS source_url, '{sn}' AS source_note, {so} AS sort_order".format(
                    sc=sql_escape(system_code), g=sql_escape(r["group"]), gn=sql_escape(r["group_name"]),
                    n=sql_escape(r["name"]), m=r["method"], p=sql_escape(r["path"]),
                    u=used, su=sql_escape(r.get("chapter", JENKINS_DOC if system_code == "JENKINS" else GITLAB_INDEX)),
                    sn=sql_escape(r.get("note", source_note_default)), so=(i + order + 1) * 10))
        out.append("INSERT INTO `sys_integration_api_endpoint` "
                   "(`system_code`, `resource_group`, `resource_group_name`, `api_name`, `http_method`, "
                   "`api_path`, `summary`, `platform_used`, `source_url`, `source_note`, `sort_order`)")
        out.append("SELECT s.* FROM (" + "\n  UNION ALL ".join(selects) + "\n) s")
        out.append("WHERE NOT EXISTS (")
        out.append("  SELECT 1 FROM `sys_integration_api_endpoint` e")
        out.append("  WHERE e.`system_code` = s.`system_code` AND e.`resource_group` = s.`resource_group`")
        out.append("    AND e.`http_method` = s.`http_method` AND e.`api_path` = s.`api_path`")
        out.append(");")
        out.append("")
    return "\n".join(out)


def dedup(rows):
    seen, result = set(), []
    for r in rows:
        key = (r["group"], r["method"], r["path"])
        if key in seen:
            continue
        seen.add(key)
        result.append(r)
    return result


def main():
    SEED_DIR.mkdir(parents=True, exist_ok=True)
    manifest = {"generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                "sources": {}, "systems": {}}

    gitlab_rows, gitlab_stats = parse_gitlab()
    gitlab_rows = dedup(gitlab_rows)
    (SEED_DIR / "gitlab_apis.sql").write_text(
        render_sql("GITLAB", gitlab_rows, "docs.gitlab.com REST API 资源文档"), encoding="utf-8")
    gitlab_stats["endpoints"] = len(gitlab_rows)  # 去重后实际入库行数
    manifest["systems"]["GITLAB"] = gitlab_stats
    manifest["sources"]["GITLAB"] = {"index": GITLAB_INDEX,
                                     "excludedSlugs": sorted(GITLAB_EXCLUDED)}

    zentao_rows, zentao_stats = parse_zentao()
    zentao_rows = dedup(zentao_rows)
    # 平台调用补充：官方手册未收录、平台实际在用并经部署实例（21.7.8）验证的端点
    zentao_rows += [
        {"group": "products", "group_name": "产品", "name": "获取产品关联项目列表",
         "method": "GET", "path": "/api.php/v1/products/{id}/projects",
         "note": "平台调用补充：官方手册未收录该 v1 端点，WorkHub 提测链路在用并经部署实例验证"},
        {"group": "executions", "group_name": "执行", "name": "获取执行Bug列表（v1）",
         "method": "GET", "path": "/api.php/v1/executions/{id}/bugs",
         "note": "平台调用补充：官方手册收录 v2 形态，WorkHub 使用 v1 形态并经部署实例验证"},
    ]
    (SEED_DIR / "zentao_apis.sql").write_text(
        render_sql("ZENTAO", zentao_rows, "zentao.net 二次开发手册 API 章节"), encoding="utf-8")
    zentao_stats["endpoints"] = len(zentao_rows)  # 去重+平台补充行后的实际入库行数
    manifest["systems"]["ZENTAO"] = zentao_stats
    manifest["sources"]["ZENTAO"] = {"sitemap": ZENTAO_SITEMAP, "book": "https://www.zentao.net/book/api.html"}

    jenkins_rows, jenkins_stats = build_jenkins()
    (SEED_DIR / "jenkins_apis.sql").write_text(
        render_sql("JENKINS", jenkins_rows, JENKINS_NOTE), encoding="utf-8")
    manifest["systems"]["JENKINS"] = jenkins_stats
    manifest["sources"]["JENKINS"] = {"doc": JENKINS_DOC, "note": JENKINS_NOTE}

    for system, rows in (("GITLAB", gitlab_rows), ("ZENTAO", zentao_rows), ("JENKINS", jenkins_rows)):
        matched = [f'{r["method"]} {r["path"]}' for r in rows
                   if platform_used(system, r["method"], r["path"])]
        manifest["systems"][system]["platformUsed"] = len(matched)
        manifest["systems"][system]["platformUsedMatches"] = matched

    (SEED_DIR / "来源清单.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: {"endpoints": v.get("endpoints"), "platformUsed": v.get("platformUsed"),
                          "degraded": len(v.get("degraded", []))}
                      for k, v in manifest["systems"].items()}, ensure_ascii=False))


if __name__ == "__main__":
    main()
