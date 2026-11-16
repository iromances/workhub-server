package cn.aslight.workhub.service.system;

import cn.aslight.workhub.dao.system.DeveloperResourceMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.system.DeveloperResourceEntity;
import cn.aslight.workhub.model.system.DeveloperResourceResponse;
import cn.aslight.workhub.model.system.DeveloperResourceSaveRequest;
import cn.aslight.workhub.model.system.UserOptionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 研发人员资源服务。
 */
@Service
public class DeveloperResourceService {

    private final DeveloperResourceMapper developerResourceMapper;
    private final UserMapper userMapper;

    public DeveloperResourceService(DeveloperResourceMapper developerResourceMapper, UserMapper userMapper) {
        this.developerResourceMapper = developerResourceMapper;
        this.userMapper = userMapper;
    }

    public List<DeveloperResourceResponse> list(String keyword) {
        return developerResourceMapper.findAll(false, trimToNull(keyword)).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<UserOptionResponse> listOptions() {
        // 人员候选统一来自账号管理（sys_user），团队表不再作为身份来源。
        List<UserOptionResponse> options = userMapper.findActiveUsers();
        return options == null || options.isEmpty()
                ? List.of(new UserOptionResponse("admin", "admin", null))
                : options;
    }

    @Transactional
    public DeveloperResourceResponse create(DeveloperResourceSaveRequest request) {
        DeveloperResourceEntity entity = toEntity(new DeveloperResourceEntity(), request);
        requireActiveAccount(entity.getUserName());
        if (developerResourceMapper.findByUserName(entity.getUserName()) != null) {
            throw new IllegalArgumentException("研发人员已存在");
        }
        developerResourceMapper.insert(entity);
        return toResponse(developerResourceMapper.findByUserName(entity.getUserName()));
    }

    @Transactional
    public DeveloperResourceResponse update(Long id, DeveloperResourceSaveRequest request) {
        DeveloperResourceEntity existing = requireExisting(id);
        DeveloperResourceEntity duplicate = developerResourceMapper.findByUserName(requireValue(request.getUserName(), "研发人员用户名不能为空"));
        if (duplicate != null && !duplicate.getId().equals(id)) {
            throw new IllegalArgumentException("研发人员已存在");
        }
        requireActiveAccount(existing.getUserName());
        DeveloperResourceEntity entity = toEntity(existing, request);
        entity.setId(id);
        developerResourceMapper.update(entity);
        return toResponse(requireExisting(id));
    }

    @Transactional
    public void delete(Long id) {
        if (developerResourceMapper.deleteById(id) == 0) {
            throw new IllegalArgumentException("研发人员不存在");
        }
    }

    /**
     * 团队名单只允许登记账号管理中已存在的活跃账号，身份不再由本表自建。
     */
    private void requireActiveAccount(String userName) {
        String normalized = trimToNull(userName);
        if (normalized == null) {
            return;
        }
        boolean exists = userMapper.findActiveUsers().stream()
                .anyMatch(user -> normalized.equals(user.userName()));
        if (!exists) {
            throw new IllegalArgumentException("研发人员「" + normalized + "」不是有效账号，请先在账号管理中创建");
        }
    }

    private DeveloperResourceEntity requireExisting(Long id) {
        DeveloperResourceEntity entity = developerResourceMapper.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("研发人员不存在");
        }
        return entity;
    }

    private DeveloperResourceEntity toEntity(DeveloperResourceEntity entity, DeveloperResourceSaveRequest request) {
        entity.setUserName(requireValue(request.getUserName(), "研发人员用户名不能为空"));
        entity.setDisplayName(requireValue(request.getDisplayName(), "研发人员名称不能为空"));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setRemark(trimToNull(request.getRemark()));
        return entity;
    }

    private DeveloperResourceResponse toResponse(DeveloperResourceEntity entity) {
        return new DeveloperResourceResponse(
                entity.getId(),
                entity.getUserName(),
                entity.getDisplayName(),
                entity.getEnabled(),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
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
}
