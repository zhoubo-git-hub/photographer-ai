package com.photogai.modules.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.mockito.ArgumentCaptor;
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

    // ===================================================================
    // 本轮新增：补齐 loginByCode / bind / upsertBinding / applyProfile 剩余分支
    // ===================================================================

    /** 复用：auto-register 链路的公共 stub（用户名不冲突 + register 成功 + 载入 user/studio）。 */
    private void stubAutoRegisterChain(Long userId, Long studioId) {
        when(wechatConfig.isAutoRegister()).thenReturn(true);
        when(userService.findByUsername(anyString())).thenReturn(Optional.empty());
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(registeredResponse(userId, studioId));
        when(studioRepository.findById(studioId)).thenReturn(Optional.of(studio(studioId)));
    }

    // ---------- bindToken 分支 ----------

    /** bindToken 只含 uid、缺 sid → 覆盖 {@code studioId == null} 一侧 → 401。 */
    @Test
    void loginByCodeBindTokenMissingStudioIdThrowsUnauthorized() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\",\"unionid\":\"u1\"}");
        WechatLoginRequest req = WechatLoginRequest.builder()
                .appType("MP").code("code").bindToken("tok").build();
        Claims claims = mock(Claims.class);
        when(claims.get("uid", Long.class)).thenReturn(5L);
        when(claims.get("sid", Long.class)).thenReturn(null);
        when(jwtUtil.parse("tok")).thenReturn(claims);

        BizException ex = assertThrows(BizException.class, () -> service.loginByCode(req));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        verify(userRepository, never()).findByStudioIdAndId(anyLong(), anyLong());
    }

    /** bindToken 解析抛异常 → parseBindToken 的 catch 分支 → 401。 */
    @Test
    void loginByCodeInvalidBindTokenThrowsUnauthorized() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\"}");
        WechatLoginRequest req = WechatLoginRequest.builder()
                .appType("MP").code("code").bindToken("bad").build();
        when(jwtUtil.parse("bad")).thenThrow(new IllegalArgumentException("malformed jwt"));

        BizException ex = assertThrows(BizException.class, () -> service.loginByCode(req));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
    }

    /** bindToken 为空白串 → 覆盖 {@code !isBlank(bindToken)} 为 false，落到"已有绑定"分支。 */
    @Test
    void loginByCodeBlankBindTokenFallsThroughToExistingBinding() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\"}");
        WechatLoginRequest req = WechatLoginRequest.builder()
                .appType("MP").code("code").bindToken("   ").build();

        UserWechat bound = new UserWechat();
        bound.setUserId(5L);
        bound.setStudioId(10L);
        bound.setAppType("MP");
        bound.setOpenid("o1");
        when(userWechatRepository.findByAppTypeAndOpenid("MP", "o1")).thenReturn(Optional.of(bound));
        when(userWechatRepository.save(any(UserWechat.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findByStudioIdAndId(10L, 5L)).thenReturn(Optional.of(user(5L, 10L)));
        when(studioRepository.findById(10L)).thenReturn(Optional.of(studio(10L)));

        WechatLoginResponse resp = service.loginByCode(req);
        assertEquals(5L, resp.getUser().getId());
        verify(jwtUtil, never()).parse(anyString());
    }

    // ---------- upsertBinding / applyProfile 分支 ----------

    /** 已有绑定命中同 appType + 同 user → upsertBinding 走"更新已有记录"分支（含 sessionKey 覆盖）。 */
    @Test
    void loginByCodeExistingSameUserBindingUpdatesRecord() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\",\"session_key\":\"sk-new\"}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("MP").code("code").build();

        UserWechat existing = new UserWechat();
        existing.setUserId(5L);
        existing.setStudioId(10L);
        existing.setAppType("MP");
        existing.setOpenid("o1");
        existing.setSessionKey("sk-old");
        when(userWechatRepository.findByAppTypeAndOpenid("MP", "o1")).thenReturn(Optional.of(existing));
        when(userWechatRepository.save(any(UserWechat.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findByStudioIdAndId(10L, 5L)).thenReturn(Optional.of(user(5L, 10L)));
        when(studioRepository.findById(10L)).thenReturn(Optional.of(studio(10L)));

        WechatLoginResponse resp = service.loginByCode(req);
        assertEquals(5L, resp.getUser().getId());
        assertEquals(Boolean.FALSE, resp.getIsNewUser());
        // 更新分支：session_key 被新值覆盖，studioId 回写
        assertEquals("sk-new", existing.getSessionKey());
        assertEquals(10L, existing.getStudioId());
        verify(userWechatRepository).save(existing);
    }

    /** MP 首绑：unionId + sessionKey 均有值 → applyProfile 两个 if 为 true；昵称/头像 MP 侧恒为 null。 */
    @Test
    void loginByCodeMpNewBindingPersistsUnionIdAndSessionKey() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\",\"unionid\":\"u1\",\"session_key\":\"sk\"}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("MP").code("code").build();
        stubNoExistingBinding();
        stubAutoRegisterChain(5L, 10L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, 10L)));

        WechatLoginResponse resp = service.loginByCode(req);
        assertNotNull(resp);

        ArgumentCaptor<UserWechat> captor = ArgumentCaptor.forClass(UserWechat.class);
        verify(userWechatRepository).save(captor.capture());
        UserWechat saved = captor.getValue();
        assertEquals("u1", saved.getUnionId());
        assertEquals("sk", saved.getSessionKey());
        assertEquals("MP", saved.getAppType());
        assertEquals("o1", saved.getOpenid());
        assertEquals(5L, saved.getUserId());
        assertEquals(10L, saved.getStudioId());
        assertNull(saved.getNickname());
        assertNull(saved.getAvatarUrl());
    }

    /** code2Session 无 unionid → findBinding 跳过 union 查询，直接走 (appType, openid)。 */
    @Test
    void loginByCodeWithoutUnionIdSkipsUnionLookup() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\"}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("MP").code("code").build();
        when(userWechatRepository.findByAppTypeAndOpenid("MP", "o1")).thenReturn(Optional.empty());
        when(userWechatRepository.save(any(UserWechat.class))).thenAnswer(i -> i.getArgument(0));
        stubAutoRegisterChain(5L, 10L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, 10L)));

        WechatLoginResponse resp = service.loginByCode(req);
        assertEquals(Boolean.TRUE, resp.getIsNewUser());
        verify(userWechatRepository, never()).findFirstByUnionIdOrderByIdAsc(anyString());
        verify(userWechatRepository, never()).findByUnionId(anyString());
        verify(userWechatRepository).save(any(UserWechat.class));
    }

    /** auto-register 时该 unionId 已被别的 user 占用 → upsertBinding 的 union 冲突循环 → 409。 */
    @Test
    void loginByCodeAutoRegisterUnionOccupiedThrowsConflict() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\",\"unionid\":\"u1\"}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("MP").code("code").build();
        when(userWechatRepository.findFirstByUnionIdOrderByIdAsc("u1")).thenReturn(Optional.empty());
        when(userWechatRepository.findByAppTypeAndOpenid("MP", "o1")).thenReturn(Optional.empty());
        UserWechat other = new UserWechat();
        other.setUserId(99L);
        other.setStudioId(77L);
        other.setAppType("APP");
        other.setOpenid("o-other");
        other.setUnionId("u1");
        when(userWechatRepository.findByUnionId("u1")).thenReturn(List.of(other));
        stubAutoRegisterChain(5L, 10L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, 10L)));

        BizException ex = assertThrows(BizException.class, () -> service.loginByCode(req));
        assertEquals(ErrorCode.WECHAT_BIND_CONFLICT.getCode(), ex.getCode());
        verify(userWechatRepository, never()).save(any(UserWechat.class));
    }

    // ---------- auto-register 分支 ----------

    /** auto-register 后微信返回头像 → 回填 users.avatar_url 并 save。 */
    @Test
    void loginByCodeAutoRegisterAppliesWechatAvatar() {
        stubConfiguredApp();
        stubWechatJson(
                "{\"openid\":\"o1\",\"access_token\":\"at\",\"unionid\":\"u1\"}",
                "{\"nickname\":\"小明\",\"headimgurl\":\"http://avatar/1.png\"}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("APP").code("code").build();
        stubNoExistingBinding();
        stubAutoRegisterChain(5L, 10L);
        User loaded = user(5L, 10L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(loaded));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        WechatLoginResponse resp = service.loginByCode(req);
        assertEquals(Boolean.TRUE, resp.getIsNewUser());
        assertEquals(Boolean.TRUE, resp.getNeedBind());
        assertEquals("http://avatar/1.png", loaded.getAvatarUrl());
        verify(userRepository).save(loaded);
    }

    /** auto-register 后 findById 为空 → 抛 SYSTEM("自动建号失败")。 */
    @Test
    void loginByCodeAutoRegisterUserMissingThrowsSystem() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\"}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("MP").code("code").build();
        stubNoExistingBinding();
        stubAutoRegisterChain(5L, 10L);
        when(userRepository.findById(5L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> service.loginByCode(req));
        assertEquals(ErrorCode.SYSTEM.getCode(), ex.getCode());
        assertNotNull(ex.getMessage());
    }

    /** 用户名连续冲突耗尽重试 → generateUsername 抛 SYSTEM。 */
    @Test
    void loginByCodeAutoRegisterUsernameAlwaysTakenThrowsSystem() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\"}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("MP").code("code").build();
        stubNoExistingBinding();
        when(wechatConfig.isAutoRegister()).thenReturn(true);
        when(userService.findByUsername(anyString())).thenReturn(Optional.of(user(99L, 77L)));

        BizException ex = assertThrows(BizException.class, () -> service.loginByCode(req));
        assertEquals(ErrorCode.SYSTEM.getCode(), ex.getCode());
        verify(authService, never()).register(any(RegisterRequest.class));
    }

    // ---------- APP / WEB sns-oauth2 分支 ----------

    /** access_token 为空串 → 覆盖 {@code isBlank(accessToken)} 一侧 → WECHAT_CODE_INVALID。 */
    @Test
    void loginByCodeAppBlankAccessTokenThrows() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\",\"access_token\":\"\"}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("APP").code("code").build();

        BizException ex = assertThrows(BizException.class, () -> service.loginByCode(req));
        assertEquals(ErrorCode.WECHAT_CODE_INVALID.getCode(), ex.getCode());
    }

    /** userinfo 调用失败 → 走 catch(BizException) 不阻断登录，昵称/头像为 null。 */
    @Test
    void loginByCodeAppUserinfoFailureStillLogsIn() {
        stubConfiguredApp();
        stubWechatChain();
        when(responseSpec.body(String.class))
                .thenReturn("{\"openid\":\"o1\",\"access_token\":\"at\",\"unionid\":\"u1\"}")
                .thenThrow(new IllegalStateException("userinfo 网络异常"));
        WechatLoginRequest req = WechatLoginRequest.builder().appType("APP").code("code").build();
        stubNoExistingBinding();
        stubAutoRegisterChain(5L, 10L);
        User loaded = user(5L, 10L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(loaded));

        WechatLoginResponse resp = service.loginByCode(req);
        assertEquals(Boolean.TRUE, resp.getIsNewUser());
        assertNull(loaded.getAvatarUrl());
        verify(userRepository, never()).save(any(User.class));

        ArgumentCaptor<UserWechat> captor = ArgumentCaptor.forClass(UserWechat.class);
        verify(userWechatRepository).save(captor.capture());
        assertNull(captor.getValue().getNickname());
        assertEquals("u1", captor.getValue().getUnionId());
    }

    /** token 响应无 unionid、userinfo 有 → 覆盖 {@code isBlank(unionId)} 为 true 的补取分支。 */
    @Test
    void loginByCodeAppTakesUnionIdFromUserinfo() {
        stubConfiguredApp();
        stubWechatJson(
                "{\"openid\":\"o1\",\"access_token\":\"at\"}",
                "{\"nickname\":\"小红\",\"headimgurl\":\"http://a\",\"unionid\":\"u9\"}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("APP").code("code").build();
        stubNoExistingBinding();
        stubAutoRegisterChain(5L, 10L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, 10L)));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        WechatLoginResponse resp = service.loginByCode(req);
        assertNotNull(resp);

        ArgumentCaptor<UserWechat> captor = ArgumentCaptor.forClass(UserWechat.class);
        verify(userWechatRepository).save(captor.capture());
        UserWechat saved = captor.getValue();
        assertEquals("u9", saved.getUnionId());
        assertEquals("小红", saved.getNickname());
        assertEquals("http://a", saved.getAvatarUrl());
        assertNull(saved.getSessionKey());
    }

    /** WEB 端同样走 sns-oauth2（isMiniProgram 为 false 分支的另一入口）。 */
    @Test
    void loginByCodeWebGoesThroughSnsOauth2() {
        stubConfiguredApp();
        stubWechatJson(
                "{\"openid\":\"ow\",\"access_token\":\"at\",\"unionid\":\"uw\"}",
                "{\"nickname\":\"web用户\",\"headimgurl\":\"http://w\"}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("WEB").code("code").build();

        UserWechat bound = new UserWechat();
        bound.setUserId(5L);
        bound.setStudioId(10L);
        bound.setAppType("MP");
        bound.setOpenid("omp");
        bound.setUnionId("uw");
        when(userWechatRepository.findFirstByUnionIdOrderByIdAsc("uw")).thenReturn(Optional.of(bound));
        when(userWechatRepository.findByAppTypeAndOpenid("WEB", "ow")).thenReturn(Optional.empty());
        when(userWechatRepository.findByUnionId("uw")).thenReturn(List.of(bound));
        when(userWechatRepository.save(any(UserWechat.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findByStudioIdAndId(10L, 5L)).thenReturn(Optional.of(user(5L, 10L)));
        when(studioRepository.findById(10L)).thenReturn(Optional.of(studio(10L)));

        WechatLoginResponse resp = service.loginByCode(req);
        assertEquals(5L, resp.getUser().getId());
        assertEquals(Boolean.FALSE, resp.getIsNewUser());

        // union 命中同一自然人：为 WEB 端补一条新绑定
        ArgumentCaptor<UserWechat> captor = ArgumentCaptor.forClass(UserWechat.class);
        verify(userWechatRepository).save(captor.capture());
        assertEquals("WEB", captor.getValue().getAppType());
        assertEquals("ow", captor.getValue().getOpenid());
    }

    // ---------- bind 分支 ----------

    /** 用户已设头像 → 覆盖 {@code isBlank(user.getAvatarUrl())} 为 false，不覆盖已有头像。 */
    @Test
    void bindKeepsExistingAvatar() {
        stubConfiguredApp();
        stubWechatJson(
                "{\"openid\":\"o1\",\"access_token\":\"at\",\"unionid\":\"u1\"}",
                "{\"nickname\":\"n\",\"headimgurl\":\"http://new\"}");
        WechatBindRequest req = WechatBindRequest.builder().appType("APP").code("code").build();
        User u = user(5L, 10L);
        u.setAvatarUrl("http://existing");
        when(userRepository.findByStudioIdAndId(10L, 5L)).thenReturn(Optional.of(u));
        stubNoExistingBinding();
        when(studioRepository.findById(10L)).thenReturn(Optional.of(studio(10L)));

        AuthResponse resp = service.bind(10L, 5L, req);
        assertNotNull(resp);
        assertEquals("http://existing", u.getAvatarUrl());
        verify(userRepository, never()).save(any(User.class));
    }

    /** bind 时 user 不属于当前 studio → loadUser 抛 NOT_FOUND。 */
    @Test
    void bindThrowsNotFoundWhenUserNotInStudio() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\"}");
        WechatBindRequest req = WechatBindRequest.builder().appType("MP").code("code").build();
        when(userRepository.findByStudioIdAndId(10L, 5L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> service.bind(10L, 5L, req));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    /** bind 时 studio 缺失 → loadStudio 抛 SYSTEM。 */
    @Test
    void bindThrowsSystemWhenStudioMissing() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\"}");
        WechatBindRequest req = WechatBindRequest.builder().appType("MP").code("code").build();
        when(userRepository.findByStudioIdAndId(10L, 5L)).thenReturn(Optional.of(user(5L, 10L)));
        stubNoExistingBinding();
        when(studioRepository.findById(10L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> service.bind(10L, 5L, req));
        assertEquals(ErrorCode.SYSTEM.getCode(), ex.getCode());
    }

    /** bind 仅 userId 为空 → 覆盖 {@code userId == null} 一侧 → 401。 */
    @Test
    void bindNullUserIdThrowsUnauthorized() {
        WechatBindRequest req = WechatBindRequest.builder().appType("MP").code("code").build();

        BizException ex = assertThrows(BizException.class, () -> service.bind(10L, null, req));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        verify(wechatConfig, never()).appOf(any());
    }

    /** bind 成功且微信侧无昵称/头像 → applyProfile 昵称、头像两个 if 均为 false。 */
    @Test
    void bindWithoutWechatProfileLeavesAvatarNull() {
        stubConfiguredApp();
        stubWechatJson("{\"openid\":\"o1\",\"unionid\":\"u1\",\"session_key\":\"sk\"}");
        WechatBindRequest req = WechatBindRequest.builder().appType("MP").code("code").build();
        User u = user(5L, 10L);
        when(userRepository.findByStudioIdAndId(10L, 5L)).thenReturn(Optional.of(u));
        stubNoExistingBinding();
        when(studioRepository.findById(10L)).thenReturn(Optional.of(studio(10L)));
        when(jwtUtil.generateToken(5L, 10L, "u5", "OWNER")).thenReturn("jwt-token");

        AuthResponse resp = service.bind(10L, 5L, req);
        assertEquals("jwt-token", resp.getToken());
        assertEquals(5L, resp.getUser().getId());
        assertEquals(10L, resp.getStudio().getId());
        assertNull(u.getAvatarUrl());
        verify(userRepository, never()).save(any(User.class));
    }

    // ---------- getJson / isBlank / truncate 剩余分支 ----------

    /** 微信返回全空白响应体 → 覆盖 {@code raw.isBlank()}（非 null 但空白）一侧。 */
    @Test
    void loginByCodeBlankBodyThrows() {
        stubConfiguredApp();
        stubWechatJson("   ");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("MP").code("code").build();

        BizException ex = assertThrows(BizException.class, () -> service.loginByCode(req));
        assertEquals(ErrorCode.WECHAT_CODE_INVALID.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("返回为空"));
    }

    /** errcode 非 0 但没有 errmsg → 覆盖 {@code errmsg.isBlank()} 为 true 的拼接分支。 */
    @Test
    void loginByCodeErrcodeWithoutErrmsgThrows() {
        stubConfiguredApp();
        stubWechatJson("{\"errcode\":40029}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("MP").code("code").build();

        BizException ex = assertThrows(BizException.class, () -> service.loginByCode(req));
        assertEquals(ErrorCode.WECHAT_CODE_INVALID.getCode(), ex.getCode());
        // errmsg 为空时不追加"，xxx"后缀
        assertEquals("微信 code 换票失败：errcode=40029", ex.getMessage());
    }

    /** 用户头像为空串（非 null）→ 覆盖 isBlank 的 {@code value.isBlank()} 一侧 → 仍回填微信头像。 */
    @Test
    void bindFillsAvatarWhenExistingAvatarIsEmptyString() {
        stubConfiguredApp();
        stubWechatJson(
                "{\"openid\":\"o1\",\"access_token\":\"at\",\"unionid\":\"u1\"}",
                "{\"nickname\":\"n\",\"headimgurl\":\"http://a\"}");
        WechatBindRequest req = WechatBindRequest.builder().appType("APP").code("code").build();
        User u = user(5L, 10L);
        u.setAvatarUrl("");
        when(userRepository.findByStudioIdAndId(10L, 5L)).thenReturn(Optional.of(u));
        stubNoExistingBinding();
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(studioRepository.findById(10L)).thenReturn(Optional.of(studio(10L)));

        AuthResponse resp = service.bind(10L, 5L, req);
        assertNotNull(resp);
        assertEquals("http://a", u.getAvatarUrl());
        verify(userRepository).save(u);
    }

    /** 超长昵称 → 覆盖 truncate 的 substring 分支（绑定昵称截 100、工作室名截 60）。 */
    @Test
    void loginByCodeAutoRegisterTruncatesOverlongNickname() {
        String longNick = "昵".repeat(150);
        stubConfiguredApp();
        stubWechatJson(
                "{\"openid\":\"o1\",\"access_token\":\"at\",\"unionid\":\"u1\"}",
                "{\"nickname\":\"" + longNick + "\",\"headimgurl\":\"http://a\"}");
        WechatLoginRequest req = WechatLoginRequest.builder().appType("APP").code("code").build();
        stubNoExistingBinding();
        stubAutoRegisterChain(5L, 10L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user(5L, 10L)));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        WechatLoginResponse resp = service.loginByCode(req);
        assertNotNull(resp);

        // 绑定昵称截断到列宽 100
        ArgumentCaptor<UserWechat> bindingCaptor = ArgumentCaptor.forClass(UserWechat.class);
        verify(userWechatRepository).save(bindingCaptor.capture());
        assertEquals(100, bindingCaptor.getValue().getNickname().length());

        // 工作室名 = 昵称截断到 60 + "的工作室"
        ArgumentCaptor<RegisterRequest> registerCaptor = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(authService).register(registerCaptor.capture());
        String studioName = registerCaptor.getValue().getStudioName();
        assertEquals("昵".repeat(60) + "的工作室", studioName);
    }
}
