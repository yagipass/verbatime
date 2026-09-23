package io.github.yagipass.verbatime.jmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

final class ViewerBridgeTest {

    static final class FakePage implements ViewerBridge.Page {

        final Map<String, Consumer<Object[]>> defined = new LinkedHashMap<>();

        final List<String> events = new ArrayList<>();

        boolean disposed;

        @Override
        public void define(final String name, final Consumer<Object[]> body) {
            defined.put(name, body);
            events.add("define " + name);
        }

        @Override
        public void load(final String html) {
            events.add("load " + html);
        }

        @Override
        public void execute(final String js) {
            events.add(js);
        }

        @Override
        public boolean focus() {
            return true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }

        @Override
        public void dispose() {
            disposed = true;
            events.add("dispose");
        }

        void call(final String fn, final Object... args) {
            defined.get(fn).accept(args);
        }
    }

    static final class RecordingHost implements ViewerBridge.Host {

        final List<String> calls = new ArrayList<>();

        SelectedCall selected;

        int selections;

        @Override
        public void ready() {
            calls.add("ready");
        }

        @Override
        public void requestWindow(final long reqId, final long t0Ns, final long t1Ns, final int px) {
            calls.add("window " + reqId + " " + t0Ns + " " + t1Ns + " " + px);
        }

        @Override
        public void reload() {
            calls.add("reload");
        }

        @Override
        public void select(final SelectedCall frame) {
            selections++;
            selected = frame;
        }

        @Override
        public void requestSearch(final long reqId, final String query) {
            calls.add("search " + reqId + " " + query);
        }

        @Override
        public void requestMatch(final long reqId, final boolean forward, final long posNs) {
            calls.add("match " + reqId + " " + forward + " " + posNs);
        }

        @Override
        public void exportSession() {
            calls.add("export");
        }
    }

    final FakeUiThread ui = new FakeUiThread();

    final RecordingHost host = new RecordingHost();

    final ViewerBridge bridge = new ViewerBridge(ui, host);

    @Test
    void openDefinesEveryHostFunctionBeforeThePageLoads() {
        final FakePage page = new FakePage();
        bridge.open(page, "<html>");
        final List<String> expected = new ArrayList<>();
        for (final String fn : ViewerBridge.HOST_FUNCTIONS) {
            expected.add("define " + fn);
        }
        expected.add("load <html>");
        assertEquals(expected, page.events, "the page calls vbtmHostReady while loading, so it must already exist");
        assertTrue(bridge.isOpen());
    }

    @Test
    void outboundCallsAreTheExactScriptThePageExpects() {
        final FakePage page = new FakePage();
        bridge.open(page, "");
        page.events.clear();
        bridge.update("{\"a\":1}", true);
        bridge.windowReply("{\"reqId\":2}");
        bridge.windowError(3, "boom");
        bridge.searchReply("{\"reqId\":4}");
        bridge.matchReply("{\"reqId\":5}");
        bridge.zoomTo(new SelectedCall(1, 2, 3, 0, 4, 0, -1, false, List.of(), null, null));
        bridge.searchFor(3, "a<b");
        assertEquals(List.of("vbtmPageUpdate({\"a\":1},true)", "vbtmPageWindow({\"reqId\":2})",
                "vbtmPageWindowError(3,\"boom\")", "vbtmPageSearch({\"reqId\":4})", "vbtmPageMatch({\"reqId\":5})",
                "vbtmPageZoomTo(1,2,3,4)", "vbtmPageSearchFor(3,\"a\\u003cb\")"), page.events);
    }

    @Test
    void aReplyForAStaleGenerationIsDroppedAndTheStaleBranchRuns() {
        final FakePage page = new FakePage();
        bridge.open(page, "");
        final long gen = bridge.generation();
        final List<String> ran = new ArrayList<>();
        bridge.postIfCurrent(gen, () -> ran.add("fresh"), () -> ran.add("stale"));
        ui.runPosted();
        bridge.postIfCurrent(gen, () -> ran.add("fresh-2"), () -> ran.add("stale-2"));
        bridge.postIfCurrent(gen, () -> ran.add("fresh-3"));
        bridge.dropPendingReplies();
        ui.runPosted();
        assertEquals(List.of("fresh", "stale-2"), ran, "the generation is checked when the reply runs, not when it"
                + " is posted: a reply already queued before a name-table reset is dropped, and the two-arg form"
                + " drops it silently");
    }

    @Test
    void closeBumpsTheGenerationDisposesThePageAndSilencesLaterCalls() {
        final FakePage page = new FakePage();
        bridge.open(page, "");
        final long before = bridge.generation();
        bridge.close();
        assertNotEquals(before, bridge.generation());
        assertTrue(page.disposed);
        assertFalse(bridge.isOpen());
        page.events.clear();
        bridge.windowReply("{}");
        assertTrue(page.events.isEmpty(), "nothing is executed on a page that is gone");

        final FakePage second = new FakePage();
        bridge.open(second, "again");
        assertEquals(ViewerBridge.HOST_FUNCTIONS.size(), second.defined.size(), "a reopened page gets its own functions");
        assertFalse(page.defined.isEmpty(), "the old page is never touched again");
    }

    @Test
    void inboundArgumentsAreNarrowedFromTheDoublesTheBrowserSends() {
        final FakePage page = new FakePage();
        bridge.open(page, "");
        page.call("vbtmHostReady");
        page.call("vbtmHostRequestWindow", 1.0, 2.0, 3.0, 1180.0);
        page.call("vbtmHostRequestWindow", 1.0, 2.0);
        page.call("vbtmHostRequestSearch", 7.0, "Foo");
        page.call("vbtmHostRequestMatch", 8.0, -1.0, 9.0);
        page.call("vbtmHostRequestMatch", 8.0, 1.0, 9.0);
        page.call("vbtmHostReload");
        page.call("vbtmHostExportSession");
        assertEquals(List.of("ready", "window 1 2 3 1180", "search 7 Foo", "match 8 false 9", "match 8 true 9", "reload",
                "export"), host.calls, "a request with too few arguments is ignored, not mis-parsed");
    }

    @Test
    void selectBuildsTheFrameFromNineArgumentsAndClearsOnFewer() {
        final FakePage page = new FakePage();
        bridge.open(page, "");
        page.call("vbtmHostSelect", 1.0, 100.0, 50.0, 20.0, 2.0, 7.0, -1.0, true,
                new Object[] { 0.0, 200.0, 3.0, 90.0, 60.0, 5.0 });
        final SelectedCall f = host.selected;
        assertEquals(1, f.tid());
        assertEquals(100, f.startNs());
        assertEquals(50, f.durNs());
        assertEquals(20, f.selfNs());
        assertEquals(2, f.depth());
        assertEquals(7, f.methodId());
        assertEquals(-1, f.exceptionId());
        assertTrue(f.unclosed());
        assertEquals(2, f.ancestors().size());
        assertEquals(0, f.ancestors().get(0).depth());
        assertEquals(1, f.ancestors().get(1).depth());

        page.call("vbtmHostSelect");
        assertNull(host.selected, "no arguments means the selection was cleared");
        page.call("vbtmHostSelect", 1.0, 2.0, 3.0, 4.0, 5.0);
        assertNull(host.selected, "a short argument list is a clear, never a half-built frame");
        assertEquals(3, host.selections);
    }
}
