package cn.aslight.workhub.model.ops;

import java.util.List;

public record BusinessLogSearchRequest(String businessLineCode,
                                       String environmentCode,
                                       List<String> serviceNames,
                                       List<String> levels,
                                       String from,
                                       String to,
                                       String phrase,
                                       String traceId,
                                       String requestId,
                                       Integer limit) {
}
