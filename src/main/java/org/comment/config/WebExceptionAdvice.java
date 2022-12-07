package org.comment.config;

import lombok.extern.slf4j.Slf4j;
import org.comment.dto.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return Result.fail("服务器异常");
    }

    @ExceptionHandler(MethodArgumentNotValidException .class)
    public Result methodArgumentNotValidHandle(MethodArgumentNotValidException e) {
        log.error(e.getMessage());
        return Result.fail(Objects.requireNonNull(e.getBindingResult().getFieldError()).getDefaultMessage());
    }
}
