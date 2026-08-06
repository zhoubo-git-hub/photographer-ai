package com.photogai.modules.studio.entity;

import com.photogai.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 工作室：多租户根。MVP 单 studio 单人；P2 团队复用。
 */
@Entity
@Table(name = "studio")
@Getter
@Setter
@NoArgsConstructor
public class Studio extends BaseEntity {

    @Column(nullable = false)
    private String name;

    /** FREE | PRO | TEAM（TEAM 列本身 VARCHAR，直接接受新值，无需 DDL 变更） */
    @Column(name = "plan_type", nullable = false)
    private String planType = "FREE";

    @Column(name = "owner_user_id")
    private Long ownerUserId;
}
