package com.photogai.modules.team;

import com.photogai.common.ErrorCode;
import com.photogai.exception.BizException;

/**
 * 团队角色矩阵：在 Service 入口集中校验，避免散落 Controller。
 *
 * <p>角色：OWNER（全权）/ ADMIN（≈OWNER，不可退订/转让）/ MEMBER（CRUD 订单客户）/ READONLY（仅读）。
 * 越权统一抛 {@link ErrorCode#TEAM_REQUIRED}(403) 或 {@link ErrorCode#FORBIDDEN}(403)。
 *
 * <p>本类为无状态工具类（静态方法），不涉及 Spring Bean 依赖，可被 billing 等其它包直接调用。
 */
public final class RoleGuard {

    private RoleGuard() {
    }

    /** 团队管理（邀请/移除/改角色/退订）：仅 OWNER / ADMIN。 */
    public static void assertManageTeam(String role) {
        assertOwnerOrAdmin(role);
    }

    /** 计费管理（订阅查询等）：仅 OWNER / ADMIN。 */
    public static void assertManageBilling(String role) {
        assertOwnerOrAdmin(role);
    }

    /** 所有者专属动作（退订 / 转让等）：仅 OWNER。ADMIN 近似 OWNER，但 PRD Q3 明确不可退订/转让。 */
    public static void assertOwnerOnly(String role) {
        if (!"OWNER".equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "该功能仅工作室所有者可操作");
        }
    }

    /** 订单写权限：非 READONLY 即可。 */
    public static void assertWriteOrder(String role) {
        if ("READONLY".equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只读成员不可操作订单");
        }
    }

    /** OWNER / ADMIN 校验。 */
    public static void assertOwnerOrAdmin(String role) {
        if (!"OWNER".equals(role) && !"ADMIN".equals(role)) {
            throw new BizException(ErrorCode.TEAM_REQUIRED, "该功能需团队版（仅所有者或管理员可操作）");
        }
    }
}
