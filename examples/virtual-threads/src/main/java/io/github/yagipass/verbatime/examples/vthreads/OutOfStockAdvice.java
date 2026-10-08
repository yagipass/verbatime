package io.github.yagipass.verbatime.examples.vthreads;

import io.github.yagipass.verbatime.examples.workload.OutOfStockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OutOfStockAdvice {

  @ExceptionHandler(OutOfStockException.class)
  public ResponseEntity<String> outOfStock(OutOfStockException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
  }
}
