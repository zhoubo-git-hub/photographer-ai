package com.photogai.common;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 分页数据载体，对齐前端 TanStack Query 的 {@code Page<T>} 结构。
 *
 * @param <T> 行数据类型
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageData<T> {

    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int number;
    private int size;
}
