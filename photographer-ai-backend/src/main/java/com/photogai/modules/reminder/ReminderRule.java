package com.photogai.modules.reminder;

import com.photogai.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 提醒规则：可配置触发点（事件）+ 偏移天数 + 启停 + 渠道。
 *
 * <p>PRO 工作室按规则驱动提醒；FREE 不可配置（回退硬编码）。多租户按 {@code studio_id} 隔离。
 */
@Entity
@Table(name = "reminder_rule")
@Getter
@Setter
@NoArgsConstructor
public class ReminderRule extends BaseEntity {

    @Column(name = "studio_id", nullable = false)
    private Long studioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReminderTriggerEvent event;

    /** 偏移天数，负=提前（如拍摄前1天=-1）。 */
    @Column(name = "offset_days", nullable = false)
    private int offsetDays;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 20)
    private String channel = "INAPP";
}
