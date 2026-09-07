package com.water.ai.meter.common;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ApiResult<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResult<T> ok(T data) {
        ApiResult<T> r = new ApiResult<>();
        r.success = true;
        r.message = "ok";
        r.data = data;
        return r;
    }

    public static <T> ApiResult<T> ok(String message, T data) {
        ApiResult<T> r = ok(data);
        r.message = message;
        return r;
    }

    public static <T> ApiResult<T> fail(String message) {
        ApiResult<T> r = new ApiResult<>();
        r.success = false;
        r.message = message;
        return r;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("success", success);
        map.put("message", message);
        map.put("data", data);
        return map;
    }
}
