package io.github.yagipass.verbatime.examples.helidon;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.OutOfStockException;
import io.github.yagipass.verbatime.examples.workload.Receipt;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

public final class OrdersService implements HttpService {

  private final OrderService orders;

  public OrdersService(OrderService orders) {
    this.orders = orders;
  }

  @Override
  public void routing(HttpRules rules) {
    rules.get("/healthz", (req, res) -> res.send("ok\n")).get("/orders", this::place);
  }

  @Override
  public void beforeStart() {
    orders.placeOrder("warmup", 1);
  }

  private void place(ServerRequest req, ServerResponse res) {
    String sku = req.query().first("sku").orElse("widget");
    int qty;
    try {
      qty = Integer.parseInt(req.query().first("qty").orElse("1"));
    } catch (NumberFormatException e) {
      res.status(Status.BAD_REQUEST_400).send("qty must be an integer\n");
      return;
    }
    try {
      Receipt receipt = orders.placeOrder(sku, qty);
      res.send(receipt.toText() + "\n");
    } catch (OutOfStockException e) {
      res.status(Status.CONFLICT_409).send(e.getMessage() + "\n");
    }
  }
}
