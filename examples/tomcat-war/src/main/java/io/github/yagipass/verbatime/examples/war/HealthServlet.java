package io.github.yagipass.verbatime.examples.war;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/healthz")
public final class HealthServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        OrderServlet.text(resp, HttpServletResponse.SC_OK, "ok");
    }
}
