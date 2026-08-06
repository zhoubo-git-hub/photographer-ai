package com.photogai.modules.auth;

import com.photogai.modules.auth.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用户仓储。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    /** 按邮箱精确查重（选填语义：仅当 email 非空时调用）。 */
    Optional<User> findByEmail(String email);

    Optional<User> findByStudioIdAndId(Long studioId, Long id);

    /** 同工作室全部成员（团队协作，含 OWNER）。 */
    java.util.List<User> findByStudioId(Long studioId);

    long countByStudioId(Long studioId);
}
