package com.photogai.modules.team;

import com.photogai.modules.team.entity.TeamInvitation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 团队邀请仓储。按 {@code studio_id} 隔离。
 */
public interface TeamInvitationRepository extends JpaRepository<TeamInvitation, Long> {

    Optional<TeamInvitation> findByToken(String token);

    List<TeamInvitation> findByStudioIdAndStatus(Long studioId, String status);
}
