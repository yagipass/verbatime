package io.github.yagipass.verbatime.examples.war;

import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.OutOfStockException;
import io.github.yagipass.verbatime.examples.workload.Receipt;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(urlPatterns = "/orders", loadOnStartup = 1)
public final class OrderServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  private transient OrderService orders;

  @Override
  public void init() {
    orders = StartupListener.orders(getServletContext());
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String sku = param(req, "sku", "widget");
    int qty;
    try {
      qty = Integer.parseInt(param(req, "qty", "1"));
    } catch (NumberFormatException e) {
      text(resp, HttpServletResponse.SC_BAD_REQUEST, "qty must be an integer");
      return;
    }
    try {
      Receipt receipt = orders.placeOrder(sku, qty);
      text(resp, HttpServletResponse.SC_OK, receipt.toText());
    } catch (OutOfStockException e) {
      text(resp, HttpServletResponse.SC_CONFLICT, e.getMessage());
    }
  }

  static void text(HttpServletResponse resp, int status, String body) throws IOException {
    resp.setStatus(status);
    resp.setContentType("text/plain;charset=UTF-8");
    resp.getWriter().println(body);
  }

  private static String param(HttpServletRequest req, String name, String dflt) {
    String v = req.getParameter(name);
    return v == null || v.isEmpty() ? dflt : v;
  }
}
