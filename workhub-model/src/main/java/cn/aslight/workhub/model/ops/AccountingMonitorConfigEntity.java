package cn.aslight.workhub.model.ops;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * REQ-d227e09d：账务场景监测配置（按业务场景维度声明预期并核对实际）。
 */
public record AccountingMonitorConfigEntity(Long id,
                                            String businessLineCode,
                                            String businessLineName,
                                            String environmentCode,
                                            String systemName,
                                            String displayName,
                                            String scenarioType,
                                            LocalTime expectedTime,
                                            LocalTime checkTime,
                                            String expectedCountSqlCode,
                                            Integer expectedFixedCount,
                                            String actualCountSqlCode,
                                            String successCountSqlCode,
                                            BigDecimal toleranceRate,
                                            Boolean alertEnabled,
                                            Boolean enabled,
                                            String remark,
                                            LocalDateTime createdAt,
                                            LocalDateTime updatedAt) {
}
