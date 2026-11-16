"""本地模拟验证，不连接任何数据库或生产 HTTP 服务。"""
import copy
import io
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch

import nacos_xxl_patch as nacos
import xxl_job_cutover as xxl
from migration_http import Http, MigrationError, digest

TOKEN = "a" * 64
OLD = "http://old-admin.invalid/xxl-job-admin"
NEW = "https://new-admin.invalid/xxl-job-admin"


class XxlFake:
    def __init__(self, version):
        self.version = version
        self.calls = []
        self.groups, self.jobs = [], []
        self.fail_id = None
        self.timeout_after_commit = None
        for n, app in enumerate(xxl.APPS, 1):
            self.groups.append({"id": n, "appname": app, "addressType": 0,
                                "addressList": "http://%s-%s.invalid:9999/" % (version, n)})
            for offset in (0, 1):
                job = {k: "" for k in xxl.FIELDS}
                job.update(id=n * 10 + offset, jobGroup=n, jobDesc="任务 %s" % n,
                           executorHandler="handler", executorTimeout=0, executorFailRetryCount=1,
                           executorParam="不应落入快照的业务参数", glueSource="不应落入快照的源码",
                           triggerNextTime=0, triggerStatus=int(offset == 0 and version == "old"))
                if version == "old":
                    job["jobCron"] = "0 0/5 * * * ?"
                else:
                    job.update(scheduleType="CRON", scheduleConf="0 0/5 * * * ?", misfireStrategy="DO_NOTHING")
                self.jobs.append(job)
        self.jobs[0]["childJobId"] = "11"  # 停用子任务不能被一键启用。

    def pages(self, path, params):
        if path == "/jobgroup/pageList":
            return copy.deepcopy(self.groups)
        return copy.deepcopy([j for j in self.jobs if j["jobGroup"] == params["jobGroup"]])

    def request(self, path, params):
        self.calls.append((path, params["id"]))
        if params["id"] == self.fail_id:
            return {"code": 500}
        for j in self.jobs:
            if j["id"] == params["id"]:
                j.update(triggerStatus=1, triggerNextTime=9999999999999)
        if params["id"] == self.timeout_after_commit:
            raise MigrationError("模拟响应丢失")
        return {"code": 200}


class XxlTests(unittest.TestCase):
    def setUp(self):
        self.old = XxlFake("old")
        self.new = XxlFake("new")
        self.env = xxl.freeze(self.old, OLD)

    def test_snapshot_excludes_business_content_and_preserves_off(self):
        payload = json.dumps(self.env, ensure_ascii=False)
        self.assertNotIn("业务参数", payload)
        self.assertNotIn("源码", payload)
        self.assertEqual(sum(j["triggerStatus"] for j in self.env["snapshot"]["jobs"]), 6)

    def test_start_and_repeat_do_not_recalculate_started_jobs(self):
        progress, saved = {}, []
        with redirect_stdout(io.StringIO()):
            self.assertEqual(xxl.start_jobs(self.new, self.env, NEW, progress, lambda p: saved.append(copy.deepcopy(p))), 6)
            self.assertEqual(xxl.start_jobs(self.new, self.env, NEW, progress, lambda p: None), 6)
        self.assertEqual(self.new.calls, [("/jobinfo/start", i) for i in (10, 20, 30, 40, 50, 60)])
        self.assertTrue(all(j["triggerStatus"] == 0 for j in self.new.jobs if j["id"] % 10 == 1))
        self.assertEqual(saved[1]["states"]["10"]["state"], "requested")

    def test_failure_stops_remaining_and_does_not_rollback_completed(self):
        self.new.fail_id = 30
        progress = {}
        with redirect_stdout(io.StringIO()), self.assertRaises(MigrationError):
            xxl.start_jobs(self.new, self.env, NEW, progress, lambda p: None)
        self.assertEqual([i for _, i in self.new.calls], [10, 20, 30])
        self.assertEqual(progress["states"]["20"]["state"], "verified")
        with self.assertRaises(MigrationError):
            xxl.preflight(self.env, xxl.inventory(self.new, "new"), NEW, progress)

    def test_timeout_after_commit_is_reconciled_without_second_start(self):
        self.new.timeout_after_commit = 20
        progress = {}
        with redirect_stdout(io.StringIO()), self.assertRaises(MigrationError):
            xxl.start_jobs(self.new, self.env, NEW, progress, lambda p: None)
        self.new.timeout_after_commit = None
        with redirect_stdout(io.StringIO()):
            xxl.start_jobs(self.new, self.env, NEW, progress, lambda p: None)
        self.assertEqual([i for _, i in self.new.calls].count(20), 1)

    def test_definition_drift_before_any_mutation(self):
        self.new.jobs[-1]["executorParam"] = "changed"
        with self.assertRaises(MigrationError):
            xxl.start_jobs(self.new, self.env, NEW, {}, lambda p: None)
        self.assertEqual(self.new.calls, [])

    def test_off_became_on_or_missing_registration_blocks(self):
        self.new.jobs[1]["triggerStatus"] = 1
        with self.assertRaises(MigrationError):
            xxl.preflight(self.env, xxl.inventory(self.new, "new"), NEW, {})
        self.new.jobs[1]["triggerStatus"] = 0
        self.new.groups[0]["addressList"] = ""
        with self.assertRaises(MigrationError):
            xxl.preflight(self.env, xxl.inventory(self.new, "new"), NEW, {})

    def test_old_executor_or_same_center_blocks(self):
        with self.assertRaises(MigrationError):
            xxl.preflight(self.env, xxl.inventory(self.new, "new"), OLD, {})
        self.new.groups[0]["addressList"] = self.old.groups[0]["addressList"]
        with self.assertRaises(MigrationError):
            xxl.preflight(self.env, xxl.inventory(self.new, "new"), NEW, {})

    def test_snapshot_after_all_stopped_is_rejected(self):
        for j in self.old.jobs:
            j["triggerStatus"] = 0
        with self.assertRaises(MigrationError):
            xxl.freeze(self.old, OLD)

    def test_missing_task_or_unrecorded_started_task_blocks(self):
        self.new.jobs.pop()
        with self.assertRaises(MigrationError):
            xxl.preflight(self.env, xxl.inventory(self.new, "new"), NEW, {})
        self.new = XxlFake("new")
        self.new.jobs[0].update(triggerStatus=1, triggerNextTime=9999999999999)
        with self.assertRaises(MigrationError):
            xxl.preflight(self.env, xxl.inventory(self.new, "new"), NEW, {})

    def test_child_order_cycle_and_outside_scope(self):
        rows = [{"id": 1, "triggerStatus": 1, "children": [2]},
                {"id": 2, "triggerStatus": 1, "children": []}]
        self.assertEqual(xxl.activation_order(rows), [2, 1])
        rows[1]["children"] = [1]
        with self.assertRaises(MigrationError):
            xxl.activation_order(rows)
        self.old.jobs[0]["childJobId"] = "999"
        with self.assertRaises(MigrationError):
            xxl.freeze(self.old, OLD)

    def test_snapshot_tampering_detected(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "snapshot.json"
            self.env["snapshot"]["jobs"][0]["triggerStatus"] = 0
            path.write_text(json.dumps(self.env))
            with self.assertRaises(MigrationError):
                xxl.load_snapshot(path)


class NacosFake:
    def __init__(self, text):
        self.info = dict(dataId="example.yml", group="EXAMPLE", tenant="ns", type="yaml",
                         content=text, md5=nacos.md5(text), desc="描述保留", configTags="tag", encryptedDataKey="")
        self.posts = []
        self.cas_fail = False
        self.stale_reads = 0

    def request(self, path, params, method="POST", headers=None, raw=False):
        if method == "GET":
            if params.get("show") == "all":
                return copy.deepcopy(self.info)
            if self.stale_reads:
                self.stale_reads -= 1
                return "stale-cache"
            return self.info["content"]
        self.posts.append((copy.deepcopy(params), headers))
        if self.cas_fail:
            return False
        if headers["casMd5"] != self.info["md5"]:
            return False
        self.info["content"] = params["content"]
        self.info["md5"] = nacos.md5(params["content"])
        return True


class NacosTests(unittest.TestCase):
    def rewrite(self, text, fmt="yaml", keys=None):
        return nacos.rewrite(text, fmt, keys or [nacos.ADDRESS, nacos.TOKEN], NEW, TOKEN)

    def test_nested_yaml_keeps_unrelated_bytes_and_comments(self):
        text = "spring:\r\n  url: jdbc:untouched\r\nxxl:\r\n  job:\r\n    admin:\r\n      addresses: 'http://old.invalid' # admin\r\n    accessToken: old # token\r\n    executor:\r\n      port: 9999\r\n"
        changed = self.rewrite(text)
        self.assertEqual(changed, text.replace("'http://old.invalid'", json.dumps(NEW)).replace("old # token", json.dumps(TOKEN) + " # token"))
        self.assertEqual(self.rewrite(changed), changed)

    def test_flat_yaml_adds_missing_token_and_empty_token(self):
        text = "xxl.job.admin.addresses: http://old.invalid\n"
        self.assertIn('xxl.job.accessToken: "' + TOKEN + '"', self.rewrite(text))
        self.assertIn(TOKEN, self.rewrite(text + "xxl.job.accessToken:\n"))
        self.assertIn('" # token-comment', self.rewrite(text + "xxl.job.accessToken: # token-comment\n"))

    def test_properties_colon_equals_and_whitespace(self):
        for separator in ("=", ": ", " "):
            text = nacos.ADDRESS + separator + "old\n" + nacos.TOKEN + "=\nother=preserved\n"
            changed = self.rewrite(text, "properties")
            self.assertIn("other=preserved\n", changed)
            self.assertIn(NEW, changed)
            self.assertEqual(self.rewrite(changed, "properties"), changed)

    def test_duplicate_multidocument_alias_and_wrong_key_rejected(self):
        for text in (
            "xxl.job.admin.addresses: old\nxxl.job.admin.addresses: other\n",
            "xxl.job.admin.addresses: old\n---\nxxl.job.accessToken: old\n",
            "xxl.job.admin.addresses: old\n--- # second-document\nxxl.job.accessToken: old\n",
            'note: "multiline\nxxl.job.admin.addresses: fake\n"\n',
            "xxl: {job: {admin: {addresses: old}}}\n",
            "xxl: *shared\n", "xxl.job.admin.addresses: |\n  old\n",
            "xxl.job.admin.addresses: old\nxxl.job.access-token: old\n",
            "xxl.job.admin.addresses: old\n<<: *shared\n",
        ):
            with self.subTest(text=text), self.assertRaises(MigrationError):
                self.rewrite(text)

    def test_fake_key_inside_block_or_array_is_not_rewritten(self):
        text = "script: |\n  xxl.job.admin.addresses: untouched\nitems:\n  - name: entry\n    xxl.job.admin.addresses: untouched\nxxl.job.admin.addresses: old\n"
        changed = self.rewrite(text)
        self.assertEqual(changed.count("xxl.job.admin.addresses: untouched"), 2)
        with self.assertRaises(MigrationError):
            self.rewrite("script: |\n  xxl.job.admin.addresses: fake\n")

    def test_properties_continuation_or_duplicate_rejected(self):
        for text in (nacos.ADDRESS + "=old\\\ncontinued\n", (nacos.ADDRESS + "=old\n") * 2,
                     "\\u0078xl.job.admin.addresses=hidden\n" + nacos.ADDRESS + "=old\n"):
            with self.assertRaises(MigrationError):
                self.rewrite(text, "properties")

    def test_only_requested_key_is_replaced(self):
        text = "xxl.job.admin.addresses: old\nxxl.job.accessToken: original\n"
        self.assertIn("accessToken: original", self.rewrite(text, keys=[nacos.ADDRESS]))

    def test_scope_empty_duplicate_and_excluded_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "scope.json"
            for targets in ([], [{"dataId": "amp-wolease.yml", "group": "DEFAULT_GROUP"}],
                            [{"dataId": "*", "group": "DEFAULT_GROUP"}]):
                p.write_text(json.dumps({"namespace": "ns", "targets": targets}))
                with self.assertRaises(MigrationError):
                    nacos.load_scope(p)

    def plan(self, client):
        scope = {"namespace": "ns", "targets": [{"dataId": "example.yml", "group": "EXAMPLE",
                   "format": "yaml", "keys": [nacos.ADDRESS, nacos.TOKEN]}]}
        return nacos.prepare(client, scope, "auth-not-logged", NEW, TOKEN)

    def test_cas_metadata_cache_verification_and_idempotence(self):
        client = NacosFake("xxl.job.admin.addresses: old\n")
        plans = self.plan(client)
        self.assertEqual(client.posts, [])  # 预览不发布。
        self.assertEqual(plans[0]["form"]["desc"], "描述保留")
        self.assertEqual(plans[0]["form"]["config_tags"], "tag")
        records = []
        client.stale_reads = 2
        with redirect_stdout(io.StringIO()), patch("nacos_xxl_patch.time.sleep"):
            nacos.publish(client, plans, "auth-not-logged", records.append)
            nacos.publish(client, self.plan(client), "auth-not-logged", records.append)
        self.assertEqual(len(client.posts), 1)
        self.assertEqual(records[-1]["state"], "verified")
        self.assertNotIn(TOKEN, json.dumps(records))
        self.assertNotIn("auth-not-logged", json.dumps(records))

    def test_cas_conflict_stops_without_claiming_success(self):
        client = NacosFake("xxl.job.admin.addresses: old\n")
        plans = self.plan(client)
        client.cas_fail = True
        records = []
        with self.assertRaises(MigrationError):
            nacos.publish(client, plans, "auth", records.append)
        self.assertEqual([r["state"] for r in records], ["requested"])

    def test_wrong_namespace_or_encrypted_config_blocks_before_post(self):
        client = NacosFake("xxl.job.admin.addresses: old\n")
        client.info["tenant"] = "wrong-namespace"
        with self.assertRaises(MigrationError):
            self.plan(client)
        client.info["tenant"] = "ns"
        client.info["encryptedDataKey"] = "encrypted"
        with self.assertRaises(MigrationError):
            self.plan(client)
        self.assertEqual(client.posts, [])

    def test_partial_batch_failure_keeps_completed_item_and_stops(self):
        first = NacosFake("xxl.job.admin.addresses: old\n")
        second = NacosFake("xxl.job.admin.addresses: old\n")
        second.info["dataId"] = "second.yml"
        second.cas_fail = True
        class Batch:
            def request(self, path, params, **kwargs):
                c = first if params["dataId"] == "example.yml" else second
                return c.request(path, params, **kwargs)
        scope = {"namespace": "ns", "targets": [
            {"dataId": name, "group": "EXAMPLE", "format": "yaml", "keys": [nacos.ADDRESS, nacos.TOKEN]}
            for name in ("example.yml", "second.yml")]}
        batch = Batch()
        plans = nacos.prepare(batch, scope, "auth", NEW, TOKEN)
        events = []
        with redirect_stdout(io.StringIO()), self.assertRaises(MigrationError):
            nacos.publish(batch, plans, "auth", events.append)
        self.assertEqual([e["state"] for e in events], ["requested", "verified", "requested"])
        self.assertEqual(len(first.posts), 1)
        self.assertEqual(first.info["md5"], plans[0]["afterMd5"])


class PagingTests(unittest.TestCase):
    def test_multiple_pages_and_duplicate_detection(self):
        client = Http("https://unit.invalid")
        all_rows = [{"id": i} for i in range(213)]
        def request(path, params):
            start = params["start"]
            return {"recordsFiltered": 213, "data": all_rows[start:start + 100]}
        client.request = request
        self.assertEqual(len(client.pages("/pageList", {})), 213)
        client.request = lambda *a: {"recordsFiltered": 2, "data": [{"id": 1}, {"id": 1}]}
        with self.assertRaises(MigrationError):
            client.pages("/pageList", {})


if __name__ == "__main__":
    unittest.main()
