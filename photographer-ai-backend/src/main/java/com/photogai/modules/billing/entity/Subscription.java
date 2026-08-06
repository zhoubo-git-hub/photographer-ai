package com.photogai.modules.billing.entity;

import com.photogai.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 订阅：PRO/TEAM 有效期的唯一真源。
 *
 * <p>{@code status=ACTIVE} 且 {@code expiresAt > now} 即视为有效（{@code isPro/isTeam} 判定依据）。
 * {@code plan_type} 仅接受 {@code PRO}/{@code TEAM}（VARCHAR，无需 DDL 变更）。
 */
@Entity
@Table(name = "subscription")
@Getter
@Setter
@NoArgsConstructor
public class Subscription extends BaseEntity {

    @Column(name = "studio_id", nullable = false)
    private Long studioId;

    /** PRO | TEAM */
    @Column(name = "plan_type", nullable = false)
    private String planType;

    /** ACTIVE | CANCELLED | EXPIRED */
    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew = true;

    /** WECHAT | ALIPAY | MOCK */
    @Column(length = 20)
    private String channel;
}
