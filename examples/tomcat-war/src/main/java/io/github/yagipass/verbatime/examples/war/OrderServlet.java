package io.github.yagipass.verbatime.examples.war;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.OutOfStockException;
import io.github.yagipass.verbatime.examples.workload.Receipt;

@WebServlet(urlPatterns = "/orders", loadOnStartup = 1)
public final class OrderServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private transient OrderService orders;

    @Override
    public void init() {
        orders = StartupListener.orders(getServletContext());
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        final String sku = param(req, "sku", "widget");
        final int qty;
        try {
            qty = Integer.parseInt(param(req, "qty", "1"));
        } catch (final NumberFormatException e) {
            text(resp, HttpServletResponse.SC_BAD_REQUEST, "qty must be an integer");
            return;
        }
        try {
            final Receipt receipt = orders.placeOrder(sku, qty);
            text(resp, HttpServletResponse.SC_OK, receipt.toText());
        } catch (final OutOfStockException e) {
            text(resp, HttpServletResponse.SC_CONFLICT, e.getMessage());
        }
    }

    private static String param(final HttpServletRequest req, final String name, final String dflt) {
        final String v = req.getParameter(name);
        return v == null || v.isEmpty() ? dflt : v;
    }

    static void text(final HttpServletResponse resp, final int status, final String body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("text/plain;charset=UTF-8");
        resp.getWriter().println(body);
    }
}
