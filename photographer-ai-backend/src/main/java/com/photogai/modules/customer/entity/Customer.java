package com.photogai.modules.customer.entity;

import com.photogai.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 客户档案：联系方式、标签（逗号分隔）、备注、复购画像。软删除。
 *
 * <p>阶段2 扩展画像字段：{@code lastShootDate}/{@code repurchaseCycleDays} 供复购引擎；
 * {@code birthday}/{@code anniversary}/{@code sourceChannel} 供画像；{@code repurchaseEnabled} 总开关。
 */
@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
public class Customer extends BaseEntity {

    @Column(name = "studio_id", nullable = false)
    private Long studioId;

    @Column(nullable = false)
    private String name;

    @Column(name = "wechat_id")
    private String wechatId;

    @Column(length = 30)
    private String phone;

    /** 逗号分隔标签，如 "婚纱/高客单" */
    private String tags;

    @Column(columnDefinition = "text")
    private String note;

    /** 最近拍摄日（复购周期起点）。 */
    @Column(name = "last_shoot_date")
    private LocalDate lastShootDate;

    /** 复购周期天数（默认按拍摄类型 365，可覆盖）。 */
    @Column(name = "repurchase_cycle_days")
    private Integer repurchaseCycleDays;

    /** 生日。 */
    @Column(name = "birthday")
    private LocalDate birthday;

    /** 纪念日（如婚期）。 */
    @Column(name = "anniversary")
    private LocalDate anniversary;

    /** 是否开启复购提醒，默认 true。 */
    @Column(name = "repurchase_enabled", nullable = false)
    private Boolean repurchaseEnabled = true;

    /** 来源渠道：微信/小红书/转介绍。 */
    @Column(name = "source_channel", length = 30)
    private String sourceChannel;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
