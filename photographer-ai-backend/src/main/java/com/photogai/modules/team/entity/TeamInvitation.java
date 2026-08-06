package com.photogai.modules.team.entity;

import com.photogai.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 团队邀请：OWNER/ADMIN 发出，受邀人凭 token 加入同一 Studio。
 *
 * <p>{@code role} 为受邀人加入后的角色（ADMIN | MEMBER | READONLY）；{@code token} 唯一。
 */
@Entity
@Table(name = "team_invitation")
@Getter
@Setter
@NoArgsConstructor
public class TeamInvitation extends BaseEntity {

    @Column(name = "studio_id", nullable = false)
    private Long studioId;

    @Column(name = "inviter_id", nullable = false)
    private Long inviterId;

    @Column(length = 120)
    private String email;

    @Column(length = 30)
    private String phone;

    /** ADMIN | MEMBER | READONLY */
    @Column(nullable = false)
    private String role;

    /** 接受邀请凭证（唯一）。 */
    @Column(nullable = false, unique = true)
    private String token;

    /** PENDING | ACCEPTED | EXPIRED */
    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accepted_user_id")
    private Long acceptedUserId;
}
