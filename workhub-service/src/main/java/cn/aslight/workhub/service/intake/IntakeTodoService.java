package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.intake.IntakeTodoMapper;
import cn.aslight.workhub.model.attachment.AttachmentResponse;
import cn.aslight.workhub.model.intake.IntakeHistoryEntity;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeTodoCreateRequest;
import cn.aslight.workhub.model.intake.IntakeTodoEntity;
import cn.aslight.workhub.model.intake.IntakeTodoResponse;
import cn.aslight.workhub.model.intake.IntakeTodoStatusRequest;
import cn.aslight.workhub.model.intake.IntakeTodoUpdateRequest;
import cn.aslight.workhub.service.attachment.AttachmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 需求待办服务。
 */
@Service
public class IntakeTodoService {

    static final String STATUS_PENDING = "待处理";
    static final String STATUS_PROCESSING = "处理中";
    static final String STATUS_COMPLETED = "已完成";
    static final String STATUS_CANCELED = "已取消";

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            STATUS_PENDING,
            STATUS_PROCESSING,
            STATUS_COMPLETED,
            STATUS_CANCELED
    );

    private final IntakeMapper intakeMapper;
    private final IntakeTodoMapper intakeTodoMapper;
    private final IntakeHistoryMapper intakeHistoryMapper;
    private final AttachmentService attachmentService;

    public IntakeTodoService(IntakeMapper intakeMapper,
                             IntakeTodoMapper intakeTodoMapper,
                             IntakeHistoryMapper intakeHistoryMapper,
                             AttachmentService attachmentService) {
        this.intakeMapper = intakeMapper;
        this.intakeTodoMapper = intakeTodoMapper;
        this.intakeHistoryMapper = intakeHistoryMapper;
        this.attachmentService = attachmentService;
    }

    public List<IntakeTodoResponse> list(Long intakeId) {
        requireIntake(intakeId);
        List<IntakeTodoEntity> todos = intakeTodoMapper.findByIntakeId(intakeId);
        Map<Long, List<AttachmentResponse>> screenshots = mapScreenshots(todos);
        return todos.stream()
                .map(todo -> toResponse(todo, screenshots))
                .toList();
    }

    @Transactional
    public IntakeTodoResponse create(Long intakeId, IntakeTodoCreateRequest request, String operatorUserName) {
        requireIntake(intakeId);
        IntakeTodoEntity entity = new IntakeTodoEntity();
        entity.setIntakeId(intakeId);
        entity.setTitle(requireValue(request.getTitle(), "待办标题不能为空"));
        entity.setContent(trimToNull(request.getContent()));
        entity.setTodoStatus(STATUS_PENDING);
        entity.setAssigneeUserName(trimToNull(request.getAssigneeUserName()));
        entity.setPlannedAt(request.getPlannedAt());
        entity.setCompletedAt(null);
        entity.setProcessResult(null);
        intakeTodoMapper.insert(entity);
        recordHistory(intakeId, "新增需求待办", buildCreateHistory(entity), operatorUserName);
        return findCreatedOrUpdated(intakeId, entity.getId());
    }

    @Transactional
    public IntakeTodoResponse update(Long intakeId,
                                     Long todoId,
                                     IntakeTodoUpdateRequest request,
                                     String operatorUserName) {
        requireIntake(intakeId);
        IntakeTodoEntity existing = requireTodo(intakeId, todoId);
        IntakeTodoEntity next = new IntakeTodoEntity();
        next.setId(todoId);
        next.setIntakeId(intakeId);
        next.setTitle(requireValue(request.getTitle(), "待办标题不能为空"));
        next.setContent(trimToNull(request.getContent()));
        next.setTodoStatus(existing.getTodoStatus());
        next.setAssigneeUserName(trimToNull(request.getAssigneeUserName()));
        next.setPlannedAt(request.getPlannedAt());
        next.setCompletedAt(existing.getCompletedAt());
        next.setProcessResult(existing.getProcessResult());
        intakeTodoMapper.updateEditableFields(next);
        recordHistory(intakeId, "更新需求待办", buildUpdateHistory(existing, next), operatorUserName);
        return findCreatedOrUpdated(intakeId, todoId);
    }

    @Transactional
    public IntakeTodoResponse updateStatus(Long intakeId,
                                           Long todoId,
                                           IntakeTodoStatusRequest request,
                                           String operatorUserName) {
        requireIntake(intakeId);
        IntakeTodoEntity existing = requireTodo(intakeId, todoId);
        String nextStatus = requireAllowedStatus(request.getStatus());
        String processResult = trimToNull(request.getProcessResult());
        if (STATUS_COMPLETED.equals(nextStatus) && processResult == null) {
            throw new IllegalArgumentException("待办完成时必须填写处理结果");
        }
        IntakeTodoEntity next = new IntakeTodoEntity();
        next.setId(todoId);
        next.setIntakeId(intakeId);
        next.setTitle(existing.getTitle());
        next.setContent(existing.getContent());
        next.setTodoStatus(nextStatus);
        next.setAssigneeUserName(existing.getAssigneeUserName());
        next.setPlannedAt(existing.getPlannedAt());
        next.setCompletedAt(STATUS_COMPLETED.equals(nextStatus)
                ? (request.getCompletedAt() == null ? LocalDateTime.now() : request.getCompletedAt())
                : null);
        next.setProcessResult(processResult);
        intakeTodoMapper.updateStatus(next);
        recordHistory(intakeId, "更新需求待办状态", buildStatusHistory(existing, next), operatorUserName);
        return findCreatedOrUpdated(intakeId, todoId);
    }

    /**
     * 保存待办截图变更：删除指定截图并追加新截图，两步在同一事务内完成。
     * 没有新增也没有删除时不写数据、不记历史。
     */
    @Transactional
    public IntakeTodoResponse saveScreenshots(Long intakeId,
                                              Long todoId,
                                              List<MultipartFile> files,
                                              List<Long> removedIds,
                                              String operatorUserName) {
        requireIntake(intakeId);
        IntakeTodoEntity existing = requireTodo(intakeId, todoId);
        List<MultipartFile> newFiles = pickImageFiles(files);
        List<Long> removedScreenshotIds = removedIds == null
                ? List.of()
                : removedIds.stream().filter(Objects::nonNull).distinct().toList();
        if (newFiles.isEmpty() && removedScreenshotIds.isEmpty()) {
            return findCreatedOrUpdated(intakeId, todoId);
        }
        List<AttachmentResponse> screenshots = todoScreenshots(todoId);
        List<String> removedFileNames = new java.util.ArrayList<>();
        for (Long removedId : removedScreenshotIds) {
            removedFileNames.add(screenshots.stream()
                    .filter(item -> removedId.equals(item.id()))
                    .findFirst()
                    .map(AttachmentResponse::fileName)
                    .orElseThrow(() -> new IllegalArgumentException("截图不存在或不属于当前待办")));
        }
        if (!removedScreenshotIds.isEmpty()) {
            attachmentService.deleteTodoScreenshots(todoId, removedScreenshotIds);
        }
        attachmentService.saveTodoScreenshots(todoId, newFiles);
        recordHistory(intakeId, "更新需求待办截图", buildScreenshotHistory(existing, newFiles, removedFileNames), operatorUserName);
        return findCreatedOrUpdated(intakeId, todoId);
    }

    @Transactional
    public void delete(Long intakeId, Long todoId, String operatorUserName) {
        requireIntake(intakeId);
        IntakeTodoEntity existing = requireTodo(intakeId, todoId);
        intakeTodoMapper.deleteById(todoId);
        attachmentService.deleteAllTodoScreenshots(todoId);
        recordHistory(intakeId, "删除需求待办", buildDeleteHistory(existing), operatorUserName);
    }

    private IntakeRecordEntity requireIntake(Long intakeId) {
        IntakeRecordEntity intake = intakeMapper.findById(intakeId);
        if (intake == null) {
            throw new IllegalArgumentException("需求不存在");
        }
        return intake;
    }

    private IntakeTodoEntity requireTodo(Long intakeId, Long todoId) {
        IntakeTodoEntity todo = intakeTodoMapper.findById(todoId);
        if (todo == null || !intakeId.equals(todo.getIntakeId())) {
            throw new IllegalArgumentException("需求待办不存在");
        }
        return todo;
    }

    private IntakeTodoResponse findCreatedOrUpdated(Long intakeId, Long todoId) {
        return intakeTodoMapper.findByIntakeId(intakeId).stream()
                .filter(item -> todoId.equals(item.getId()))
                .findFirst()
                .map(item -> toResponse(item, todoScreenshots(todoId)))
                .orElseGet(() -> toResponse(requireTodo(intakeId, todoId), todoScreenshots(todoId)));
    }

    /** 取出有内容的图片文件；待办截图只收图片。 */
    private List<MultipartFile> pickImageFiles(List<MultipartFile> files) {
        List<MultipartFile> imageFiles = new java.util.ArrayList<>();
        if (files == null) {
            return imageFiles;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String contentType = trimToNull(file.getContentType());
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new IllegalArgumentException("截图仅支持图片文件：" + file.getOriginalFilename());
            }
            imageFiles.add(file);
        }
        return imageFiles;
    }

    private Map<Long, List<AttachmentResponse>> mapScreenshots(List<IntakeTodoEntity> todos) {
        return attachmentService.mapTodoScreenshots(todos.stream().map(IntakeTodoEntity::getId).toList());
    }

    private List<AttachmentResponse> todoScreenshots(Long todoId) {
        return attachmentService.mapTodoScreenshots(List.of(todoId)).getOrDefault(todoId, List.of());
    }

    private String requireAllowedStatus(String status) {
        String normalized = requireValue(status, "待办状态不能为空");
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("待办状态不合法");
        }
        return normalized;
    }

    private IntakeTodoResponse toResponse(IntakeTodoEntity entity, Map<Long, List<AttachmentResponse>> screenshots) {
        return toResponse(entity, screenshots.getOrDefault(entity.getId(), List.of()));
    }

    private IntakeTodoResponse toResponse(IntakeTodoEntity entity, List<AttachmentResponse> screenshots) {
        return new IntakeTodoResponse(
                entity.getId(),
                entity.getIntakeId(),
                entity.getTitle(),
                entity.getContent(),
                entity.getTodoStatus(),
                entity.getAssigneeUserName(),
                entity.getPlannedAt(),
                entity.getCompletedAt(),
                entity.getProcessResult(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                screenshots
        );
    }

    private String buildCreateHistory(IntakeTodoEntity entity) {
        List<String> details = new java.util.ArrayList<>();
        details.add("待办标题：" + defaultHistoryValue(entity.getTitle()));
        details.add("待办内容：" + defaultHistoryValue(entity.getContent()));
        details.add("处理人：" + defaultHistoryValue(entity.getAssigneeUserName()));
        details.add("计划处理时间：" + defaultHistoryValue(formatTime(entity.getPlannedAt())));
        details.add("处理状态：" + STATUS_PENDING);
        return String.join("\n", details);
    }

    private String buildUpdateHistory(IntakeTodoEntity before, IntakeTodoEntity after) {
        List<String> changes = new java.util.ArrayList<>();
        appendChange(changes, "待办标题", before.getTitle(), after.getTitle());
        appendChange(changes, "待办内容", before.getContent(), after.getContent());
        appendChange(changes, "处理人", before.getAssigneeUserName(), after.getAssigneeUserName());
        appendChange(changes, "计划处理时间", formatTime(before.getPlannedAt()), formatTime(after.getPlannedAt()));
        return changes.isEmpty() ? "未发生字段变化" : String.join("\n", changes);
    }

    private String buildStatusHistory(IntakeTodoEntity before, IntakeTodoEntity after) {
        List<String> changes = new java.util.ArrayList<>();
        appendChange(changes, "处理状态", before.getTodoStatus(), after.getTodoStatus());
        appendChange(changes, "处理结果", before.getProcessResult(), after.getProcessResult());
        appendChange(changes, "完成时间", formatTime(before.getCompletedAt()), formatTime(after.getCompletedAt()));
        return changes.isEmpty() ? "未发生字段变化" : String.join("\n", changes);
    }

    private String buildScreenshotHistory(IntakeTodoEntity todo,
                                          List<MultipartFile> newFiles,
                                          List<String> removedFileNames) {
        List<String> details = new java.util.ArrayList<>();
        details.add("待办标题：" + defaultHistoryValue(todo.getTitle()));
        if (!removedFileNames.isEmpty()) {
            details.add("删除截图：" + String.join("、", removedFileNames));
        }
        if (!newFiles.isEmpty()) {
            details.add("新增截图：" + String.join("、", newFiles.stream().map(MultipartFile::getOriginalFilename).toList()));
        }
        return String.join("\n", details);
    }

    private String buildDeleteHistory(IntakeTodoEntity entity) {
        List<String> details = new java.util.ArrayList<>();
        details.add("待办标题：" + defaultHistoryValue(entity.getTitle()));
        details.add("待办内容：" + defaultHistoryValue(entity.getContent()));
        details.add("处理人：" + defaultHistoryValue(entity.getAssigneeUserName()));
        details.add("计划处理时间：" + defaultHistoryValue(formatTime(entity.getPlannedAt())));
        details.add("处理状态：" + defaultHistoryValue(entity.getTodoStatus()));
        details.add("处理结果：" + defaultHistoryValue(entity.getProcessResult()));
        return String.join("\n", details);
    }

    private void appendChange(List<String> changes, String label, String before, String after) {
        String beforeValue = defaultHistoryValue(before);
        String afterValue = defaultHistoryValue(after);
        if (!beforeValue.equals(afterValue)) {
            changes.add(label + "：" + beforeValue + " -> " + afterValue);
        }
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private String defaultHistoryValue(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? "-" : normalized;
    }

    private String requireValue(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void recordHistory(Long intakeId, String summary, String detail, String operatorUserName) {
        IntakeHistoryEntity entity = new IntakeHistoryEntity();
        entity.setIntakeId(intakeId);
        entity.setActionType("UPDATE");
        entity.setActionSummary(summary);
        entity.setDetailText(detail);
        entity.setOperatorUserName(trimToNull(operatorUserName) == null ? "system" : operatorUserName.trim());
        intakeHistoryMapper.insert(entity);
    }
}
