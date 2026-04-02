package cn.aslight.workhub.common.api;

import java.util.List;

/**
 * 分页响应模型。
 */
public record PageResponse<T>(long total, List<T> items) {
}
