package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertScopeIngestionMapper;
import cn.aslight.workhub.model.ops.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class ElkSystemAlertCollector {
    private static final Logger log=LoggerFactory.getLogger(ElkSystemAlertCollector.class);
    private final AtomicBoolean running=new AtomicBoolean();
    private final ElkAlertRuntimeConfigService configService;
    private final SystemAlertLogSource source;
    private final SystemAlertScopeIngestionMapper ingestionMapper;
    private final SystemAlertCollectionStore store;

    public ElkSystemAlertCollector(ElkAlertRuntimeConfigService configService,SystemAlertLogSource source,
            SystemAlertScopeIngestionMapper ingestionMapper,SystemAlertCollectionStore store) {
        this.configService=configService; this.source=source; this.ingestionMapper=ingestionMapper; this.store=store;
    }
    public void collect() { collect(false); }
    public void resume() { collect(true); }
    private void collect(boolean pendingOnly) {
        if(!running.compareAndSet(false,true)) return;
        try {
            var config=configService.current(null);
            if(!config.enabled()) return;
            var pending=pendingOnly?new HashSet<>(store.pending()):Set.<Long>of();
            if(pendingOnly && pending.isEmpty()) return;
            var scopes=ingestionMapper.findEnabledScopes().stream().filter(scope->!pendingOnly || pending.contains(scope.getId())).toList();
            attachScopeChildren(scopes);
            var rules=ingestionMapper.findEnabledRules(); attachRuleKeywords(rules);
            for(var scope:scopes) collectScope(scope,rules,config);
        } catch(Exception ex) { log.warn("ELK采集调度失败，原因={}",safeError(ex)); }
        finally { running.set(false); }
    }
    private void collectScope(SystemAlertScopeEntity scope,List<SystemAlertRuleEntity> rules,ElkAlertRuntimeConfig config) {
        SystemAlertCollectionStore.Claim claim=null;
        try {
            claim=store.claim(scope,rules,config.initialLookbackMinutes(),config.requestTimeoutSeconds());
            if(claim==null) return;
            var fixed=claim.definition().scope(); var state=claim.state();
            var page=source.readErrorPage(fixed.getIndexPatternExpression(),SystemAlertCollectionStore.selected(fixed),fixed.getEnvironmentCode(),
                    state.fromTime().toInstant(ZoneOffset.UTC),state.toTime().toInstant(ZoneOffset.UTC),store.position(state));
            store.commit(claim,page);
        } catch(Exception ex) {
            String message=safeError(ex);
            try {
                if(claim!=null) store.fail(claim,message,ex instanceof SystemAlertLogSource.RestartRequired);
                else ingestionMapper.saveFailure(scope.getId(),message);
            } catch(Exception stateError) { log.warn("保存ELK采集失败状态失败，scopeId={}，原因={}",scope.getId(),stateError.getClass().getSimpleName()); }
            log.warn("ELK系统预警采集未完成，scopeId={}，原因={}",scope.getId(),message);
        }
    }

    private void attachScopeChildren(List<SystemAlertScopeEntity> scopes) {
        if (scopes.isEmpty()) {
            return;
        }
        List<Long> ids = scopes.stream().map(SystemAlertScopeEntity::getId).toList();
        Map<Long, List<SystemAlertScopeServiceEntity>> servicesByScope = ingestionMapper.findServices(ids).stream()
                .collect(Collectors.groupingBy(SystemAlertScopeServiceEntity::scopeId));
        Map<Long, List<String>> patternsByScope = ingestionMapper.findIndexPatterns(ids).stream()
                .collect(Collectors.groupingBy(
                        SystemAlertScopeIndexEntity::scopeId,
                        Collectors.mapping(SystemAlertScopeIndexEntity::indexPattern, Collectors.toList())
                ));
        for (SystemAlertScopeEntity scope : scopes) {
            scope.setServices(servicesByScope.getOrDefault(scope.getId(), List.of()));
            List<String> patterns = patternsByScope.getOrDefault(scope.getId(), List.of());
            scope.setIndexPatterns(patterns);
            scope.setIndexPatternExpression(patterns.isEmpty() ? null : String.join(",", patterns));
        }
    }

    private void attachRuleKeywords(List<SystemAlertRuleEntity> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        List<Long> ids = rules.stream().map(SystemAlertRuleEntity::getId).toList();
        Map<Long, List<String>> keywordsByRule = ingestionMapper.findRuleKeywords(ids).stream()
                .collect(Collectors.groupingBy(
                        SystemAlertRuleKeywordEntity::ruleId,
                        Collectors.mapping(SystemAlertRuleKeywordEntity::keyword, Collectors.toList())
                ));
        for (SystemAlertRuleEntity rule : rules) {
            rule.setKeywords(keywordsByRule.getOrDefault(rule.getId(), List.of()));
        }
    }

    private String safeError(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        return abbreviate(message.replaceAll("(?i)(apikey|authorization|password|token)\\s*[:=]\\s*\\S+", "$1=***"), 1_000);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }
}
