package cn.aslight.workhub.model.ops;

import java.time.Instant;
import java.util.List;

public record BusinessLogSearchResponse(String businessLineCode,
                                        String environmentCode,
                                        List<String> indexPatterns,
                                        List<String> serviceNames,
                                        List<String> levels,
                                        Instant from,
                                        Instant to,
                                        int limit,
                                        int count,
                                        boolean truncated,
                                        boolean dataMasked,
                                        List<ElkSystemAlertLog> items) {
}
