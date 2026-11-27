package cn.aslight.workhub.domain.auth.service;

import cn.aslight.workhub.dao.system.SysUserMapper;
import cn.aslight.workhub.model.auth.UserProfileResponse;
import cn.aslight.workhub.model.attachment.AttachmentEntity;
import cn.aslight.workhub.model.system.SysUserEntity;
import cn.aslight.workhub.security.JwtTokenService;
import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.service.auth.AuthService;
import cn.aslight.workhub.service.auth.PermissionContextService;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceAvatarTest {
    private static final byte[] PNG = "avatar".getBytes(StandardCharsets.UTF_8);

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final PermissionContextService permissionContextService = mock(PermissionContextService.class);
    private final SystemAuditService auditService = mock(SystemAuditService.class);
    private final AttachmentService attachmentService = mock(AttachmentService.class);
    private final SysUserEntity user = new SysUserEntity();
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(mock(PasswordEncoder.class), mock(JwtTokenService.class), userMapper,
                permissionContextService, auditService, attachmentService);
        user.setId(3L);
        user.setUserName("alice");
        when(permissionContextService.requireActiveUser("alice")).thenReturn(user);
        when(permissionContextService.roleCodes(3L)).thenReturn(List.of());
        when(permissionContextService.permissionCodes(3L)).thenReturn(List.of());
        // 真实实现会更新用户行，这里同步到内存对象，便于核对返回的资料。
        doAnswer(call -> {
            user.setAvatarUrl(call.getArgument(1));
            return 1;
        }).when(userMapper).updateAvatar(eq(3L), any());
    }

    @Test
    void uploadAvatarShouldStoreRowAndPointProfileToIt() {
        when(attachmentService.saveUserAvatar(eq(3L), any())).thenReturn(77L);

        UserProfileResponse profile = service.uploadAvatar("alice", png("avatar.png"), "127.0.0.1");

        assertEquals("/api/auth/avatars/77", profile.avatarUrl());
        verify(userMapper).updateAvatar(3L, "/api/auth/avatars/77");
        verify(attachmentService, never()).deleteUserAvatar(any());
        verify(auditService).operation(eq("alice"), isNull(), eq("UPLOAD_AVATAR"), eq("SYS_USER"), eq("3"),
                isNull(), eq("上传个人头像"), eq("SUCCESS"), isNull(), eq("127.0.0.1"));
    }

    @Test
    void uploadAvatarShouldDeletePreviousUploadedAvatarOnly() {
        when(attachmentService.saveUserAvatar(eq(3L), any())).thenReturn(78L);

        user.setAvatarUrl("/api/auth/avatars/77");
        service.uploadAvatar("alice", png("avatar.png"), "127.0.0.1");
        verify(attachmentService).deleteUserAvatar(77L);

        // 用户管理里手工填写的地址、不是附件 ID 的旧地址都不清理。
        user.setAvatarUrl("https://example.com/avatar.png");
        service.uploadAvatar("alice", png("avatar.png"), "127.0.0.1");
        user.setAvatarUrl("/api/auth/avatars/not-an-id");
        service.uploadAvatar("alice", png("avatar.png"), "127.0.0.1");
        verify(attachmentService, times(1)).deleteUserAvatar(any());
    }

    @Test
    void uploadAvatarShouldKeepValidationUnchanged() {
        assertThrows(IllegalArgumentException.class, () -> service.uploadAvatar("alice",
                new MockMultipartFile("file", "a.txt", "text/plain", PNG), "ip"));
        assertThrows(IllegalArgumentException.class, () -> service.uploadAvatar("alice",
                new MockMultipartFile("file", "a.txt", "image/png", PNG), "ip"));
        assertThrows(IllegalArgumentException.class, () -> service.uploadAvatar("alice",
                new MockMultipartFile("file", "a..png", "image/png", PNG), "ip"));
        assertThrows(IllegalArgumentException.class, () -> service.uploadAvatar("alice",
                new MockMultipartFile("file", new byte[2 * 1024 * 1024 + 1]), "ip"));
        assertThrows(IllegalArgumentException.class, () -> service.uploadAvatar("alice", null, "ip"));

        verify(attachmentService, never()).saveUserAvatar(any(), any());
        verify(userMapper, never()).updateAvatar(any(), any());
    }

    @Test
    void loadAvatarShouldReturnStoredContentAndContentType() throws Exception {
        AttachmentEntity entity = new AttachmentEntity();
        entity.setId(77L);
        entity.setBizType(AttachmentService.USER_AVATAR);
        entity.setContentType("image/gif");
        when(attachmentService.loadUserAvatar(77L))
                .thenReturn(new AttachmentService.AttachmentResource(entity, new ByteArrayResource(PNG)));

        AuthService.AvatarResource avatar = service.loadAvatar("77");

        assertEquals("image/gif", avatar.contentType());
        assertArrayEquals(PNG, avatar.resource().getContentAsByteArray());
    }

    @Test
    void loadAvatarShouldRejectNonNumericOrMissingId() {
        for (String key : new String[] {"abc", "1.png", "0", "", null}) {
            assertThrows(IllegalArgumentException.class, () -> service.loadAvatar(key), "key=" + key);
        }
        verify(attachmentService, never()).loadUserAvatar(any());

        when(attachmentService.loadUserAvatar(76L)).thenThrow(new IllegalArgumentException("头像文件不存在"));
        assertThrows(IllegalArgumentException.class, () -> service.loadAvatar("76"));
    }

    private MockMultipartFile png(String fileName) {
        return new MockMultipartFile("file", fileName, "image/png", PNG);
    }
}
