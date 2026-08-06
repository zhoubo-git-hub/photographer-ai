package com.photogai.modules.reminder;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 提醒规则仓储。所有查询按 {@code studio_id} 隔离。
 */
public interface ReminderRuleRepository extends JpaRepository<ReminderRule, Long> {

    List<ReminderRule> findByStudioId(Long studioId);

    List<ReminderRule> findByStudioIdAndEventAndEnabledTrue(
            Long studioId, ReminderTriggerEvent event);

    Optional<ReminderRule> findByIdAndStudioId(Long id, Long studioId);
}
