package com.photogai.modules.contract;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 合同模板仓储。内置模板（studio_id=NULL）对所有租户可见，自定义仅本 studio。
 */
public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, Long> {

    /** 内置模板 + 本工作室自定义模板。 */
    List<ContractTemplate> findByStudioIdIsNullOrStudioId(Long studioId);

    boolean existsByIdAndStudioIdIsNullOrStudioId(Long id, Long studioId);
}
