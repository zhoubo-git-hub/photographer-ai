package com.photogai.modules.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.modules.auth.dto.AuthResponse;
import com.photogai.modules.auth.dto.LoginRequest;
import com.photogai.modules.auth.dto.RegisterRequest;
import com.photogai.modules.auth.entity.User;
import com.photogai.modules.quota.QuotaService;
import com.photogai.modules.studio.StudioRepository;
import com.photogai.modules.studio.entity.Studio;
import com.photogai.config.JwtUtil;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 认证服务单元测试（Mockito，不连 PG）。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StudioRepository studioRepository;
    @Mock
    private QuotaService quotaService;
    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private User sampleUser() {
        User u = new User();
        u.setId(1L);
        u.setStudioId(1L);
        u.setUsername("tester");
        u.setRole("OWNER");
        u.setPasswordHash("hash");
        return u;
    }

    private Studio sampleStudio() {
        Studio s = new Studio();
        s.setId(1L);
        s.setName("我的工作室");
        return s;
    }

    @Test
    void loginReturnsAuthResponse() {
        when(userService.findByUsername("tester")).thenReturn(Optional.of(sampleUser()));
        when(userService.matches("secret123", "hash")).thenReturn(true);
        when(studioRepository.findById(1L)).thenReturn(Optional.of(sampleStudio()));
        when(jwtUtil.generateToken(anyLong(), anyLong(), anyString(), anyString())).thenReturn("token");

        AuthResponse resp = authService.login(
                LoginRequest.builder().username("tester").password("secret123").build());
        assertEquals("token", resp.getToken());
        assertEquals("tester", resp.getUser().getUsername());
    }

    @Test
    void loginThrowsUnauthorizedWhenUserNotFound() {
        when(userService.findByUsername("nope")).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> authService.login(
                LoginRequest.builder().username("nope").password("x").build()));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    void registerCreatesStudioAndOwner() {
        when(userService.findByUsername("tester")).thenReturn(Optional.empty());
        when(userService.encodePassword(anyString())).thenReturn("hash");
        when(studioRepository.save(any(Studio.class))).thenAnswer(i -> {
            Studio s = i.getArgument(0);
            s.setId(1L);
            return s;
        });
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtUtil.generateToken(anyLong(), anyLong(), anyString(), anyString())).thenReturn("token");

        AuthResponse resp = authService.register(RegisterRequest.builder()
                .username("tester").password("secret123").studioName("我的工作室").build());
        assertEquals("token", resp.getToken());
        assertEquals(1L, resp.getStudio().getId());
    }

    @Test
    void registerThrowsValidationWhenUsernameExists() {
        when(userService.findByUsername("tester")).thenReturn(Optional.of(sampleUser()));

        BizException ex = assertThrows(BizException.class, () -> authService.register(
                RegisterRequest.builder().username("tester").password("secret123")
                        .studioName("我的工作室").build()));
        assertEquals(ErrorCode.VALIDATION.getCode(), ex.getCode());
    }
}
