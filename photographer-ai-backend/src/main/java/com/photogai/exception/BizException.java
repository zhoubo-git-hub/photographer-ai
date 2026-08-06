package com.photogai.exception;

import com.photogai.common.ErrorCode;
import lombok.Getter;

/**
 * 业务异常。携带 HTTP 语义错误码（见 {@link ErrorCode}）。
 *
 * <p>示例：免费额度触顶抛 {@code new BizException(ErrorCode.FORBIDDEN, "已达 10 单上限")}；
 * 档期冲突抛 {@code new BizException(ErrorCode.CONFLICT, "档期冲突")}。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
