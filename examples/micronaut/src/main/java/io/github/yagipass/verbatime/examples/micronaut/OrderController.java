package io.github.yagipass.verbatime.examples.micronaut;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.OutOfStockException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;

@Controller
public class OrderController {

    private final OrderService orders;

    OrderController(final OrderService orders) {
        this.orders = orders;
    }

    @Get("/orders")
    @Produces(MediaType.TEXT_PLAIN)
    public String place(@QueryValue(defaultValue = "widget") final String sku, @QueryValue(defaultValue = "1") final int qty) {
        return orders.placeOrder(sku, qty).toText() + "\n";
    }

    @Get("/healthz")
    @Produces(MediaType.TEXT_PLAIN)
    public String healthz() {
        return "ok\n";
    }

    @Error(exception = OutOfStockException.class)
    public HttpResponse<String> outOfStock(final OutOfStockException e) {
        return HttpResponse.status(HttpStatus.CONFLICT).body(e.getMessage() + "\n");
    }
}
