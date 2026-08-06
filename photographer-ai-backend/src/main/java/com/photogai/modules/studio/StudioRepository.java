package com.photogai.modules.studio;

import com.photogai.modules.studio.entity.Studio;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 工作室仓储。
 */
public interface StudioRepository extends JpaRepository<Studio, Long> {

    /** 按套餐类型查询（复购引擎扫描 PRO 工作室）。 */
    java.util.List<Studio> findAllByPlanType(String planType);
}
