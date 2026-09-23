import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.yagipass.verbatime.format.CorruptTraceException;
import io.github.yagipass.verbatime.format.EventCursor;
import io.github.yagipass.verbatime.format.TraceReader;

final class TraceStats {

    private TraceStats() {
    }

    static long[] eventsPerSession(final byte[] recording) {
        final List<Long> finished = new ArrayList<>();
        final Map<Long, Long> open = new HashMap<>();
        final EventCursor cursor = new EventCursor();
        final TraceReader.Outcome outcome;
        try {
            outcome = TraceReader.read(recording, new TraceReader.Visitor() {
                @Override
                public void chunk(final long tid, final long baseTicks, final byte[] bytes, final int off,
                        final int len, final boolean sessionEnd, final boolean truncated) {
                    if (truncated) {
                        throw new IllegalStateException("recording has a cut chunk at " + off);
                    }
                    long events = open.getOrDefault(tid, 0L);
                    cursor.reset(bytes, off, len, baseTicks);
                    EventCursor.Event e = cursor.next();
                    while (e == EventCursor.Event.ENTER || e == EventCursor.Event.EXIT) {
                        events++;
                        e = cursor.next();
                    }
                    if (e != EventCursor.Event.END) {
                        throw new IllegalStateException("chunk payload is " + e + " at " + cursor.stopIndex());
                    }
                    if (sessionEnd) {
                        open.remove(tid);
                        finished.add(events);
                    } else {
                        open.put(tid, events);
                    }
                }
            });
        } catch (final CorruptTraceException e) {
            throw new IllegalStateException(e.getMessage() + " at " + e.offset(), e);
        }
        if (outcome != TraceReader.Outcome.CLEAN) {
            throw new IllegalStateException("recording is " + outcome);
        }
        return finished.stream().mapToLong(Long::longValue).toArray();
    }
}
