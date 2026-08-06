package com.photogai.modules.auth.entity;

import com.photogai.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户（摄影师账号）。MVP 单人；P2 成员/权限。
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(name = "studio_id", nullable = false)
    private Long studioId;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(length = 120)
    private String email;

    /** OWNER | ADMIN | MEMBER | READONLY（角色矩阵见 team.RoleGuard） */
    @Column(nullable = false)
    private String role = "OWNER";

    /** 头像地址（微信登录自动回填，支撑 App / 小程序展示）。 */
    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;
}
