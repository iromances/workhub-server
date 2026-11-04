package cn.aslight.workhub.model.ops;

import java.time.Instant;
import java.util.List;

/** 系统生成的区间及内容核查结果；业务判断仍由Agent作出。 */
public record SystemAlertQueryEvidence(int schemaVersion,String executionId,Definition definition,String definitionHash,
        String windowId,String targetBindingHash,String receiptChainHash,int pageCount,Instant startedAt,Instant finishedAt,
        Instant collectionStartedAt,Instant collectionFinishedAt,Instant from,Instant to,Instant availableFrom,Instant availableThrough,
        boolean complete,List<String> problems,List<BusinessLogQueryEvidence.Activity> activities,
        int ignoredCount,List<ElkSystemAlertLog> ignoredEvents,List<SystemAlertRuleEntity> filterRules) {
    public record Definition(Long scopeId,String businessLineCode,String environmentCode,String watchMode,
            List<String> indexPatterns,List<String> serviceNames,String collectionDefinitionHash,String recordTimeZone) { }
}
