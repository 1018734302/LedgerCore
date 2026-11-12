package com.ledgercore.web;

import com.ledgercore.common.ApiResponse;
import com.ledgercore.common.LedgerException;
import com.ledgercore.common.Trace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LedgerException.class)
    public ApiResponse<Void> onBusiness(LedgerException e) {
        log.warn("[{}] 业务拒绝: {}", Trace.current(), e.getMessage());
        return ApiResponse.fail(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> onUnexpected(Exception e) {
        log.error("[{}] 未预期异常", Trace.current(), e);
        return ApiResponse.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
    }
}
