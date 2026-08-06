package com.photogai.modules.reminder;

/**
 * 提醒触发事件：对应订单状态流转的触发点，或独立的复购周期。
 */
public enum ReminderTriggerEvent {
    DEPOSIT,    // 进入定金
    SHOOT,      // 进入拍摄（拍摄前提醒）
    EDIT,       // 进入修图（修图超期 +7 天，对应阶段1 硬编码）
    DELIVER,    // 进入交付（交付后求好评）
    REPURCHASE  // 复购周期
}
