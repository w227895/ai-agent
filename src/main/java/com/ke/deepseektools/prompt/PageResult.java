package com.ke.deepseektools.prompt;

import java.util.List;

public record PageResult<T>(
        List<T> items,
        long total,
        int page,
        int size,
        int totalPages) {

    public PageResult(List<T> items, long total, int page, int size) {
        this(items, total, page, size, (int) Math.ceil((double) total / size));
    }
}
