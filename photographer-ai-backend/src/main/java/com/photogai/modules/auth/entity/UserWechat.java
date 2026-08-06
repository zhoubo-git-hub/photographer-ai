package com.photogai.modules.auth.entity;

import com.photogai.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 微信绑定关系：三端（小程序 / App / Web）同一 studio 的纽带。
 *
 * <p>唯一键 {@code (app_type, openid)}：同一终端同一微信号只允许一条绑定；
 * {@code union_id} 建普通索引：任一端登录命中同一 UnionID 即复用同一 {@code user + studio}。
 *
 * <p>多租户：{@code studio_id} 冗余存储，便于按租户清理与审计（与 user 所属 studio 一致）。
 */
@Entity
@Table(
        name = "user_wechat",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_wechat_app_openid", columnNames = {"app_type", "openid"}),
        indexes = {
                @Index(name = "idx_user_wechat_union", columnList = "union_id"),
                @Index(name = "idx_user_wechat_studio", columnList = "studio_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class UserWechat extends BaseEntity {

    /** 绑定到的用户 ID。 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 多租户隔离字段，与用户所属 studio 保持一致。 */
    @Column(name = "studio_id", nullable = false)
    private Long studioId;

    /** WEB | APP | MP，见 {@code WechatAppType}。 */
    @Column(name = "app_type", nullable = false, length = 10)
    private String appType;

    /** 该终端下的微信用户唯一标识。 */
    @Column(name = "openid", nullable = false, length = 64)
    private String openid;

    /** 微信开放平台 UnionID：三端打通关键，未绑定开放平台时可为空。 */
    @Column(name = "union_id", length = 64)
    private String unionId;

    /** 仅小程序：{@code code2Session} 换取的 session_key（解密手机号等场景使用）。 */
    @Column(name = "session_key", length = 64)
    private String sessionKey;

    /** 微信昵称（开放平台 userinfo 返回；小程序侧可能为空）。 */
    @Column(name = "nickname", length = 100)
    private String nickname;

    /** 微信头像地址。 */
    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;
}
