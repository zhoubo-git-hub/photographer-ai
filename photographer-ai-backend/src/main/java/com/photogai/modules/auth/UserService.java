package com.photogai.modules.auth;

import com.photogai.modules.auth.entity.User;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户领域服务：密码加密、按用户名/ID 查询。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** BCrypt 加密明文密码。 */
    public String encodePassword(String raw) {
        return passwordEncoder.encode(raw);
    }

    /** 校验明文密码与哈希是否匹配。 */
    public boolean matches(String raw, String hash) {
        return passwordEncoder.matches(raw, hash);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findByStudioIdAndId(Long studioId, Long id) {
        return userRepository.findByStudioIdAndId(studioId, id);
    }
}
