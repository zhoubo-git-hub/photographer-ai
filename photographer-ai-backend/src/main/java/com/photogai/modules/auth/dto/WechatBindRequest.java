package com.photogai.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信绑定请求：已登录（密码账号）用户把某端微信绑到当前账号，需携带 JWT。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WechatBindRequest {

    /** 终端类型：MP | APP | WEB。 */
    @NotBlank(message = "appType 不能为空")
    @Pattern(regexp = "MP|APP|WEB", message = "appType 仅支持 MP/APP/WEB")
    private String appType;

    /** 微信登录临时票据。 */
    @NotBlank(message = "微信 code 不能为空")
    @Size(max = 200, message = "微信 code 长度非法")
    private String code;
}
