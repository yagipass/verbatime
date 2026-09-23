package io.github.yagipass.verbatime.jmc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.jmc.index.RandomTraces;
import io.github.yagipass.verbatime.jmc.index.TestTraces;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.query.WindowExtractor;

public final class ViewerPreview {

    private ViewerPreview() {
    }

    private static final String[][] CLASSES = {
            { "com.example.web.RequestDispatcher", "dispatch(Ljakarta/servlet/http/HttpServletRequest;)V",
                    "resolveHandler(Ljava/lang/String;)Ljava/lang/Object;" },
            { "com.example.app.OrderService", "processOrder(Ljava/lang/String;)Lcom/example/app/OrderResult;",
                    "validate(Ljava/lang/String;)Z", "applyDiscount(JI)J" },
            { "com.example.app.InventoryService", "reserve(J)Z", "release(J)V" },
            { "com.example.dao.OrderDao", "insert(Lcom/example/app/Order;)J", "findById(J)Lcom/example/app/Order;" },
            { "com.example.dao.JdbcTemplate", "query(Ljava/lang/String;)Ljava/util/List;", "update(Ljava/lang/String;)I",
                    "mapRow(Ljava/sql/ResultSet;)Ljava/lang/Object;" },
            { "com.example.cache.CacheClient", "get(Ljava/lang/String;)Ljava/lang/Object;",
                    "put(Ljava/lang/String;Ljava/lang/Object;)V" },
            { "com.example.util.JsonMapper", "serialize(Ljava/lang/Object;)Ljava/lang/String;",
                    "deserialize(Ljava/lang/String;)Ljava/lang/Object;" },
            { "com.example.batch.ReportJob", "run()V", "aggregate(Ljava/util/List;)V", "flush()V" }, };

    public static void main(final String[] args) throws Exception {
        final Path out = Path.of(args.length > 0 ? args[0] : "preview.html");
        final byte[] bytes = buildTrace();
        final TraceSnapshot data = TestTraces.index(bytes, 1 << 20);
        final ViewerJson.SentNames sent = new ViewerJson.SentNames();
        final String init = ViewerJson.metaJson(data, sent, new ViewerJson.SentSessions());

        final WindowExtractor.Window win = WindowExtractor.extract(data, data.minNs - 1, data.maxNs + 1, 2_000_000,
                60_000);
        final String winJson = ViewerJson.windowJson(data, win, 0, sent);

        final String html = ViewerHtml.render(ViewerHtml.template(), init, "window.__WIN = " + winJson + ";\n" + SHIM);
        Files.writeString(out, html, StandardCharsets.UTF_8);
        System.out.println("wrote " + out.toAbsolutePath() + ": " + data.totalCalls + " calls, " + data.threads.size()
                + " threads, " + data.sessions.size() + " sessions, D=" + data.overviewThresholdNs + "ns");
    }

    private static final String SHIM = """
            window.vbtmHostRequestWindow = function (id) {
              setTimeout(function () { __WIN.reqId = id; vbtmPageWindow(__WIN); }, 30);
            };
            window.vbtmHostRequestSearch = function (id, q) {
              setTimeout(function () {
                q = String(q).toLowerCase();
                const ids = [];
                NAMES.forEach(function (nm, mid) {
                  if (nm.toLowerCase().indexOf(q) >= 0) ids.push(mid);
                });
                const set = new Set(ids);
                let calls = 0;
                for (const th of threads) {
                  for (let i = 0; i < th.n; i++) if (set.has(th.nm[i])) calls++;
                }
                vbtmPageSearch({ reqId: id, methods: ids.length, calls: calls, ids: ids });
              }, 30);
            };
            window.vbtmHostRequestMatch = function (id, dir, pos) {
              setTimeout(function () {
                let best = null;
                for (const th of threads) {
                  for (let i = 0; i < th.n; i++) {
                    if (!emphasis || !emphasis.has(th.nm[i])) continue;
                    const s = th.ts[i];
                    if (dir > 0 ? s > pos : s < pos) {
                      if (!best || (dir > 0 ? s < best.startNs : s > best.startNs)) {
                        best = { tid: th.tid, startNs: s, durNs: th.dur[i], depth: th.dp[i], nm: th.nm[i] };
                      }
                    }
                  }
                }
                vbtmPageMatch({ reqId: id, match: best });
              }, 30);
            };
            window.vbtmHostSelect = function () { console.log("vbtmHostSelect", Array.from(arguments)); };
            window.vbtmHostExportSession = function () { console.log("vbtmHostExportSession"); };
            window.vbtmHostReload = function () { location.reload(); };
            """;

    private static final String[] EXCEPTIONS = { "java.sql.SQLTransientConnectionException",
            "com.example.app.OrderRejectedException", "java.lang.IllegalStateException" };

    private record Ev(long ticks, boolean enter, int methodId, int exc) {
    }

    private static int maybeThrow(final Random rng, final int oneIn) {
        return rng.nextInt(oneIn) == 0 ? 1 + rng.nextInt(EXCEPTIONS.length) : -1;
    }

    private static byte[] buildTrace() {
        final TraceBuilder w = TestTraces.writer();
        w.thread(11, "http-worker-1");
        w.thread(12, "http-worker-2");
        w.thread(21, "batch-scheduler");
        int base = 0;
        for (final String[] c : CLASSES) {
            final String[] sigs = new String[c.length - 1];
            System.arraycopy(c, 1, sigs, 0, sigs.length);
            w.clazz(base, c[0], sigs);
            base += 10;
        }
        for (int x = 0; x < EXCEPTIONS.length; x++) {
            w.exception(x + 1, EXCEPTIONS[x]);
        }
        final Random rng = new Random(42);
        emitSessions(w, rng, 11, 10_000, 4, true);
        emitSessions(w, rng, 12, 160_000, 3, true);
        emitSessions(w, rng, 21, 60_000, 1, false);
        w.gc(14_500, 3_200, Vbtm.GC_ACTION_MINOR, "G1 Young Generation", "G1 Evacuation Pause");
        w.gc(62_000, 12_000, Vbtm.GC_ACTION_MAJOR, "G1 Old Generation", "G1 Humongous Allocation");
        w.gc(165_000, 2_500, Vbtm.GC_ACTION_MINOR, "G1 Young Generation", "G1 Evacuation Pause");
        w.gc(200_000, 40_000, Vbtm.GC_ACTION_MAJOR, "G1 Old Generation", "System.gc()");
        return w.bytes();
    }

    private static void emitSessions(final TraceBuilder w, final Random rng, final long tid, final long startTicks,
            final int sessions, final boolean closeLast) {
        long t = startTicks;
        for (int s = 0; s < sessions; s++) {
            final List<Ev> evs = new ArrayList<>();
            final boolean batch = tid == 21;
            final int root = batch ? 70 : 0;
            final long[] tick = { t };
            evs.add(new Ev(tick[0], true, root, -1));
            final int requests = batch ? 4 : 8 + rng.nextInt(8);
            for (int r = 0; r < requests; r++) {
                tick[0] += 20 + rng.nextInt(200);
                subtree(evs, rng, tick, batch ? 71 : 10, 1, batch ? 8 : 10);
            }
            tick[0] += 30 + rng.nextInt(100);
            final boolean lastOpen = !closeLast && s == sessions - 1;
            if (!lastOpen) {
                evs.add(new Ev(tick[0], false, 0, maybeThrow(rng, 50)));
            }
            final List<long[]> events = new ArrayList<>(evs.size());
            for (final Ev e : evs) {
                events.add(new long[] { e.ticks, e.enter ? 1 : 0, e.enter ? e.methodId : e.exc });
            }
            RandomTraces.chunkEvents(events, () -> 200 + rng.nextInt(400),
                    (base, payload, last) -> w.chunk(tid, base, payload, last && !lastOpen));
            t = tick[0] + 5_000 + rng.nextInt(60_000);
        }
    }

    private static void subtree(final List<Ev> evs, final Random rng, final long[] tick, final int methodId, final int depth,
            final int maxDepth) {
        evs.add(new Ev(tick[0], true, methodId, -1));
        final int kids = depth >= maxDepth ? 0 : depth < 3 ? 2 + rng.nextInt(4) : rng.nextInt(4);
        if (kids == 0 || depth >= maxDepth) {
            tick[0] += 3 + rng.nextInt(depth < 4 ? 800 : 120);
        } else {
            for (int k = 0; k < kids; k++) {
                tick[0] += 1 + rng.nextInt(40);
                final int child = switch (rng.nextInt(8)) {
                    case 0 -> 11;
                    case 1 -> 12;
                    case 2 -> 20;
                    case 3 -> 30;
                    case 4 -> 40;
                    case 5 -> 41;
                    case 6 -> 50;
                    default -> 60;
                };
                subtree(evs, rng, tick, child + rng.nextInt(2), depth + 1, maxDepth);
            }
            tick[0] += 1 + rng.nextInt(30);
        }
        evs.add(new Ev(tick[0], false, 0, maybeThrow(rng, 120)));
    }
}
