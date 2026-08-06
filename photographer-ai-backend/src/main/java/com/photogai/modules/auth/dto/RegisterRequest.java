package com.photogai.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册请求：同时创建工作室外与首个 OWNER 用户。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度 3-50")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度 6-100")
    private String password;

    /** 选填：仅当非空时才校验格式与全局唯一（@Email 对 null/blank 视为合法）。 */
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "工作室名称不能为空")
    private String studioName;
}
