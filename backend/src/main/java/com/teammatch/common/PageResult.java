package com.teammatch.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

/**
 * 分页结果。page 从 1 开始。
 */
public record PageResult<T>(List<T> records, long total, long page, long size) {

    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> mapper) {
        return new PageResult<>(page.getRecords().stream().map(mapper).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    public static <T> PageResult<T> of(IPage<?> page, List<T> records) {
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
