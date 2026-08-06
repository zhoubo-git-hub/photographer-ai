package com.photogai.modules.auth;

import com.photogai.common.ErrorCode;
import com.photogai.config.JwtUtil;
import com.photogai.exception.BizException;
import com.photogai.modules.auth.dto.AuthResponse;
import com.photogai.modules.auth.dto.LoginRequest;
import com.photogai.modules.auth.dto.RegisterRequest;
import com.photogai.modules.auth.dto.UserDTO;
import com.photogai.modules.auth.entity.User;
import com.photogai.modules.quota.QuotaService;
import com.photogai.modules.studio.entity.Studio;
import com.photogai.modules.studio.dto.StudioDTO;
import com.photogai.modules.studio.StudioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务：注册（建工作室 + 首个 OWNER 用户 + 初始额度）、登录签发 JWT。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final StudioRepository studioRepository;
    private final QuotaService quotaService;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userService.findByUsername(req.getUsername()).isPresent()) {
            throw new BizException(ErrorCode.VALIDATION, "用户名已存在");
        }

        // 邮箱选填：仅当非空时才校验全局唯一，避免空值误判重复。
        if (req.getEmail() != null && !req.getEmail().isBlank()
                && userRepository.findByEmail(req.getEmail()).isPresent()) {
            throw new BizException(ErrorCode.VALIDATION, "邮箱已被注册");
        }

        Studio studio = new Studio();
        studio.setName(req.getStudioName());
        studio.setPlanType("FREE");
        Studio savedStudio = studioRepository.save(studio);

        User user = new User();
        user.setStudioId(savedStudio.getId());
        user.setUsername(req.getUsername());
        user.setPasswordHash(userService.encodePassword(req.getPassword()));
        user.setEmail(req.getEmail());
        user.setRole("OWNER");
        User savedUser = userRepository.save(user);

        savedStudio.setOwnerUserId(savedUser.getId());
        studioRepository.save(savedStudio);

        // 初始化额度行（在管订单数归零）
        quotaService.recountOrders(savedStudio.getId());

        String token = jwtUtil.generateToken(
                savedUser.getId(), savedStudio.getId(), savedUser.getUsername(), savedUser.getRole());

        return AuthResponse.builder()
                .token(token)
                .user(UserDTO.from(savedUser))
                .studio(StudioDTO.from(savedStudio))
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = userService.findByUsername(req.getUsername())
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误"));

        if (!userService.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        Studio studio = studioRepository.findById(user.getStudioId())
                .orElseThrow(() -> new BizException(ErrorCode.SYSTEM, "工作室数据异常"));

        String token = jwtUtil.generateToken(
                user.getId(), studio.getId(), user.getUsername(), user.getRole());

        return AuthResponse.builder()
                .token(token)
                .user(UserDTO.from(user))
                .studio(StudioDTO.from(studio))
                .build();
    }
}
