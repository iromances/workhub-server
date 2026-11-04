package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertScopeIngestionMapper;
import cn.aslight.workhub.model.ops.*;
import cn.aslight.workhub.observability.ElkAlertProperties;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ElkSystemAlertCollectorTest {
    @Test void disabledDoesNotAccessCollectionOrSource() {
        var source=mock(SystemAlertLogSource.class); var mapper=mock(SystemAlertScopeIngestionMapper.class); var store=mock(SystemAlertCollectionStore.class);
        new ElkSystemAlertCollector(new ElkAlertRuntimeConfigService(new ElkAlertProperties()),source,mapper,store).collect();
        verifyNoInteractions(source,mapper,store);
    }
    @Test void queriesFrozenClaimAndCommitsPage() {
        var source=mock(SystemAlertLogSource.class); var mapper=mock(SystemAlertScopeIngestionMapper.class); var store=mock(SystemAlertCollectionStore.class);
        var scope=scope(); prepare(mapper,scope); var claim=claim(scope); when(store.claim(any(),any(),anyInt(),anyInt())).thenReturn(claim);
        var page=new SystemAlertLogSource.ErrorPage(List.of(),null,true,false,null);
        when(source.readErrorPage(any(),any(),any(),any(),any(),any())).thenReturn(page);
        collector(source,mapper,store).collect();
        verify(source).readErrorPage(eq("workhub-*"),eq(List.of()),eq("TEST"),eq(claim.state().fromTime().toInstant(ZoneOffset.UTC)),eq(claim.state().toTime().toInstant(ZoneOffset.UTC)),isNull());
        verify(store).commit(claim,page); verify(store,never()).fail(any(),any(),anyBoolean());
    }
    @Test void expiredPitRestartsSameClaimInsteadOfCallingLegacyReader() {
        var source=mock(SystemAlertLogSource.class); var mapper=mock(SystemAlertScopeIngestionMapper.class); var store=mock(SystemAlertCollectionStore.class);
        var scope=scope(); prepare(mapper,scope); var claim=claim(scope); when(store.claim(any(),any(),anyInt(),anyInt())).thenReturn(claim);
        when(source.readErrorPage(any(),any(),any(),any(),any(),any())).thenThrow(new SystemAlertLogSource.RestartRequired("expired"));
        collector(source,mapper,store).collect(); verify(store).fail(claim,"expired",true);
        verify(source,never()).readErrors(any(),any(),any(),any(),any()); verify(store,never()).commit(any(),any());
    }
    @Test void transientErrorKeepsPositionAndNextScopeStillRuns() {
        var source=mock(SystemAlertLogSource.class); var mapper=mock(SystemAlertScopeIngestionMapper.class); var store=mock(SystemAlertCollectionStore.class);
        var scope=scope(); prepare(mapper,scope); var claim=claim(scope); when(store.claim(any(),any(),anyInt(),anyInt())).thenReturn(claim);
        when(source.readErrorPage(any(),any(),any(),any(),any(),any())).thenThrow(new IllegalStateException("temporarily unavailable"));
        collector(source,mapper,store).collect(); verify(store).fail(claim,"temporarily unavailable",false);
    }
    @Test void continuationOnlyReadsPendingEnabledScopesAndDoesNotStartCompletedOnes() {
        var source=mock(SystemAlertLogSource.class); var mapper=mock(SystemAlertScopeIngestionMapper.class); var store=mock(SystemAlertCollectionStore.class);
        var scope=scope(); prepare(mapper,scope); when(store.pending()).thenReturn(List.of(99L));
        collector(source,mapper,store).resume(); verify(store,never()).claim(any(),any(),anyInt(),anyInt()); verifyNoInteractions(source);
    }
    private void prepare(SystemAlertScopeIngestionMapper mapper,SystemAlertScopeEntity scope) {
        when(mapper.findEnabledScopes()).thenReturn(List.of(scope)); when(mapper.findEnabledRules()).thenReturn(List.of());
        when(mapper.findIndexPatterns(any())).thenReturn(List.of(new SystemAlertScopeIndexEntity(1L,1L,"workhub-*",0)));
        when(mapper.findServices(any())).thenReturn(List.of());
    }
    private ElkSystemAlertCollector collector(SystemAlertLogSource source,SystemAlertScopeIngestionMapper mapper,SystemAlertCollectionStore store) {
        var props=new ElkAlertProperties();props.setEnabled(true);
        return new ElkSystemAlertCollector(new ElkAlertRuntimeConfigService(props),source,mapper,store);
    }
    private SystemAlertScopeEntity scope() {
        var scope=new SystemAlertScopeEntity();scope.setId(1L);scope.setEnabled(true);scope.setBusinessLineCode("BL000012");scope.setEnvironmentCode("TEST");scope.setWatchMode("ALL");
        scope.setIndexPatterns(List.of("workhub-*"));scope.setIndexPatternExpression("workhub-*");return scope;
    }
    private SystemAlertCollectionStore.Claim claim(SystemAlertScopeEntity scope) {
        var at=LocalDateTime.of(2026,11,4,8,0);
        return new SystemAlertCollectionStore.Claim(new SystemAlertCollectionState(1L,"window","{}","a".repeat(64),at.minusMinutes(5),at,null,null,null,"ACTIVE","owner",at.plusMinutes(3),0,0,1,at),new SystemAlertCollectionStore.Definition(scope,List.of()));
    }
}
