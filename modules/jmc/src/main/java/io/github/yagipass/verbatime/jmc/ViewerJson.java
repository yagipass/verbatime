package io.github.yagipass.verbatime.jmc;

import java.util.BitSet;

import io.github.yagipass.verbatime.jmc.index.Calls;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.Session;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot.ThreadIndex;
import io.github.yagipass.verbatime.jmc.query.MatchSearch;
import io.github.yagipass.verbatime.jmc.query.WindowExtractor.Window;

public final class ViewerJson {

    static final int COVERAGE_BUCKETS = 1600;

    private ViewerJson() {
    }

    public static final class SentSessions {

        int sent;

        final BitSet open = new BitSet();

        long generation = -1;

        private boolean lastReset;

        private int gcSent;

        public SentSessions copy() {
            final SentSessions c = new SentSessions();
            c.sent = sent;
            c.open.or(open);
            c.generation = generation;
            c.lastReset = lastReset;
            c.gcSent = gcSent;
            return c;
        }

        public boolean lastReset() {
            return lastReset;
        }
    }

    public static final class SentNames {

        final BitSet methods = new BitSet();

        final BitSet exceptions = new BitSet();

        public SentNames copy() {
            final SentNames c = new SentNames();
            c.methods.or(methods);
            c.exceptions.or(exceptions);
            return c;
        }

        public void or(final SentNames o) {
            methods.or(o.methods);
            exceptions.or(o.exceptions);
        }

        private void clear() {
            methods.clear();
            exceptions.clear();
        }
    }

    public static String metaJson(final TraceSnapshot d, final SentNames sent, final SentSessions cursor) {
        final StringBuilder sb = new StringBuilder(1 << 16);

        sb.append("{\"meta\":{\"source\":");
        Json.appendQuoted(sb, d.path.getFileName().toString());
        sb.append(",\"startEpochMs\":").append(d.startEpochMs);
        sb.append(",\"utcOffsetSeconds\":").append(d.utcOffsetSeconds);
        sb.append(",\"minNs\":").append(d.minNs);
        sb.append(",\"maxNs\":").append(d.maxNs);
        sb.append(",\"totalCalls\":").append(d.totalCalls);
        sb.append(",\"totalMethods\":").append(d.totalMethods);
        sb.append(",\"truncated\":").append(d.truncated);
        sb.append(",\"corrupt\":").append(d.corruptOffset >= 0);
        if (d.corruptOffset >= 0) {
            sb.append(",\"corruptOffset\":").append(d.corruptOffset);
            sb.append(",\"corruptReason\":");
            Json.appendQuoted(sb, d.corruptReason);
        }
        final int size = d.sessions.size();
        final boolean reset = cursor.generation != d.generation || size < cursor.sent;
        sb.append(",\"sessionsReset\":").append(reset);
        sb.append(",\"sessions\":[");
        final BitSet used = new BitSet();
        boolean firstItem = true;
        for (int i = 0; i < size; i++) {
            if (!reset && i < cursor.sent && !cursor.open.get(i)) {
                continue;
            }
            final Session s = d.sessions.get(i);
            if (s.rootMethodId >= 0) {
                used.set(s.rootMethodId);
            }
            if (!firstItem) {
                sb.append(',');
            }
            firstItem = false;
            sb.append("{\"seq\":").append(s.seq);
            sb.append(",\"tid\":").append(s.tid);
            sb.append(",\"rootId\":").append(s.rootMethodId);
            sb.append(",\"startNs\":").append(s.startNs);
            sb.append(",\"durNs\":").append(s.durNs());
            sb.append(",\"unclosed\":").append(!s.ended);
            sb.append('}');
        }
        cursor.sent = size;
        cursor.open.clear();
        for (int i = 0; i < size; i++) {
            if (!d.sessions.get(i).ended) {
                cursor.open.set(i);
            }
        }
        cursor.generation = d.generation;
        cursor.lastReset = reset;
        sb.append("],\"threads\":[");
        firstItem = true;
        final double bucketNs = Math.max(d.maxNs - d.minNs, 1) / (double) COVERAGE_BUCKETS;
        for (final ThreadIndex m : d.threads) {
            if (!firstItem) {
                sb.append(',');
            }
            firstItem = false;
            sb.append("{\"tid\":").append(m.tid);
            sb.append(",\"name\":");
            Json.appendQuoted(sb, d.threadName(m.tid));
            sb.append(",\"maxDepth\":").append(m.maxDepth);
            sb.append(",\"totalCalls\":").append(m.totalCalls);
            sb.append(",\"coverage\":[");
            appendCoverage(sb, m, d.minNs, bucketNs);
            sb.append("]}");
        }
        sb.append("],\"gc\":{");
        final boolean gcReset = reset || d.gc.count < cursor.gcSent;
        final int gcFrom = gcReset ? 0 : cursor.gcSent;
        sb.append("\"reset\":").append(gcReset);
        sb.append(",\"totalNs\":").append(d.gc.totalNs);
        sb.append(",\"items\":[");
        for (int i = gcFrom; i < d.gc.count; i++) {
            if (i > gcFrom) {
                sb.append(',');
            }
            sb.append('[').append(d.gc.startNs[i]).append(',').append(d.gc.durNs[i]).append(',').append(d.gc.action[i])
                    .append(',');
            Json.appendQuoted(sb, d.gc.collector[i]);
            sb.append(',');
            Json.appendQuoted(sb, d.gc.cause[i]);
            sb.append(']');
        }
        sb.append("]}");
        cursor.gcSent = d.gc.count;
        sb.append("},\"methodNames\":[");
        if (reset) {
            sent.clear();
        }
        appendNewNames(sb, d, used, sent.methods);
        sb.append("],\"exceptionNames\":[]}");
        return sb.toString();
    }

    public static String windowJson(final TraceSnapshot d, final Window r, final long reqId, final SentNames sent) {
        final StringBuilder sb = new StringBuilder(1 << 16);
        sb.append("{\"reqId\":").append(reqId);
        sb.append(",\"threads\":[");
        final BitSet used = new BitSet();
        final BitSet usedExc = new BitSet();
        boolean firstThread = true;
        for (final Calls tf : r.callsByThread) {
            if (!firstThread) {
                sb.append(',');
            }
            firstThread = false;
            sb.append("{\"tid\":").append(tf.tid);
            sb.append(",\"count\":").append(tf.count);
            sb.append(",\"startDeltas\":[");
            long prev = 0;
            for (int i = 0; i < tf.count; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(tf.startNs[i] - prev);
                prev = tf.startNs[i];
            }
            sb.append("],\"durNs\":[");
            appendLongs(sb, tf.durNs, tf.count);
            sb.append("],\"methodIds\":[");
            for (int i = 0; i < tf.count; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(tf.methodId[i]);
                used.set(tf.methodId[i]);
            }
            sb.append("],\"depths\":[");
            appendInts(sb, tf.depth, tf.count);
            sb.append("],\"selfNs\":[");
            appendLongs(sb, tf.selfNs, tf.count);
            sb.append("],\"unclosed\":[");
            boolean firstU = true;
            for (int i = 0; i < tf.count; i++) {
                if (tf.unclosed[i]) {
                    if (!firstU) {
                        sb.append(',');
                    }
                    firstU = false;
                    sb.append(i);
                }
            }
            sb.append("],\"exc\":[");
            boolean firstT = true;
            for (int i = 0; i < tf.count; i++) {
                final int exc = tf.exceptionId[i];
                if (exc >= 0) {
                    if (!firstT) {
                        sb.append(',');
                    }
                    firstT = false;
                    sb.append(i).append(',').append(exc);
                    if (exc > 0) {
                        usedExc.set(exc);
                    }
                }
            }
            sb.append("]}");
        }
        sb.append("],\"methodNames\":[");
        appendNewNames(sb, d, used, sent.methods);
        sb.append("],\"exceptionNames\":[");
        appendNewExceptionNames(sb, d, usedExc, sent.exceptions);
        sb.append("]}");
        return sb.toString();
    }

    public static String searchJson(final long reqId, final BitSet ids, final long calls) {
        final StringBuilder sb = new StringBuilder(1 << 12);
        sb.append("{\"reqId\":").append(reqId);
        sb.append(",\"methods\":").append(ids.cardinality());
        sb.append(",\"calls\":").append(calls);
        sb.append(",\"ids\":[");
        boolean first = true;
        for (int id = ids.nextSetBit(0); id >= 0; id = ids.nextSetBit(id + 1)) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(id);
        }
        sb.append("]}");
        return sb.toString();
    }

    public static String matchJson(final long reqId, final MatchSearch.Match m) {
        final StringBuilder sb = new StringBuilder(160);
        sb.append("{\"reqId\":").append(reqId);
        if (m == null) {
            sb.append(",\"match\":null}");
            return sb.toString();
        }
        sb.append(",\"match\":{\"tid\":").append(m.tid);
        sb.append(",\"startNs\":").append(m.startNs);
        sb.append(",\"durNs\":").append(m.durNs);
        sb.append(",\"depth\":").append(m.depth);
        sb.append(",\"methodId\":").append(m.methodId);
        sb.append("}}");
        return sb.toString();
    }

    private static void appendCoverage(final StringBuilder sb, final ThreadIndex m, final long minNs,
            final double bucketNs) {
        final int n = COVERAGE_BUCKETS;
        final double[] covered = new double[n];
        for (int i = 0; i < m.overview.count; i++) {
            if (m.overview.depth[i] != 0) {
                continue;
            }
            final double s = m.overview.startNs[i] - minNs;
            final double e = s + m.overview.durNs[i];
            final int b0 = (int) (s / bucketNs);
            final int b1 = Math.min((int) (e / bucketNs), n - 1);
            for (int b = b0; b <= b1; b++) {
                final double overlap = Math.min(e, (b + 1) * bucketNs) - Math.max(s, b * bucketNs);
                if (overlap > 0) {
                    covered[b] += overlap;
                }
            }
        }
        int runValue = -1;
        int runLength = 0;
        for (int i = 0; i < n; i++) {
            final int v = Math.min(Math.max((int) Math.round(covered[i] / bucketNs * 100), 0), 100);
            if (v == runValue) {
                runLength++;
                continue;
            }
            if (runLength > 0) {
                sb.append(runValue).append(',').append(runLength).append(',');
            }
            runValue = v;
            runLength = 1;
        }
        sb.append(runValue).append(',').append(runLength);
    }

    private static void appendNewNames(final StringBuilder sb, final TraceSnapshot d, final BitSet used,
            final BitSet sent) {
        boolean first = true;
        for (int id = used.nextSetBit(0); id >= 0; id = used.nextSetBit(id + 1)) {
            if (!sent.get(id)) {
                sent.set(id);
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('[').append(id).append(',');
                Json.appendQuoted(sb, d.methodName(id));
                sb.append(']');
            }
        }
    }

    private static void appendNewExceptionNames(final StringBuilder sb, final TraceSnapshot d, final BitSet used,
            final BitSet sent) {
        boolean first = true;
        for (int id = used.nextSetBit(1); id >= 0; id = used.nextSetBit(id + 1)) {
            if (sent.get(id)) {
                continue;
            }
            final String name = id < d.exceptionNames.length ? d.exceptionNames[id] : null;
            if (name == null) {
                continue;
            }
            sent.set(id);
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('[').append(id).append(',');
            Json.appendQuoted(sb, name);
            sb.append(']');
        }
    }

    private static void appendLongs(final StringBuilder sb, final long[] a, final int n) {
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(a[i]);
        }
    }

    private static void appendInts(final StringBuilder sb, final int[] a, final int n) {
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(a[i]);
        }
    }
}
