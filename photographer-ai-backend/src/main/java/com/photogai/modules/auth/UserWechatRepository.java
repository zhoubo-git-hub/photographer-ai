package com.photogai.modules.auth;

import com.photogai.modules.auth.entity.UserWechat;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 微信绑定仓储。
 *
 * <p>说明：{@code openid} / {@code union_id} 是微信侧的全局标识，查绑定时<b>不能</b>带
 * {@code studio_id} 过滤（登录时尚不知道属于哪个 studio）；命中记录后再以其
 * {@code studioId} 作为多租户上下文继续查询。
 */
public interface UserWechatRepository extends JpaRepository<UserWechat, Long> {

    /** 按终端 + openid 精确定位（对应唯一索引 {@code uk_user_wechat_app_openid}）。 */
    Optional<UserWechat> findByAppTypeAndOpenid(String appType, String openid);

    /** 同一 UnionID 下的全部终端绑定（三端最多三条）。 */
    List<UserWechat> findByUnionId(String unionId);

    /** 按 UnionID 取最早的一条绑定（首登所建账号），用于三端打通。 */
    Optional<UserWechat> findFirstByUnionIdOrderByIdAsc(String unionId);

    /** 某用户在各终端的绑定列表（多租户：带 studio_id 过滤）。 */
    List<UserWechat> findByStudioIdAndUserId(Long studioId, Long userId);

    /** 某用户在指定终端是否已绑定（多租户：带 studio_id 过滤）。 */
    Optional<UserWechat> findByStudioIdAndUserIdAndAppType(Long studioId, Long userId, String appType);
}
