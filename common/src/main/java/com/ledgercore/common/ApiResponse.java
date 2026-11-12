package com.ledgercore.common;

import lombok.Data;

@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private String traceNo;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 0;
        r.message = "OK";
        r.data = data;
        r.traceNo = Trace.current();
        return r;
    }

    public static <T> ApiResponse<T> fail(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 1;
        r.message = message;
        r.traceNo = Trace.current();
        return r;
    }
}
