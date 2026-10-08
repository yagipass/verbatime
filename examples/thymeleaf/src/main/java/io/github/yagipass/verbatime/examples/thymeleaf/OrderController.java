package io.github.yagipass.verbatime.examples.thymeleaf;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.OutOfStockException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
public class OrderController {

  private final OrderService orders;

  OrderController(OrderService orders) {
    this.orders = orders;
  }

  @GetMapping("/orders")
  public String place(
      @RequestParam(defaultValue = "widget") String sku,
      @RequestParam(defaultValue = "1") int qty,
      Model model) {
    model.addAttribute("receipt", orders.placeOrder(sku, qty));
    return "order";
  }

  @ExceptionHandler(OutOfStockException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public String outOfStock(OutOfStockException e, Model model) {
    model.addAttribute("message", e.getMessage());
    return "error";
  }
}
