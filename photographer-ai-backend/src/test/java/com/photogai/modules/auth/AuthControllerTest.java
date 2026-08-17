package com.photogai.modules.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photogai.common.JwtUser;
import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;
import com.photogai.exception.GlobalExceptionHandler;
import com.photogai.modules.auth.dto.AuthResponse;
import com.photogai.modules.auth.dto.LoginRequest;
import com.photogai.modules.auth.dto.RegisterRequest;
import com.photogai.modules.auth.dto.UserDTO;
import com.photogai.modules.auth.dto.WechatBindRequest;
import com.photogai.modules.auth.dto.WechatLoginRequest;
import com.photogai.modules.auth.dto.WechatLoginResponse;
import com.photogai.modules.studio.dto.StudioDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 认证控制器测试（standalone MockMvc，不加载 Spring 上下文）。
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private WechatService wechatService;

    @InjectMocks
    private AuthController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new JwtUser(1L, 1L, "tester", "STUDIO"), "", AuthorityUtils.NO_AUTHORITIES));
    }

    private AuthResponse sampleAuthResponse() {
        return AuthResponse.builder()
                .token("jwt-token")
                .user(UserDTO.builder().id(1L).username("tester").build())
                .studio(StudioDTO.builder().id(1L).name("我的工作室").build())
                .build();
    }

    @Test
    void registerReturnsAuthResponse() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .username("tester").password("secret123").studioName("我的工作室").build();
        when(authService.register(any(RegisterRequest.class))).thenReturn(sampleAuthResponse());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("jwt-token"));
    }

    @Test
    void registerPropagatesValidationError() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .username("tester").password("secret123").studioName("我的工作室").build();
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new BizException(ErrorCode.VALIDATION, "用户名已存在"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    void loginReturnsAuthResponse() throws Exception {
        LoginRequest req = LoginRequest.builder().username("tester").password("secret123").build();
        when(authService.login(any(LoginRequest.class))).thenReturn(sampleAuthResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void loginPropagatesUnauthorized() throws Exception {
        LoginRequest req = LoginRequest.builder().username("tester").password("wrong").build();
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void wechatLoginReturnsResponse() throws Exception {
        WechatLoginRequest req = WechatLoginRequest.builder().appType("MP").code("wxcode").build();
        WechatLoginResponse resp = WechatLoginResponse.builder()
                .token("jwt-token")
                .user(UserDTO.builder().id(1L).username("tester").build())
                .studio(StudioDTO.builder().id(1L).name("我的工作室").build())
                .isNewUser(false).needBind(false).build();
        when(wechatService.loginByCode(any(WechatLoginRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/auth/wechat/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("jwt-token"));
    }

    @Test
    void wechatLoginPropagatesInvalidCode() throws Exception {
        WechatLoginRequest req = WechatLoginRequest.builder().appType("MP").code("bad").build();
        when(wechatService.loginByCode(any(WechatLoginRequest.class)))
                .thenThrow(new BizException(ErrorCode.WECHAT_CODE_INVALID, "微信 code 无效或已失效，请重新授权"));

        mockMvc.perform(post("/api/auth/wechat/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("微信 code 无效或已失效，请重新授权"));
    }

    @Test
    void wechatBindReturnsAuthResponse() throws Exception {
        WechatBindRequest req = WechatBindRequest.builder().appType("MP").code("wxcode").build();
        when(wechatService.bind(anyLong(), anyLong(), any(WechatBindRequest.class)))
                .thenReturn(sampleAuthResponse());

        mockMvc.perform(post("/api/auth/wechat/bind")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("jwt-token"));
    }

    @Test
    void wechatBindPropagatesNotFound() throws Exception {
        WechatBindRequest req = WechatBindRequest.builder().appType("MP").code("wxcode").build();
        when(wechatService.bind(anyLong(), anyLong(), any(WechatBindRequest.class)))
                .thenThrow(new BizException(ErrorCode.NOT_FOUND, "用户不存在或不属于当前工作室"));

        mockMvc.perform(post("/api/auth/wechat/bind")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("用户不存在或不属于当前工作室"));
    }
}
