package com.photogai.modules.order.enums;

/**
 * 提醒类型。
 */
public enum ReminderType {
    DEPOSIT_DUE,        // 定金待付
    SHOOT_TOMORROW,     // 拍摄前1天
    EDIT_OVERDUE,       // 修图超期
    DELIVER_REVIEW,     // 交付后求好评（阶段2 新增）
    REPURCHASE,         // 复购提醒（阶段2 新增，无关联订单）
    SUBSCRIPTION_UPGRADED,  // 订阅升级成功（阶段3 新增，仅 Java 枚举，不改库）
    SUBSCRIPTION_EXPIRED    // 订阅到期降级（阶段3 新增，仅 Java 枚举，不改库）
}
