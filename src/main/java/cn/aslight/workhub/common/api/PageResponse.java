package cn.aslight.workhub.common.api;

import java.util.List;

public record PageResponse<T>(long total, List<T> items) {
}
