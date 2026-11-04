package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.*;
import tools.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;

/** Jenkins 固定构建的受信任归档回执；成功状态本身不能证明实际执行了测试。 */
final class AgentBuildTestReceipt {
    static Verified verify(JsonNode value,AgentBuildTestEntity request,AgentJenkinsPolicy.BindingSnapshot target,
                           long build,Map<String,String> parameters,Instant now) {
        if (value == null || !value.isObject() || integer(value,"schemaVersion",1,1) != 1
                || !"WORKHUB_BUILD_TEST".equals(text(value,"type")) || !request.id().equals(text(value,"requestId"))
                || !request.manifestHash().equals(text(value,"manifestHash")) || !"BUILD_TEST".equals(target.binding().stage())
                || !"TEST".equals(target.binding().scope().environmentCode())
                || !String.join("/",target.binding().jobSegments()).equals(text(value,"jobFullName"))
                || !value.path("buildNumber").isIntegralNumber() || !value.path("buildNumber").canConvertToLong() || value.path("buildNumber").asLong() != build || build < 1) throw invalid();
        var scope = value.path("scope"); var expected = target.binding().scope();
        if (!expected.businessLineCode().equals(text(scope,"businessLineCode")) || !expected.environmentCode().equals(text(scope,"environmentCode"))) throw invalid();
        var commits = new TreeMap<String,String>();
        if (!value.path("testedCommits").isObject()) throw invalid();
        for (var entry : value.path("testedCommits").properties()) {
            String parameter = target.binding().commitParameters().get(entry.getKey());
            if (parameter == null || !entry.getValue().isString()) throw invalid();
            String sha = AgentCodeGit.sha(entry.getValue().asText()); if (!sha.equals(parameters.get(parameter))) throw invalid();
            commits.put(entry.getKey(),sha);
        }
        if (!commits.keySet().equals(target.binding().commitParameters().keySet())) throw invalid();
        int buildExit = integer(value,"buildExitCode",-1,255), testExit = integer(value,"testExitCode",-1,255);
        int tests = integer(value,"tests",0,10000000), failures = integer(value,"failures",0,10000000),
                errors = integer(value,"errors",0,10000000), skipped = integer(value,"skipped",0,10000000);
        if ((long)failures + errors + skipped > tests) throw invalid();
        Instant started,finished;
        try { started=Instant.parse(text(value,"startedAt")); finished=Instant.parse(text(value,"finishedAt")); }
        catch (DateTimeException ex) { throw invalid(); }
        if (finished.isBefore(started) || started.isBefore(request.createdAt().toInstant(ZoneOffset.UTC).minusSeconds(120)) || finished.isAfter(now)) throw invalid();
        String summary=text(value,"summary"); if (summary.length()>16000) throw invalid();
        return new Verified(1,"WORKHUB_BUILD_TEST",request.id(),request.manifestHash(),expected,String.join("/",target.binding().jobSegments()),build,
                Collections.unmodifiableMap(commits),buildExit,testExit,tests,failures,errors,skipped,started.toString(),finished.toString(),summary);
    }
    private static int integer(JsonNode value,String name,int min,int max) {
        var field=value.path(name); if (!field.isIntegralNumber() || !field.canConvertToInt() || field.asLong()<min || field.asLong()>max) throw invalid(); return field.asInt();
    }
    private static String text(JsonNode value,String name) { if (!value.path(name).isString()) throw invalid(); return value.path(name).asText(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("独立测试回执与固定请求、准确提交、TEST 范围或实际执行统计不一致"); }
    record Verified(int schemaVersion,String type,String requestId,String manifestHash,AgentScope scope,String jobFullName,long buildNumber,
            Map<String,String> testedCommits,int buildExitCode,int testExitCode,int tests,int failures,int errors,int skipped,
            String startedAt,String finishedAt,String summary) {
        boolean passed() { return buildExitCode==0 && testExitCode==0 && tests>skipped && failures==0 && errors==0; }
    }
}
