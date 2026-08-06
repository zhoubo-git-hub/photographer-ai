package com.photogai.modules.contract;

import com.photogai.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 合同模板：内置（studio_id=NULL，对所有租户可见）或工作室自定义。
 * 内容含 {@code {{占位符}}}，生成时按订单信息替换。
 */
@Entity
@Table(name = "contract_template")
@Getter
@Setter
@NoArgsConstructor
public class ContractTemplate extends BaseEntity {

    /** NULL = 系统内置模板，对所有 studio 可见。 */
    @Column(name = "studio_id")
    private Long studioId;

    @Column(nullable = false)
    private String name;

    @Column(length = 50)
    private String category;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean builtin = false;

    @Column(name = "created_by")
    private Long createdBy;
}
