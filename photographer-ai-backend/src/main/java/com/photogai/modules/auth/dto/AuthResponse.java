package com.photogai.modules.auth.dto;

import com.photogai.modules.studio.dto.StudioDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录/注册响应：Token + 用户 + 工作室。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private UserDTO user;
    private StudioDTO studio;
}
