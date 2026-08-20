package cn.aslight.workhub.model.ops;

import java.util.List;

public record AccountingRunDetailResponse(AccountingRunEntity run,
                                          List<AccountingResultEntity> results) {
}
