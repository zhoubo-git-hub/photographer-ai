package com.photogai.modules.ai;

import com.photogai.modules.ai.entity.QuoteCalibration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 报价校准仓储。按 {@code studio_id} 隔离。
 */
public interface QuoteCalibrationRepository extends JpaRepository<QuoteCalibration, Long> {

    List<QuoteCalibration> findByStudioId(Long studioId);

    List<QuoteCalibration> findByStudioIdAndStatus(Long studioId, String status);

    Optional<QuoteCalibration> findByStudioIdAndDimensionKey(Long studioId, String dimensionKey);
}
