package com.photogai.modules.order.enums;

/**
 * 订单状态：流转顺序即枚举声明顺序，仅允许相邻状态正向/回退。
 *
 * <p>CONSULT → DEPOSIT → SHOOT → EDIT → DELIVER → REPURCHASE
 */
public enum OrderStatus {
    CONSULT,    // 咨询中
    DEPOSIT,    // 已付定金
    SHOOT,      // 拍摄中
    EDIT,       // 修图中
    DELIVER,    // 已交付
    REPURCHASE; // 复购

    /** 在流转链中的序号（从 0 起）。 */
    public int order() {
        return this.ordinal();
    }
}
