package io.github.yagipass.verbatime.agent.jmx;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.github.yagipass.verbatime.agent.Recording;
import io.github.yagipass.verbatime.agent.probe.Log;

final class RecordingStreams {

    private static final int READ_SIZE = 1 << 20;

    private static final long IDLE_TIMEOUT_NANOS = 60_000_000_000L;

    private static final class Stream {
        private final long id;

        private final Recording recording;

        private final FileChannel channel;

        private long offset;

        private long lastAccessNanos = System.nanoTime();

        private Stream(long id, Recording recording, FileChannel channel, long offset) {
            this.id = id;
            this.recording = recording;
            this.channel = channel;
            this.offset = offset;
        }
    }

    private final Consumer<Recording> onRetired;

    private final Map<Long, Stream> streams = new HashMap<>();

    private long nextId = 1;

    RecordingStreams(Consumer<Recording> onRetired) {
        this.onRetired = onRetired;
    }

    long open(Recording r, long fromOffset) {
        retireAll(RecordingStreams::isIdle, true);
        long committed = r.writer().committedBytes();
        if (fromOffset < 0 || fromOffset > committed) {
            throw new IllegalArgumentException("offset " + fromOffset + " is out of range, the committed size is " + committed);
        }
        FileChannel ch;
        try {
            ch = FileChannel.open(r.writer().path(), StandardOpenOption.READ);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot open " + r.writer().path(), e);
        }
        synchronized (this) {
            Stream s = new Stream(nextId++, r, ch, fromOffset);
            streams.put(s.id, s);
            return s.id;
        }
    }

    byte[] read(long streamId) {
        retireAll(RecordingStreams::isIdle, true);
        Stream s;
        synchronized (this) {
            s = streams.get(streamId);
            if (s == null) {
                throw new IllegalArgumentException("unknown stream id " + streamId);
            }
            s.lastAccessNanos = System.nanoTime();
        }
        byte[] out;
        try {
            synchronized (s) {
                out = readLocked(s);
            }
        } catch (IOException e) {
            retire(s, false);
            throw new UncheckedIOException("cannot read " + s.recording.writer().path(), e);
        }
        if (out == null) {
            retire(s, true);
        }
        return out;
    }

    private static byte[] readLocked(Stream s) throws IOException {
        boolean closed = s.recording.closed();
        long avail = s.recording.writer().committedBytes() - s.offset;
        if (avail <= 0) {
            return closed ? null : new byte[0];
        }
        byte[] out = new byte[(int) Math.min(avail, READ_SIZE)];
        ByteBuffer buf = ByteBuffer.wrap(out);
        while (buf.hasRemaining()) {
            if (s.channel.read(buf, s.offset + buf.position()) < 0) {
                break;
            }
        }
        int read = buf.position();
        s.offset += read;
        return read == out.length ? out : Arrays.copyOf(out, read);
    }

    void close(long streamId) {
        retireAll(s -> s.id == streamId, true);
    }

    void closeAllOf(Recording r) {
        retireAll(s -> s.recording == r, false);
    }

    void closeAll() {
        retireAll(s -> true, true);
    }

    synchronized boolean hasStreamsOf(Recording r) {
        for (Stream s : streams.values()) {
            if (s.recording == r) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIdle(Stream s) {
        return System.nanoTime() - s.lastAccessNanos > IDLE_TIMEOUT_NANOS;
    }

    private void retire(Stream s, boolean delivered) {
        synchronized (this) {
            if (streams.get(s.id) != s) {
                return;
            }
            streams.remove(s.id);
            closeChannel(s);
            if (delivered) {
                s.recording.markDelivered();
            }
        }
        onRetired.accept(s.recording);
    }

    private void retireAll(Predicate<Stream> which, boolean notify) {
        List<Recording> retired = new ArrayList<>();
        synchronized (this) {
            for (Iterator<Stream> it = streams.values().iterator(); it.hasNext();) {
                Stream s = it.next();
                if (which.test(s)) {
                    it.remove();
                    closeChannel(s);
                    retired.add(s.recording);
                }
            }
        }
        if (notify) {
            for (Recording r : retired) {
                onRetired.accept(r);
            }
        }
    }

    private static void closeChannel(Stream s) {
        try {
            s.channel.close();
        } catch (IOException e) {
            Log.warn("closing stream #" + s.id + ": " + e);
        }
    }
}
