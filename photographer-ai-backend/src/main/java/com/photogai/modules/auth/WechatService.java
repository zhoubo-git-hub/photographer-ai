package com.photogai.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photogai.common.ErrorCode;
import com.photogai.config.JwtUtil;
import com.photogai.config.WechatConfig;
import com.photogai.exception.BizException;
import com.photogai.modules.auth.dto.AuthResponse;
import com.photogai.modules.auth.dto.RegisterRequest;
import com.photogai.modules.auth.dto.UserDTO;
import com.photogai.modules.auth.dto.WechatBindRequest;
import com.photogai.modules.auth.dto.WechatLoginRequest;
import com.photogai.modules.auth.dto.WechatLoginResponse;
import com.photogai.modules.auth.entity.User;
import com.photogai.modules.auth.entity.UserWechat;
import com.photogai.modules.auth.enums.WechatAppType;
import com.photogai.modules.studio.StudioRepository;
import com.photogai.modules.studio.dto.StudioDTO;
import com.photogai.modules.studio.entity.Studio;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 微信登录服务：三端（小程序 / App / Web）统一账号。
 *
 * <p>核心链路：
 * <ol>
 *   <li>换票：MP 走 {@code auth.code2Session}；APP / WEB 走开放平台
 *       {@code sns/oauth2/access_token} + {@code sns/userinfo}。</li>
 *   <li>定位账号：<b>先 union_id（优先）</b>，再 {@code (app_type, openid)}；
 *       命中即复用同一 {@code user + studio} —— 这就是三端打通的实现点。</li>
 *   <li>首登自动建号：<b>复用 {@link AuthService#register}</b>（建 Studio + OWNER 用户 + 初始额度），
 *       不重写建租户逻辑。</li>
 *   <li>{@code bindToken} 非空：不建号，只把该微信绑到 token 对应的已登录账号。</li>
 * </ol>
 *
 * <p>零新增 Maven 依赖：HTTP 调用统一用 Spring 内置 {@link RestClient}（同 {@code LlmClient}）。
 */
@Slf4j
@Service
public class WechatService {

    /** 自动建号时的用户名前缀。 */
    private static final String AUTO_USERNAME_PREFIX = "wx_";

    /** 自动建号时生成唯一用户名的最大重试次数。 */
    private static final int USERNAME_RETRY = 8;

    /** 工作室名兜底。 */
    private static final String DEFAULT_STUDIO_NAME = "我的工作室";

    /** studio.name 列长度 100，昵称拼接时留足余量。 */
    private static final int MAX_STUDIO_NAME_LEN = 60;

    private final RestClient restClient;
    private final WechatConfig wechatConfig;
    private final UserWechatRepository userWechatRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final StudioRepository studioRepository;
    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WechatService(
            RestClient wechatRestClient,
            WechatConfig wechatConfig,
            UserWechatRepository userWechatRepository,
            UserRepository userRepository,
            UserService userService,
            StudioRepository studioRepository,
            AuthService authService,
            JwtUtil jwtUtil) {
        this.restClient = wechatRestClient;
        this.wechatConfig = wechatConfig;
        this.userWechatRepository = userWechatRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.studioRepository = studioRepository;
        this.authService = authService;
        this.jwtUtil = jwtUtil;
    }

    // ==================================================================
    // 对外能力
    // ==================================================================

    /**
     * 微信登录（三端统一入口）。
     *
     * @param req 终端类型 + code（+ 可选 bindToken）
     * @return token + 用户 + 工作室（结构与账号密码登录一致）
     */
    @Transactional
    public WechatLoginResponse loginByCode(WechatLoginRequest req) {
        WechatAppType appType = WechatAppType.from(req.getAppType());
        WechatUserInfo wx = resolveWechatUser(appType, req.getCode());

        // 分支一：带 bindToken → 绑到已登录账号，绝不建号
        if (req.getBindToken() != null && !req.getBindToken().isBlank()) {
            Claims claims = parseBindToken(req.getBindToken());
            Long userId = claims.get("uid", Long.class);
            Long studioId = claims.get("sid", Long.class);
            if (userId == null || studioId == null) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "bindToken 内容不完整，请重新登录后再绑定");
            }
            User user = loadUser(studioId, userId);
            upsertBinding(user, studioId, wx);
            Studio studio = loadStudio(studioId);
            log.info("[Wechat] bindToken 绑定成功：appType={}, userId={}, studioId={}", appType, userId, studioId);
            return buildResponse(user, studio, false, false);
        }

        // 分支二：已存在绑定 → 直接复用同一 user + studio（三端打通）
        Optional<UserWechat> bound = findBinding(appType, wx);
        if (bound.isPresent()) {
            UserWechat record = bound.get();
            User user = loadUser(record.getStudioId(), record.getUserId());
            // 若命中的是别的终端（靠 union_id 打通），为当前终端补一条绑定
            upsertBinding(user, record.getStudioId(), wx);
            Studio studio = loadStudio(record.getStudioId());
            log.info("[Wechat] 登录命中已有绑定：appType={}, userId={}, studioId={}, byUnionId={}",
                    appType, user.getId(), studio.getId(), !isBlank(wx.unionId()));
            return buildResponse(user, studio, false, false);
        }

        // 分支三：首登自动建号（复用 AuthService.register 的建 studio 逻辑）
        if (!wechatConfig.isAutoRegister()) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "该微信尚未绑定账号，请先注册后再绑定微信");
        }
        AuthResponse registered = authService.register(buildRegisterRequest(wx));
        User user = userRepository.findById(registered.getUser().getId())
                .orElseThrow(() -> new BizException(ErrorCode.SYSTEM, "自动建号失败：用户数据异常"));
        Studio studio = loadStudio(registered.getStudio().getId());

        // 微信头像落到 users.avatar_url，供 App / 小程序直接展示
        if (!isBlank(wx.avatarUrl())) {
            user.setAvatarUrl(wx.avatarUrl());
            user = userRepository.save(user);
        }
        upsertBinding(user, studio.getId(), wx);
        log.info("[Wechat] 首登自动建号：appType={}, userId={}, studioId={}", appType, user.getId(), studio.getId());
        return buildResponse(user, studio, true, true);
    }

    /**
     * 已登录用户绑定微信（需 JWT）。
     *
     * @param studioId 当前租户（来自 {@code CurrentUser.getStudioId()}）
     * @param userId   当前用户
     * @param req      终端类型 + code
     * @return 刷新后的登录态（token + 用户 + 工作室）
     */
    @Transactional
    public AuthResponse bind(Long studioId, Long userId, WechatBindRequest req) {
        if (studioId == null || userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录后再绑定微信");
        }
        WechatAppType appType = WechatAppType.from(req.getAppType());
        WechatUserInfo wx = resolveWechatUser(appType, req.getCode());

        User user = loadUser(studioId, userId);
        upsertBinding(user, studioId, wx);

        // 未设置过头像时，用微信头像补齐
        if (isBlank(user.getAvatarUrl()) && !isBlank(wx.avatarUrl())) {
            user.setAvatarUrl(wx.avatarUrl());
            user = userRepository.save(user);
        }
        Studio studio = loadStudio(studioId);
        log.info("[Wechat] 绑定成功：appType={}, userId={}, studioId={}", appType, userId, studioId);

        return AuthResponse.builder()
                .token(issueToken(user, studio))
                .user(UserDTO.from(user))
                .studio(StudioDTO.from(studio))
                .build();
    }

    // ==================================================================
    // 微信换票（RestClient，零新增依赖）
    // ==================================================================

    /** 按终端选择换票方式，返回统一的微信用户信息。 */
    private WechatUserInfo resolveWechatUser(WechatAppType appType, String code) {
        WechatConfig.WechatApp app = wechatConfig.appOf(appType);
        if (!app.configured()) {
            throw new BizException(ErrorCode.WECHAT_CODE_INVALID,
                    "微信 " + appType.name() + " 端未配置 appid/secret，无法完成登录");
        }
        return appType.isMiniProgram() ? byCode2Session(app, code) : bySnsOauth2(appType, app, code);
    }

    /** 小程序：{@code code2Session} → openid / session_key / unionid。 */
    private WechatUserInfo byCode2Session(WechatConfig.WechatApp app, String code) {
        String url = UriComponentsBuilder.fromHttpUrl(wechatConfig.getCode2SessionUrl())
                .queryParam("appid", app.appid())
                .queryParam("secret", app.secret())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .toUriString();

        JsonNode node = getJson(url, "code2Session");
        String openid = text(node, "openid");
        if (isBlank(openid)) {
            throw new BizException(ErrorCode.WECHAT_CODE_INVALID, "微信 code 无效：未返回 openid");
        }
        return new WechatUserInfo(
                WechatAppType.MP,
                openid,
                text(node, "unionid"),
                text(node, "session_key"),
                null,
                null);
    }

    /** App / Web：开放平台 {@code sns.oauth2.access_token} + {@code sns.userinfo}。 */
    private WechatUserInfo bySnsOauth2(WechatAppType appType, WechatConfig.WechatApp app, String code) {
        String tokenUrl = UriComponentsBuilder.fromHttpUrl(wechatConfig.getOauth2AccessTokenUrl())
                .queryParam("appid", app.appid())
                .queryParam("secret", app.secret())
                .queryParam("code", code)
                .queryParam("grant_type", "authorization_code")
                .toUriString();

        JsonNode tokenNode = getJson(tokenUrl, "sns.oauth2.access_token");
        String openid = text(tokenNode, "openid");
        String accessToken = text(tokenNode, "access_token");
        String unionId = text(tokenNode, "unionid");
        if (isBlank(openid) || isBlank(accessToken)) {
            throw new BizException(ErrorCode.WECHAT_CODE_INVALID, "微信 code 无效：未返回 openid/access_token");
        }

        // 拉昵称头像；失败不阻断登录（昵称头像非必需）
        String nickname = null;
        String avatarUrl = null;
        try {
            String infoUrl = UriComponentsBuilder.fromHttpUrl(wechatConfig.getUserinfoUrl())
                    .queryParam("access_token", accessToken)
                    .queryParam("openid", openid)
                    .queryParam("lang", "zh_CN")
                    .toUriString();
            JsonNode infoNode = getJson(infoUrl, "sns.userinfo");
            nickname = text(infoNode, "nickname");
            avatarUrl = text(infoNode, "headimgurl");
            if (isBlank(unionId)) {
                unionId = text(infoNode, "unionid");
            }
        } catch (BizException e) {
            log.warn("[Wechat] 拉取用户资料失败（不阻断登录）：appType={}, msg={}", appType, e.getMessage());
        }

        return new WechatUserInfo(appType, openid, unionId, null, nickname, avatarUrl);
    }

    /** 统一 GET + errcode 校验；任何网络/解析/业务错误都归一到 {@code WECHAT_CODE_INVALID}。 */
    private JsonNode getJson(String url, String api) {
        String raw;
        try {
            raw = restClient.get().uri(url).retrieve().body(String.class);
        } catch (Exception e) {
            log.warn("[Wechat] 调用 {} 失败：{}", api, e.getMessage());
            throw new BizException(ErrorCode.WECHAT_CODE_INVALID, "微信接口调用失败（" + api + "）");
        }
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.WECHAT_CODE_INVALID, "微信接口返回为空（" + api + "）");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new BizException(ErrorCode.WECHAT_CODE_INVALID, "微信接口响应解析失败（" + api + "）");
        }
        int errcode = node.path("errcode").asInt(0);
        if (errcode != 0) {
            String errmsg = node.path("errmsg").asText("");
            log.warn("[Wechat] {} 返回错误：errcode={}, errmsg={}", api, errcode, errmsg);
            throw new BizException(ErrorCode.WECHAT_CODE_INVALID,
                    "微信 code 换票失败：errcode=" + errcode + (errmsg.isBlank() ? "" : "，" + errmsg));
        }
        return node;
    }

    // ==================================================================
    // 绑定关系维护
    // ==================================================================

    /** 查已有绑定：union_id 优先（跨端打通），再退回 {@code (app_type, openid)}。 */
    private Optional<UserWechat> findBinding(WechatAppType appType, WechatUserInfo wx) {
        if (!isBlank(wx.unionId())) {
            Optional<UserWechat> byUnion = userWechatRepository.findFirstByUnionIdOrderByIdAsc(wx.unionId());
            if (byUnion.isPresent()) {
                return byUnion;
            }
        }
        return userWechatRepository.findByAppTypeAndOpenid(appType.name(), wx.openid());
    }

    /**
     * 新增或更新一条绑定（同一微信只能属于一个账号）。
     *
     * @throws BizException {@code WECHAT_BIND_CONFLICT} 当该 openid/unionid 已绑到别的用户
     */
    private UserWechat upsertBinding(User user, Long studioId, WechatUserInfo wx) {
        String appType = wx.appType().name();

        Optional<UserWechat> existing = userWechatRepository.findByAppTypeAndOpenid(appType, wx.openid());
        if (existing.isPresent()) {
            UserWechat record = existing.get();
            if (!Objects.equals(record.getUserId(), user.getId())) {
                throw new BizException(ErrorCode.WECHAT_BIND_CONFLICT, "该微信已绑定其他账号，请先在原账号解绑");
            }
            applyProfile(record, wx);
            record.setStudioId(studioId);
            return userWechatRepository.save(record);
        }

        // UnionID 已被别的账号占用 → 冲突（避免同一自然人被拆成两个 studio）
        if (!isBlank(wx.unionId())) {
            List<UserWechat> sameUnion = userWechatRepository.findByUnionId(wx.unionId());
            for (UserWechat other : sameUnion) {
                if (!Objects.equals(other.getUserId(), user.getId())) {
                    throw new BizException(ErrorCode.WECHAT_BIND_CONFLICT, "该微信已绑定其他账号，请先在原账号解绑");
                }
            }
        }

        UserWechat record = new UserWechat();
        record.setUserId(user.getId());
        record.setStudioId(studioId);
        record.setAppType(appType);
        record.setOpenid(wx.openid());
        applyProfile(record, wx);
        return userWechatRepository.save(record);
    }

    /** 仅在微信侧返回了新值时覆盖，避免把已有 unionid / 昵称冲成空。 */
    private void applyProfile(UserWechat record, WechatUserInfo wx) {
        if (!isBlank(wx.unionId())) {
            record.setUnionId(wx.unionId());
        }
        if (!isBlank(wx.sessionKey())) {
            record.setSessionKey(wx.sessionKey());
        }
        if (!isBlank(wx.nickname())) {
            record.setNickname(truncate(wx.nickname(), 100));
        }
        if (!isBlank(wx.avatarUrl())) {
            record.setAvatarUrl(truncate(wx.avatarUrl(), 512));
        }
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /** 自动建号入参：用户名去重、随机密码、昵称派生工作室名。 */
    private RegisterRequest buildRegisterRequest(WechatUserInfo wx) {
        return RegisterRequest.builder()
                .username(generateUsername(wx.openid()))
                .password(UUID.randomUUID().toString().replace("-", ""))
                .email(null)
                .studioName(deriveStudioName(wx.nickname()))
                .build();
    }

    /** 生成不冲突的用户名：{@code wx_} + openid 摘要片段，冲突则追加随机后缀。 */
    private String generateUsername(String openid) {
        String seed = Integer.toHexString(Math.abs(Objects.hashCode(openid)));
        String candidate = AUTO_USERNAME_PREFIX + truncate(seed, 12);
        for (int i = 0; i < USERNAME_RETRY; i++) {
            if (userService.findByUsername(candidate).isEmpty()) {
                return candidate;
            }
            candidate = AUTO_USERNAME_PREFIX + truncate(seed, 8) + "_"
                    + UUID.randomUUID().toString().substring(0, 6);
        }
        throw new BizException(ErrorCode.SYSTEM, "自动建号失败：用户名生成冲突，请稍后重试");
    }

    /** 昵称派生工作室名，缺省 {@value #DEFAULT_STUDIO_NAME}。 */
    private String deriveStudioName(String nickname) {
        if (isBlank(nickname)) {
            return DEFAULT_STUDIO_NAME;
        }
        return truncate(nickname.trim(), MAX_STUDIO_NAME_LEN) + "的工作室";
    }

    /** 解析 bindToken；非法/过期一律 401。 */
    private Claims parseBindToken(String bindToken) {
        try {
            return jwtUtil.parse(bindToken);
        } catch (Exception e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "bindToken 无效或已过期，请重新登录后再绑定");
        }
    }

    private User loadUser(Long studioId, Long userId) {
        return userRepository.findByStudioIdAndId(studioId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "用户不存在或不属于当前工作室"));
    }

    private Studio loadStudio(Long studioId) {
        return studioRepository.findById(studioId)
                .orElseThrow(() -> new BizException(ErrorCode.SYSTEM, "工作室数据异常"));
    }

    private String issueToken(User user, Studio studio) {
        return jwtUtil.generateToken(user.getId(), studio.getId(), user.getUsername(), user.getRole());
    }

    private WechatLoginResponse buildResponse(User user, Studio studio, boolean isNewUser, boolean needBind) {
        return WechatLoginResponse.builder()
                .token(issueToken(user, studio))
                .user(UserDTO.from(user))
                .studio(StudioDTO.from(studio))
                .isNewUser(isNewUser)
                .needBind(needBind)
                .build();
    }

    /** JSON 取文本，空字符串归一为 {@code null}。 */
    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    /**
     * 微信侧返回的统一用户信息（内部传输载体）。
     *
     * @param appType    终端类型
     * @param openid     该终端下的唯一标识
     * @param unionId    开放平台 UnionID，可能为空（未绑定开放平台）
     * @param sessionKey 仅小程序有值
     * @param nickname   昵称，可能为空
     * @param avatarUrl  头像，可能为空
     */
    public record WechatUserInfo(
            WechatAppType appType,
            String openid,
            String unionId,
            String sessionKey,
            String nickname,
            String avatarUrl) {
    }
}
