package com.photogai.modules.auth.dto;

import com.photogai.modules.auth.entity.User;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private Long id;
    private Long studioId;
    private String username;
    private String email;
    private String role;
    /** 头像地址（微信登录后自动回填，可为空）。 */
    private String avatarUrl;
    private LocalDateTime createdAt;

    public static UserDTO from(User user) {
        if (user == null) {
            return null;
        }
        return UserDTO.builder()
                .id(user.getId())
                .studioId(user.getStudioId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
