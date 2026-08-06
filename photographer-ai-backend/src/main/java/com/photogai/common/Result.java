package com.photogai.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 统一响应包装。
 *
 * <p>约定：{@code code=0} 成功，{@code code>0} 业务错误。分页场景 {@code data} 为
 * {@link PageData}。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Result<T> {

    /** 错误码，0 表示成功。 */
    private int code;

    /** 业务数据。 */
    private T data;

    /** 提示信息。 */
    private String message;

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, data, "ok");
    }

    public static <T> Result<T> ok() {
        return new Result<>(0, null, "ok");
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, null, message);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), null, message);
    }
}
