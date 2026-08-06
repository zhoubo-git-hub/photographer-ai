package com.photogai.modules.team.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邀请成员请求。至少填写 email 或 phone 之一。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamInviteRequest {
    private String email;
    private String phone;

    /** ADMIN | MEMBER | READONLY */
    @NotBlank(message = "邀请角色不能为空")
    private String role;
}
