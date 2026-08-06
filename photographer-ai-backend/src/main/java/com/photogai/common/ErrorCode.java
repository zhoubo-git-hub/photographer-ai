package com.photogai.common;

/**
 * 全局错误码约定。
 *
 * <ul>
 *   <li>0 成功</li>
 *   <li>400 VALIDATION 入参错误</li>
 *   <li>401 UNAUTHORIZED 未登录/过期</li>
 *   <li>403 FORBIDDEN 额度不足/无权限</li>
 *   <li>404 NOT_FOUND 资源不存在</li>
 *   <li>409 CONFLICT 档期冲突</li>
 *   <li>500 SYSTEM 系统错误</li>
 * </ul>
 */
public enum ErrorCode {

    SUCCESS(0, "ok"),
    VALIDATION(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限或额度不足"),
    PRO_REQUIRED(403, "该功能为专业版专属，请升级专业版"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "档期冲突"),
    SYSTEM(500, "系统错误"),

    // ===== 阶段3 增量错误码 =====
    /** 402：需先订阅并支付（用于"订阅已到期"单独引导续费，区别于从未订阅的 403）。 */
    PAYMENT_REQUIRED(402, "订阅已到期，请续费以继续使用"),
    /** 支付失败（微信商户缺配置或通道异常）。 */
    PAYMENT_FAILED(400, "支付失败"),
    /** 订阅已到期（消息语义，配合 402 引导续费）。 */
    SUBSCRIPTION_EXPIRED(403, "订阅已到期，请续费"),
    /** 无有效订阅（查询订阅时）。 */
    SUBSCRIPTION_NOT_FOUND(404, "无有效订阅"),
    /** 需团队版（TEAM 专属功能）。 */
    TEAM_REQUIRED(403, "该功能需团队版"),
    /** 邀请无效或已过期。 */
    INVALID_INVITATION(400, "邀请无效或已过期"),
    /** 团队人数已达上限（默认 5）。 */
    MEMBER_LIMIT_EXCEEDED(403, "团队人数已达上限"),
    /** 校准样本不足，建议仅供参考。 */
    CALIBRATION_SAMPLE_INSUFFICIENT(409, "样本不足，建议仅供参考"),
    /** 校准超出安全边界，禁止采纳写回。 */
    CALIBRATION_OUT_OF_BOUND(409, "校准超出安全边界，禁止采纳"),

    // ===== 移动端扩张（三端打通）增量错误码 =====
    /** 微信 code 无效 / 换票失败（含未配置 appid-secret、微信接口 errcode 非 0）。 */
    WECHAT_CODE_INVALID(400, "微信 code 无效或已失效，请重新授权"),
    /** 该微信已绑定其他账号（openid 或 unionid 已被占用）。 */
    WECHAT_BIND_CONFLICT(409, "该微信已绑定其他账号");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
