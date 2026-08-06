package com.photogai.modules.order;

import com.photogai.modules.order.entity.Reminder;
import com.photogai.modules.order.enums.ReminderStatus;
import java.util.List;
import java.util.Optional;

import com.photogai.modules.order.enums.ReminderType;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 提醒仓储。查询强制按 {@code studio_id} 隔离。
 */
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findByStudioIdAndStatus(Long studioId, ReminderStatus status);

    List<Reminder> findByStudioIdAndTypeAndStatus(
            Long studioId, ReminderType type, ReminderStatus status);

    List<Reminder> findByStudioIdAndCustomerIdAndType(
            Long studioId, Long customerId, ReminderType type);

    boolean existsByStudioIdAndCustomerIdAndTypeAndStatus(
            Long studioId, Long customerId, ReminderType type, ReminderStatus status);

    Optional<Reminder> findByIdAndStudioId(Long id, Long studioId);
}
