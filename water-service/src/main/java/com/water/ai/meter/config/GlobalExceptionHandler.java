package com.water.ai.meter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,Object> malformedInput(Exception e) {
        return Map.of("success",false,"code",400,"message","请求参数或日期格式无效");
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Map<String,Object>> handleStatus(org.springframework.web.server.ResponseStatusException e) {
        return org.springframework.http.ResponseEntity.status(e.getStatusCode()).body(Map.of("success",false,"code",e.getStatusCode().value(),"message",e.getReason()==null?"请求失败":e.getReason()));
    }

    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleDuplicateKey(org.springframework.dao.DuplicateKeyException e) {
        return Map.of("success", false, "code", 409,
                "message", "请求号或业务编号已存在，请核对内容；重试原登记时使用相同金额和渠道");
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return Map.of(
                "success", false,
                "code", 400,
                "message", "参数校验失败",
                "errors", errors
        );
    }

    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBindException(BindException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return Map.of(
                "success", false,
                "code", 400,
                "message", "参数绑定失败",
                "errors", errors
        );
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数异常: {}", e.getMessage());
        return Map.of(
                "success", false,
                "code", 400,
                "message", e.getMessage()
        );
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: ", e);
        return Map.of(
                "success", false,
                "code", 500,
                "message", "系统内部错误，请稍后重试"
        );
    }

    /**
     * 处理所有未捕获异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleException(Exception e) {
        log.error("系统异常: ", e);
        return Map.of(
                "success", false,
                "code", 500,
                "message", "系统异常，请联系管理员"
        );
    }
}
