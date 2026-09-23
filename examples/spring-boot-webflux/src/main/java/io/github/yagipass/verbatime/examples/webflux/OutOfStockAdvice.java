package io.github.yagipass.verbatime.examples.webflux;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.yagipass.verbatime.examples.workload.OutOfStockException;

@RestControllerAdvice
public class OutOfStockAdvice {

    @ExceptionHandler(OutOfStockException.class)
    public ResponseEntity<String> outOfStock(final OutOfStockException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
}
