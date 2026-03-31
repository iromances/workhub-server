package cn.aslight.workhub.domain.project.dto;

public record ProjectSummaryResponse(Long id,
                                     String code,
                                     String name,
                                     String type,
                                     String ownerUserName,
                                     String status) {
}
