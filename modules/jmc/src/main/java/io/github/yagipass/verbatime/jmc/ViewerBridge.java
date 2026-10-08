package io.github.yagipass.verbatime.jmc;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

final class ViewerBridge {

  static final int MAX_RESPONSE_CHARS = 2 * 1024 * 1024;

  static final List<String> HOST_FUNCTIONS =
      List.of(
          "vbtmHostReady",
          "vbtmHostRequestWindow",
          "vbtmHostReload",
          "vbtmHostSelect",
          "vbtmHostRequestSearch",
          "vbtmHostRequestMatch",
          "vbtmHostExportSession");

  static final List<String> PAGE_FUNCTIONS =
      List.of(
          "vbtmPageUpdate",
          "vbtmPageWindow",
          "vbtmPageWindowError",
          "vbtmPageSearch",
          "vbtmPageMatch",
          "vbtmPageZoomTo",
          "vbtmPageSearchFor");

  interface Page {

    void define(String name, Consumer<Object[]> body);

    void load(String html);

    void execute(String js);

    boolean focus();

    boolean isDisposed();

    void dispose();
  }

  interface Host {

    void ready();

    void requestWindow(long reqId, long t0Ns, long t1Ns, int px);

    void reload();

    void select(@Nullable SelectedCall frame);

    void requestSearch(long reqId, String query);

    void requestMatch(long reqId, boolean forward, long posNs);

    void exportSession();
  }

  private final UiThread ui;

  private final Host host;

  private final AtomicLong generation = new AtomicLong();

  private @Nullable Page page;

  ViewerBridge(UiThread ui, Host host) {
    this.ui = ui;
    this.host = host;
  }

  void open(Page next, String html) {
    close();
    page = next;
    next.define("vbtmHostReady", args -> host.ready());
    next.define("vbtmHostRequestWindow", this::onRequestWindow);
    next.define("vbtmHostReload", args -> host.reload());
    next.define("vbtmHostSelect", this::onSelect);
    next.define("vbtmHostRequestSearch", this::onSearch);
    next.define("vbtmHostRequestMatch", this::onFindMatch);
    next.define("vbtmHostExportSession", args -> host.exportSession());
    next.load(html);
  }

  void close() {
    generation.incrementAndGet();
    Page p = page;
    page = null;
    if (p != null && !p.isDisposed()) {
      p.dispose();
    }
  }

  boolean isOpen() {
    return page != null && !page.isDisposed();
  }

  boolean focus() {
    return isOpen() && Objects.requireNonNull(page).focus();
  }

  long generation() {
    return generation.get();
  }

  void dropPendingReplies() {
    generation.incrementAndGet();
  }

  void postIfCurrent(long gen, Runnable r) {
    ui.post(() -> generation.get() == gen, r);
  }

  void postIfCurrent(long gen, Runnable r, Runnable stale) {
    ui.post(
        () -> {
          if (generation.get() == gen) {
            r.run();
          } else {
            stale.run();
          }
        });
  }

  void update(String metaJson, boolean live) {
    execute("vbtmPageUpdate(" + metaJson + "," + live + ")");
  }

  void windowReply(String json) {
    execute("vbtmPageWindow(" + json + ")");
  }

  void windowError(long reqId, String message) {
    execute("vbtmPageWindowError(" + reqId + "," + Json.quote(message) + ")");
  }

  void searchReply(String json) {
    execute("vbtmPageSearch(" + json + ")");
  }

  void matchReply(String json) {
    execute("vbtmPageMatch(" + json + ")");
  }

  void zoomTo(SelectedCall f) {
    execute(
        "vbtmPageZoomTo(" + f.tid() + "," + f.startNs() + "," + f.durNs() + "," + f.depth() + ")");
  }

  void searchFor(int methodId, String name) {
    execute("vbtmPageSearchFor(" + methodId + "," + Json.quote(name) + ")");
  }

  private void execute(String js) {
    if (isOpen()) {
      Objects.requireNonNull(page).execute(js);
    }
  }

  private void onRequestWindow(Object[] args) {
    if (args.length < 4) {
      return;
    }
    host.requestWindow(
        num(args[0]).longValue(),
        num(args[1]).longValue(),
        num(args[2]).longValue(),
        (int) num(args[3]).doubleValue());
  }

  private void onSelect(Object[] args) {
    if (args.length < 9) {
      host.select(null);
      return;
    }
    int depth = (int) num(args[4]).doubleValue();
    host.select(
        new SelectedCall(
            num(args[0]).longValue(),
            num(args[1]).longValue(),
            num(args[2]).longValue(),
            num(args[3]).longValue(),
            depth,
            (int) num(args[5]).doubleValue(),
            (int) num(args[6]).doubleValue(),
            Boolean.TRUE.equals(args[7]),
            SelectedCall.parseAncestors(args[8], depth),
            null,
            null));
  }

  private void onSearch(Object[] args) {
    if (args.length < 2) {
      return;
    }
    host.requestSearch(num(args[0]).longValue(), String.valueOf(args[1]));
  }

  private void onFindMatch(Object[] args) {
    if (args.length < 3) {
      return;
    }
    host.requestMatch(
        num(args[0]).longValue(), num(args[1]).doubleValue() >= 0, num(args[2]).longValue());
  }

  private static Number num(Object o) {
    return (Number) o;
  }
}
