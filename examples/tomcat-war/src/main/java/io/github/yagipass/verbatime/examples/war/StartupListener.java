package io.github.yagipass.verbatime.examples.war;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import io.github.yagipass.verbatime.examples.workload.OrderService;

@WebListener
public final class StartupListener implements ServletContextListener {

    private static final String ATTR = OrderService.class.getName();

    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        final OrderService orders = new OrderService();
        orders.placeOrder("warmup", 1);
        sce.getServletContext().setAttribute(ATTR, orders);
    }

    static OrderService orders(final ServletContext context) {
        return (OrderService) context.getAttribute(ATTR);
    }
}
