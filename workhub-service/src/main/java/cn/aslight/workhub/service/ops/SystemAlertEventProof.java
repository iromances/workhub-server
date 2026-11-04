package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.model.ops.*;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** 展示名称可重命名；业务范围、主键、时间和正文必须与采集时一致。 */
final class SystemAlertEventProof {
    private SystemAlertEventProof() { }
    static String hash(ObjectMapper json, SystemAlertStoredEvent value) {
        return hash(json, new SystemAlertEventResponse(value.id(),value.businessLineCode(),value.environmentCode(),null,
                value.serviceName(),value.level(),value.eventCategory(),value.title(),value.message(),value.errorType(),
                value.stackTrace(),value.traceId(),value.requestId(),value.occurredAt(),value.sourceType()));
    }
    static String hash(ObjectMapper json, SystemAlertEventResponse value) {
        return AccountingExecutionEvidenceService.hash(json.writeValueAsString(new SystemAlertEventResponse(value.id(),
                value.businessLineCode(),value.environmentCode(),null,value.serviceName(),value.level(),value.eventCategory(),
                value.title(),value.message(),value.errorType(),value.stackTrace(),value.traceId(),value.requestId(),value.occurredAt(),value.sourceType())));
    }
    static boolean matches(SystemAlertStoredEvent stored, SystemAlertScopeEntity scope,
            ElkSystemAlertLog event, String category, ZoneId zone) {
        if (stored == null || stored.occurredAt() == null) return false;
        var sourceTime=LocalDateTime.ofInstant(event.occurredAt(),zone);
        var floor=sourceTime.truncatedTo(ChronoUnit.SECONDS);
        // 旧DATETIME(0)可能截断或舍入小数秒，不能接受其他时间偏差。
        if (!stored.occurredAt().equals(sourceTime) && !stored.occurredAt().equals(floor)
                && !(sourceTime.getNano()>=500_000_000 && stored.occurredAt().equals(floor.plusSeconds(1)))) return false;
        var expected=new SystemAlertStoredEvent(stored.id(),scope.getBusinessLineCode(),scope.getEnvironmentCode(),event.serviceName(),
                event.level(),category,event.title(),event.message(),event.errorType(),event.stackTrace(),event.traceId(),event.requestId(),stored.occurredAt(),"ELK",event.sourceEventId());
        return Objects.equals(stored,expected);
    }
}
