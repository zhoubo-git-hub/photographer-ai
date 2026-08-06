package com.photogai.modules.quota;

import com.photogai.modules.quota.entity.Quota;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 额度仓储。
 */
public interface QuotaRepository extends JpaRepository<Quota, Long> {

    Optional<Quota> findByStudioId(Long studioId);
}
