package com.photogai.modules.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.photogai.common.ErrorCode;
import com.photogai.config.WechatConfig;
import com.photogai.config.JwtUtil;
import com.photogai.exception.BizException;
import com.photogai.modules.auth.dto.AuthResponse;
import com.photogai.modules.auth.dto.RegisterRequest;
import com.photogai.modules.auth.dto.UserDTO;
import com.photogai.modules.auth.dto.WechatBindRequest;
import com.photogai.modules.auth.dto.WechatLoginRequest;
import com.photogai.modules.auth.dto.WechatLoginResponse;
import com.photogai.modules.auth.entity.User;
import com.photogai.modules.auth.entity.UserWechat;
import com.photogai.modules.studio.StudioRepository;
import com.photogai.modules.studio.dto.StudioDTO;
import com.photogai.modules.studio.entity.Studio;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;

/**
 * 微信登录服务单元测试（纯 Mockito，不连微信/PG）。
 *
 * <p>覆盖分支：loginByCode 的三条主路径（bindToken 绑定 / 已有 union 绑定复用 / 首登自动建号）、
 * auto-register 关闭、resolveWechatUser 的未配置 / openid 缺失 / errcode 非 0 / 空响应 / JSON 解析失败、
 * App 端 sns-oauth2 + userinfo 拉取、bind 的空参校验 / 微信头像回填 / openid 冲突、bindToken 内容不完整。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WechatServiceTest {

    @Mock
    private RestClient restClient;
    @Mock
    private WechatConfig wechatConfig;
    @Mock
    private UserWechatRepository userWechatRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private StudioRepository studioRepository;
    @Mock
    private AuthService authService;
    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RestClient.RequestHeadersUriSpec<?> uriSpec;
    @Mock
    private RestClient.RequestHeadersSpec<?> headersSpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;

    @InjectMocks
    private WechatService service;

    private User user(Long id, Long studioId) {
        User u = new User();
        u.setId(id);
        u.setStudioId(studioId);
        u.setUsername("u" + id);
        u.setRole("OWNER");
        return u;
    }

    private Studio studio(Long id) {
        Studio s = new Studio();
        s.setId(id);
        s.setName("studio" + id);
        return s;
    }

    /** 装配 RestClient 调用链：get().uri().retrieve().body(String)。 */
    private void stubWechatChain() {
        doReturn(uriSpec).when(restClient).get();
        doReturn(headersSpec).when(uriSpec).uri(anyString());
        doReturn(responseSpec).when(headersSpec).retrieve();
    }

    private void stubWechatJson(String... jsons) {
        stubWechatChain();
        if (jsons.length == 1) {
            doReturn(jsons[0]).when(responseSpec).body(String.class);
        } else {
            doReturn(jsons[0], java.util.Arrays.copyOfRange(jsons, 1, jsons.length))
                    .when(responseSpec).body(String.class);
        }
    }

    private void stubConfiguredApp() {
        when(wechatConfig.appOf(any())).thenReturn(new WechatConfig.WechatApp("appid", "secret"));
        when(wechatConfig.getCode2SessionUrl())
                .thenReturn("https://api.weixin.qq.com/sns/jscode2session");
        when(wechatConfig.getOauth2AccessTokenUrl())
                .thenReturn("https://api.weixin.qq.com/sns/oauth2/access_token");
        when(wechatConfig.getUserinfoUrl())
                .thenReturn("https://api.weixin.qq.com/sns/userinfo");
    }

    private void stubNoExistingBinding() {
        when(userWechatRepository.findFirstByUnionIdOrderByIdAsc(anyString())).thenReturn(Optional.empty());
        when(userWechatRepository.findByAppTypeAndOpenid(anyString(), anyString())).thenReturn(Optional.empty());
        when(userWechatRepository.findByUnionId(anyString())).thenReturn(List.of());
        when(userWechatRepository.save(any(UserWechat.class))).thenAnswer(i -> i.getArgument(0));
    }

    private AuthResponse registeredResponse(Long userId, Long studioId) {
        return AuthResponse.builder()
                .token("tok")
                .user(UserDTO.builder().id(userId).studioId(studioId).username("u" + userId).build())
                .studio(StudioDTO.builder().id(studioId).name("s").build())
                .build();
    }

    // ========================= 三端打通主路径 =========================

    @Test
    void loginByCodeWithBindTokenUpsertsAndReturnsResponse() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\",\"unionid\":\"u1\"}");
        WechatLoginRequest req = WechatLoginRequest.builder()
                .appType("MP").code("code").bindToken("tok").build();

        Claims claims = mock(Claims.class);
        when(claims.get("uid", Long.class)).thenReturn(5L);
        when(claims.get("sid", Long.class)).thenReturn(10L);
        when(jwtUtil.parse("tok")).thenReturn(claims);

        when(userRepository.findByStudioIdAndId(10L, 5L)).thenReturn(Optional.of(user(5L, 10L)));
        stubNoExistingBinding();
        when(studioRepository.findById(10L)).thenReturn(Optional.of(studio(10L)));

        WechatLoginResponse resp = service.loginByCode(req);
        assertNotNull(resp);
        assertEquals(5L, resp.getUser().getId());
        assertEquals(Boolean.FALSE, resp.getIsNewUser());
    }

    @Test
    void loginByCodeExistingUnionBindingReusesAccount() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\",\"unionid\":\"u1\"}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("MP").code("code").build();

        stubNoExistingBinding();
        UserWechat bound = new UserWechat();
        bound.setUserId(5L);
        bound.setStudioId(10L);
        bound.setAppType("MP");
        bound.setOpenid("o1");
        when(userWechatRepository.findFirstByUnionIdOrderByIdAsc("u1")).thenReturn(Optional.of(bound));
        when(userRepository.findByStudioIdAndId(10L, 5L)).thenReturn(Optional.of(user(5L, 10L)));
        when(studioRepository.findById(10L)).thenReturn(Optional.of(studio(10L)));

        WechatLoginResponse resp = service.loginByCode(req);
        assertNotNull(resp);
        assertEquals(5L, resp.getUser().getId());
    }

    @Test
    void loginByCodeAutoRegisterCreatesAccount() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\"}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("MP").code("code").build();
        when(wechatConfig.isAutoRegister()).thenReturn(true);
        stubNoExistingBinding();
        when(userService.findByUsername(anyString())).thenReturn(Optional.empty());
        when(authService.register(any(RegisterRequest.class))).thenReturn(registeredResponse(5L, 10L));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, 10L)));
        when(studioRepository.findById(10L)).thenReturn(Optional.of(studio(10L)));

        WechatLoginResponse resp = service.loginByCode(req);
        assertNotNull(resp);
        assertEquals(Boolean.TRUE, resp.getIsNewUser());
    }

    @Test
    void loginByCodeAutoRegisterDisabledThrowsUnauthorized() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\"}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("MP").code("code").build();
        when(wechatConfig.isAutoRegister()).thenReturn(false);
        stubNoExistingBinding();

        BizException ex = assertThrows(BizException.class, () -> service.loginByCode(req));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    // ========================= resolveWechatUser 分支 =========================

    @Test
    void loginByCodeUnconfiguredAppThrows() {
        when(wechatConfig.appOf(any())).thenReturn(new WechatConfig.WechatApp("", ""));
        WechatLoginRequest req = WechatLoginRequest.builder().appType("MP").code("code").build();

        BizException ex = assertThrows(BizException.class, () -> service.loginByCode(req));
        assertEquals(ErrorCode.WECHAT_CODE_INVALID.getCode(), ex.getCode());
    }

    @Test
    void loginByCodeOpenidMissingThrows() {
        stubConfiguredApp();
        stubWechatJson("{\"errcode\":0}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("MP").code("code").build();

        BizException ex = assertThrows(BizException.class, () -> service.loginByCode(req));
        assertEquals(ErrorCode.WECHAT_CODE_INVALID.getCode(), ex.getCode());
    }

    @Test
    void loginByCodeErrcodeNonZeroThrows() {
        stubConfiguredApp();
        stubWechatJson("{\"errcode\":40029,\"errmsg\":\"invalid code\"}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("MP").code("code").build();

        BizException ex = assertThrows(BizException.class, () -> service.loginByCode(req));
        assertEquals(ErrorCode.WECHAT_CODE_INVALID.getCode(), ex.getCode());
    }

    @Test
    void loginByCodeNullBodyThrows() {
        stubConfiguredApp();
        stubWechatChain();
        when(responseSpec.body(String.class)).thenReturn(null);
        WechatLoginRequest req = WechatLoginRequest.builder().appType("MP").code("code").build();

        BizException ex = assertThrows(BizException.class, () -> service.loginByCode(req));
        assertEquals(ErrorCode.WECHAT_CODE_INVALID.getCode(), ex.getCode());
    }

    @Test
    void loginByCodeInvalidJsonThrows() {
        stubConfiguredApp();
        stubWechatJson("not-a-json");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("MP").code("code").build();

        BizException ex = assertThrows(BizException.class, () -> service.loginByCode(req));
        assertEquals(ErrorCode.WECHAT_CODE_INVALID.getCode(), ex.getCode());
    }

    @Test
    void loginByCodeAppSnsOauth2SuccessWithUserinfo() {
        stubConfiguredApp();
        stubWechatJson(
                "{\"openid\":\"o1\",\"access_token\":\"at\",\"unionid\":\"u1\"}",
                "{\"nickname\":\"nick\",\"headimgurl\":\"http://a\",\"unionid\":\"u1\"}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("APP").code("code").build();
        when(wechatConfig.isAutoRegister()).thenReturn(true);
        stubNoExistingBinding();
        when(userService.findByUsername(anyString())).thenReturn(Optional.empty());
        when(authService.register(any(RegisterRequest.class))).thenReturn(registeredResponse(5L, 10L));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, 10L)));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(studioRepository.findById(10L)).thenReturn(Optional.of(studio(10L)));

        WechatLoginResponse resp = service.loginByCode(req);
        assertNotNull(resp);
    }

    @Test
    void loginByCodeAppMissingOpenidThrows() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"\",\"access_token\":\"at\"}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("APP").code("code").build();

        BizException ex = assertThrows(BizException.class, () -> service.loginByCode(req));
        assertEquals(ErrorCode.WECHAT_CODE_INVALID.getCode(), ex.getCode());
    }

    // ========================= bind 分支 =========================

    @Test
    void bindSuccessFallsBackAvatarFromWechat() {
        stubConfiguredApp();
        stubWechatJson(
                "{\"openid\":\"o1\",\"access_token\":\"at\",\"unionid\":\"u1\"}",
                "{\"nickname\":\"n\",\"headimgurl\":\"http://a\"}");
        WechatBindRequest req = WechatBindRequest.builder().appType("APP").code("code").build();
        User u = user(5L, 10L); // 头像默认空，触发微信头像回填分支
        when(userRepository.findByStudioIdAndId(10L, 5L)).thenReturn(Optional.of(u));
        stubNoExistingBinding();
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(studioRepository.findById(10L)).thenReturn(Optional.of(studio(10L)));

        AuthResponse resp = service.bind(10L, 5L, req);
        assertNotNull(resp);
        assertEquals("http://a", u.getAvatarUrl());
    }

    @Test
    void bindNullIdsThrowsUnauthorized() {
        WechatBindRequest req = WechatBindRequest.builder().appType("MP").code("code").build();

        BizException ex = assertThrows(BizException.class, () -> service.bind(null, null, req));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    @Test
    void bindConflictWhenOpenidBoundToOtherUser() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\"}");
        WechatBindRequest req = WechatBindRequest.builder().appType("MP").code("code").build();
        when(userRepository.findByStudioIdAndId(10L, 5L)).thenReturn(Optional.of(user(5L, 10L)));

        UserWechat existing = new UserWechat();
        existing.setUserId(99L);
        existing.setAppType("MP");
        existing.setOpenid("o1");
        when(userWechatRepository.findByAppTypeAndOpenid("MP", "o1")).thenReturn(Optional.of(existing));

        BizException ex = assertThrows(BizException.class, () -> service.bind(10L, 5L, req));
        assertEquals(ErrorCode.WECHAT_BIND_CONFLICT.getCode(), ex.getCode());
    }

    @Test
    void loginByCodeBindTokenIncompleteClaimsThrows() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\",\"unionid\":\"u1\"}");
        WechatLoginRequest req = WechatLoginRequest.builder()
                .appType("MP").code("code").bindToken("tok").build();
        Claims claims = mock(Claims.class);
        when(claims.get("uid", Long.class)).thenReturn(null);
        when(claims.get("sid", Long.class)).thenReturn(10L);
        when(jwtUtil.parse("tok")).thenReturn(claims);

        BizException ex = assertThrows(BizException.class, () -> service.loginByCode(req));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }
}
