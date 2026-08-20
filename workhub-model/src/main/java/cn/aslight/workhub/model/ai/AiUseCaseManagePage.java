package cn.aslight.workhub.model.ai;

import java.util.List;

/** 与 asset 管理端一致的分页结构。 */
public record AiUseCaseManagePage(long current, long size, long total, long pages,
                                  List<AiUseCaseConfigResponse> records,
                                  List<AiUseCaseConfigResponse> items) {
    public static AiUseCaseManagePage of(List<AiUseCaseConfigResponse> all, int page, int pageSize) {
        int current = Math.max(page, 1);
        int size = Math.min(Math.max(pageSize, 1), 200);
        int from = Math.min((current - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        List<AiUseCaseConfigResponse> records = all.subList(from, to);
        long pages = all.isEmpty() ? 0 : (all.size() + (long) size - 1) / size;
        return new AiUseCaseManagePage(current, size, all.size(), pages, records, records);
    }
}
