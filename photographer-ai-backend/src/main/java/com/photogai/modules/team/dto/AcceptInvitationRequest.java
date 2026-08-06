package com.photogai.modules.team.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 接受邀请请求：受邀人自设用户名/密码，绑定同一 Studio。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcceptInvitationRequest {
    @NotBlank(message = "邀请 token 不能为空")
    private String token;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
