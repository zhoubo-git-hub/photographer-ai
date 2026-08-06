package com.photogai.modules.team.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 团队成员视图（含其名下订单数）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberDTO {
    private Long id;
    private String username;
    private String email;
    private String role;
    private int orderCount;
    /** 邀请场景下返回接受凭证（成员列表为 null）。 */
    private String token;
    /** 邀请记录 ID（成员列表为 null）。 */
    private Long invitationId;
}
