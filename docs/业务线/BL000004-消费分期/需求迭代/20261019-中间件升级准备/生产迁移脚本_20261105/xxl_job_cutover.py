#!/usr/bin/env python3
"""XXL-JOB 2.2.0 -> 2.5.0：旧侧快照、新侧预检、按快照批量启用。"""
import argparse
import fcntl
import json
import os
import sys
from pathlib import Path

from migration_http import Http, MigrationError, base_url, digest, now, require, write_private

NEW_ADMIN = "https://amp-xxl-job.xxingtong.com/xxl-job-admin"
APPS = ("amp-risk", "amp-order", "amp-capital", "amp-saps", "amp-payment", "amp-ops-reporting")
FIELDS = ("jobDesc", "author", "alarmEmail", "executorRouteStrategy", "executorHandler",
          "executorParam", "executorBlockStrategy", "executorTimeout", "executorFailRetryCount",
          "glueType", "glueSource", "childJobId")


def children(job):
    value = job["childJobId"] or ""
    require(isinstance(value, str), "子任务字段类型错误")
    try:
        ids = [int(x.strip()) for x in value.split(",") if x.strip()]
    except ValueError:
        raise MigrationError("子任务 ID 无效") from None
    require(all(x > 0 for x in ids) and len(set(ids)) == len(ids), "子任务 ID 重复或无效")
    return ids


def definition(job, app, version):
    require(all(k in job for k in FIELDS), "任务定义字段不完整，不能证明迁移一致")
    result = {k: job[k] for k in FIELDS}
    result.update(id=job["id"], jobGroup=job["jobGroup"], appname=app,
                  scheduleType="CRON", childJobId=children(job))
    if version == "old":
        require("jobCron" in job, "源端不是预期的 2.2.0 任务结构")
        result["scheduleConf"] = job["jobCron"]
    else:
        require(job.get("scheduleType") == "CRON" and job.get("misfireStrategy") == "DO_NOTHING",
                "新端调度类型或错过调度策略不符合迁移映射")
        result["scheduleConf"] = job["scheduleConf"]
    require(job.get("triggerStatus") in (0, 1), "未知任务启停状态")
    return result


def inventory(client, version):
    groups = client.pages("/jobgroup/pageList", {"appname": "", "title": ""})
    selected = [g for g in groups if g.get("appname") in APPS]
    require(len(selected) == len(APPS) and {g["appname"] for g in selected} == set(APPS),
            "六组执行器缺失或 AppName 重复")
    result, addresses = {}, {}
    for group in selected:
        require(group.get("addressType") == 0, "发现手动地址执行器，请单独核对后调整方案")
        addresses[group["appname"]] = sorted(set(
            base_url(v.strip()) for v in (group.get("addressList") or "").split(",") if v.strip()))
        jobs = client.pages("/jobinfo/pageList", {"jobGroup": group["id"], "triggerStatus": -1,
            "jobDesc": "", "executorHandler": "", "author": ""})
        require(jobs, "范围内执行器任务为空，拒绝生成不完整清单")
        for job in jobs:
            require(job["jobGroup"] == group["id"] and job["id"] not in result,
                    "任务组错误或任务 ID 重复")
            defined = definition(job, group["appname"], version)
            result[job["id"]] = {"id": job["id"], "appname": group["appname"],
                "definitionSha256": digest(defined), "triggerStatus": job["triggerStatus"],
                "children": children(job), "nextTime": job.get("triggerNextTime", 0)}
    for row in result.values():
        require(all(i in result for i in row["children"]), "子任务超出六组迁移范围或已缺失")
    return {"jobs": sorted(result.values(), key=lambda x: x["id"]), "addresses": addresses}


def stable_part(inv):
    return [{k: v for k, v in j.items() if k != "nextTime"} for j in inv["jobs"]]


def freeze(client, source_url):
    first, second = inventory(client, "old"), inventory(client, "old")
    require(stable_part(first) == stable_part(second), "两次读取的定义或启停状态不同，请冻结编辑后重试")
    require(any(j["triggerStatus"] for j in second["jobs"]), "源端已全部停用，不能覆盖停用前清单")
    body = {"version": 1, "createdAt": now(), "sourceAdmin": base_url(source_url),
            "apps": list(APPS), **second}
    return {"sha256": digest(body), "snapshot": body}


def load_snapshot(path):
    env = json.loads(Path(path).read_text(encoding="utf-8"))
    body = env["snapshot"]
    require(env["sha256"] == digest(body), "快照摘要不匹配")
    require(body["version"] == 1 and body["apps"] == list(APPS), "快照范围或版本不正确")
    ids = [j["id"] for j in body["jobs"]]
    require(ids and len(ids) == len(set(ids)) and any(j["triggerStatus"] == 1 for j in body["jobs"]),
            "快照无效或无启用任务")
    return env


def activation_order(rows):
    by_id = {j["id"]: j for j in rows}
    order, active, done = [], set(), set()
    def visit(job_id):
        require(job_id in by_id, "子任务超出快照范围")
        require(job_id not in active, "任务存在循环依赖")
        if job_id in done:
            return
        active.add(job_id)
        for child in by_id[job_id]["children"]:
            visit(child)
        active.remove(job_id)
        done.add(job_id)
        if by_id[job_id]["triggerStatus"] == 1:
            order.append(job_id)
    for job_id in sorted(by_id):
        visit(job_id)
    return order


def preflight(env, current, target_url, progress):
    snap = env["snapshot"]
    require(base_url(target_url) != snap["sourceAdmin"], "新旧中心入口相同")
    expected = {j["id"]: j for j in snap["jobs"]}
    actual = {j["id"]: j for j in current["jobs"]}
    require(set(expected) == set(actual), "新旧任务 ID 清单不一致")
    if progress:
        require(progress["snapshotSha256"] == env["sha256"] and progress["targetAdmin"] == target_url,
                "进度文件属于另一个快照或中心")
    previous = (progress or {}).get("states", {})
    for job_id, old in expected.items():
        new = actual[job_id]
        require(old["appname"] == new["appname"] and old["definitionSha256"] == new["definitionSha256"],
                "任务 %s 的执行器或定义有变化，停止操作" % job_id)
        if old["triggerStatus"] == 0:
            require(new["triggerStatus"] == 0, "原 STOP 任务 %s 已被启用" % job_id)
        elif new["triggerStatus"] == 1:
            require(str(job_id) in previous, "任务 %s 在本次交接前已启用，请核实" % job_id)
            require(new["nextTime"] > 0, "任务 %s 已启用但下次时间无效" % job_id)
        else:
            require(str(job_id) not in previous, "任务 %s 曾发起启用但现在停用，须人工核实，禁止盲目重试" % job_id)
    for app in APPS:
        urls = current["addresses"][app]
        require(urls, app + " 在新中心没有注册地址")
        old_urls = {u for values in snap["addresses"].values() for u in values}
        require(not (set(urls) & old_urls), app + " 的新注册地址仍指向旧执行器")
    return actual, activation_order(snap["jobs"])


def start_jobs(client, env, target_url, progress, save):
    current = inventory(client, "new")
    actual, order = preflight(env, current, target_url, progress)
    if not progress:
        progress.update(snapshotSha256=env["sha256"], targetAdmin=target_url, states={}, startedAt=now())
    save(progress)
    for job_id in order:
        if actual[job_id]["triggerStatus"] == 1:
            if progress["states"][str(job_id)]["state"] != "verified":
                progress["states"][str(job_id)] = {"state": "verified", "at": now(),
                    "observedNextTime": actual[job_id]["nextTime"]}
                save(progress)
            print("SKIP 已启用 jobId=%s" % job_id)
            continue
        progress["states"][str(job_id)] = {"state": "requested", "at": now()}
        save(progress)  # 先记录意图；超时也不能把未知结果当作未执行。
        response = client.request("/jobinfo/start", {"id": job_id})
        require(isinstance(response, dict) and response.get("code") == 200,
                "任务 %s 启用失败；停止后续操作，需核实该任务状态" % job_id)
        current = inventory(client, "new")
        actual, _ = preflight(env, current, target_url, progress)
        require(actual[job_id]["triggerStatus"] == 1, "任务 %s 启用后回读未生效" % job_id)
        progress["states"][str(job_id)] = {"state": "verified", "at": now(),
                                           "observedNextTime": actual[job_id]["nextTime"]}
        save(progress)
        print("OK jobId=%s nextTime=%s" % (job_id, actual[job_id]["nextTime"]))
    progress["completedAt"] = now()
    save(progress)
    return len(order)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("snapshot", help="旧任务停用前保存清单，不修改旧中心")
    p.add_argument("--old-admin", required=True)
    p.add_argument("--out", default="xxl-before-stop.json")
    for name in ("check", "start"):
        p = sub.add_parser(name)
        p.add_argument("--snapshot", required=True)
        p.add_argument("--new-admin", default=NEW_ADMIN)
        p.add_argument("--progress", default="xxl-start-progress.json")
        if name == "start":
            p.add_argument("--apply", action="store_true", help="不传此项只预检")
            p.add_argument("--old-center-isolated", action="store_true",
                           help="声明已隔离旧侧全部触发且在途、重试、子任务、回调已收敛")
    args = parser.parse_args()
    if args.command == "snapshot":
        require(not Path(args.out).exists(), "快照文件已存在，请使用新的文件名，不能覆盖")
        client = Http(args.old_admin)
        client.xxl_login("XXL_OLD")
        env = freeze(client, args.old_admin)
        write_private(args.out, env, exclusive=True)
        print("已保存 %s 条任务，ON=%s；停止旧任务前保管好此文件" %
              (len(env["snapshot"]["jobs"]), sum(j["triggerStatus"] for j in env["snapshot"]["jobs"])))
        return
    env = load_snapshot(args.snapshot)
    target = base_url(args.new_admin)
    require(target != env["snapshot"]["sourceAdmin"], "新旧入口相同")
    apply = args.command == "start" and args.apply
    require(not apply or args.old_center_isolated, "执行前必须明确 --old-center-isolated；该声明不能代替实际隔离")
    client = Http(target)
    client.xxl_login("XXL_NEW")
    path = Path(args.progress)
    with open(str(path) + ".lock", "a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        progress = json.loads(path.read_text()) if path.exists() else {}
        if not apply:
            current = inventory(client, "new")
            actual, order = preflight(env, current, target, progress)
            print("预检通过：计划 ON=%s，保持 STOP=%s，已启用=%s；未发起启用" %
                  (len(order), len(actual) - len(order), sum(j["triggerStatus"] for j in actual.values())))
            print("启用顺序 jobId=" + ",".join(map(str, order)))
        else:
            def save(value):
                tmp = Path(str(path) + ".tmp")
                write_private(tmp, value)
                os.replace(tmp, path)
            count = start_jobs(client, env, target, progress, save)
            print("已核对恢复 %s 条计划任务；漏跑批次另按台账补齐" % count)


if __name__ == "__main__":
    try:
        main()
    except (MigrationError, OSError, ValueError, KeyError, TypeError) as exc:
        # 协议字段异常不输出对象，避免将执行参数带入异常日志。
        message = str(exc) if isinstance(exc, MigrationError) else type(exc).__name__
        print("失败：" + message, file=sys.stderr)
        sys.exit(1)
