package com.fimory.api.common;

import java.util.Map;

public record ApiResponse<T>(boolean success, T data, Map<String, Object> meta) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, Map.of());
    }

    public static <T> ApiResponse<T> ok(T data, Map<String, Object> meta) {
        return new ApiResponse<>(true, data, meta);
    }
}
