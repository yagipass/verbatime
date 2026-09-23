package io.github.yagipass.verbatime.examples.quarkus;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.OutOfStockException;
import io.github.yagipass.verbatime.examples.workload.Receipt;

@Path("/")
public class OrderResource {

    @Inject
    OrderService orders;

    @GET
    @Path("/orders")
    @Produces(MediaType.APPLICATION_JSON)
    public Receipt place(@RestQuery @DefaultValue("widget") final String sku, @RestQuery @DefaultValue("1") final int qty) {
        return orders.placeOrder(sku, qty);
    }

    @GET
    @Path("/healthz")
    @Produces(MediaType.TEXT_PLAIN)
    public String healthz() {
        return "ok";
    }

    @ServerExceptionMapper
    public RestResponse<String> outOfStock(final OutOfStockException e) {
        return RestResponse.status(Response.Status.CONFLICT, e.getMessage());
    }
}
