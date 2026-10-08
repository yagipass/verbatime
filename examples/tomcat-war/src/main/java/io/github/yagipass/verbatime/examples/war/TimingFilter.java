package io.github.yagipass.verbatime.examples.war;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter("/*")
public final class TimingFilter extends HttpFilter {

  private static final long serialVersionUID = 1L;

  private transient long initializedAtNanos;

  @Override
  public void init() {
    initializedAtNanos = System.nanoTime();
  }

  @Override
  protected void doFilter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
      throws IOException, ServletException {
    long start = System.nanoTime();
    try {
      chain.doFilter(req, resp);
    } finally {
      resp.setHeader("X-Elapsed-Micros", Long.toString((System.nanoTime() - start) / 1_000));
      resp.setHeader(
          "X-Uptime-Millis", Long.toString((System.nanoTime() - initializedAtNanos) / 1_000_000));
    }
  }
}
