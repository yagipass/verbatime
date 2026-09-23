package io.github.yagipass.verbatime.jmc.control;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

final class FakeAgent implements Agent {

    Map<String, String> steady = Map.of("v", "4", "pid", "7", "state", "idle", "roots", "0");

    final Deque<Map<String, String>> statuses = new ArrayDeque<>();

    IOException statusError;

    String[] searchResults = new String[0];

    IOException searchError;

    final List<String> searchQueries = new ArrayList<>();

    final List<String[]> replacedRoots = new ArrayList<>();

    long nextRecordingId = 1;

    int starts;

    IOException startError;

    int stops;

    IOException stopError;

    final Deque<byte[]> chunks = new ArrayDeque<>();

    IOException readError;

    Runnable onRead = () -> {
    };

    final List<Long> openedAt = new ArrayList<>();

    final List<Long> closedStreams = new ArrayList<>();

    long nextStreamId = 100;

    boolean closed;

    @Override
    public Map<String, String> status() throws IOException {
        if (statusError != null) {
            throw statusError;
        }
        return statuses.isEmpty() ? steady : statuses.poll();
    }

    @Override
    public String[] searchMethods(final String query, final int max) throws IOException {
        searchQueries.add(query);
        if (searchError != null) {
            throw searchError;
        }
        return searchResults;
    }

    @Override
    public void replaceRoots(final String[] specs) throws IOException {
        replacedRoots.add(specs);
    }

    @Override
    public long startRecording() throws IOException {
        starts++;
        if (startError != null) {
            throw startError;
        }
        return nextRecordingId++;
    }

    @Override
    public void stopRecording() throws IOException {
        stops++;
        if (stopError != null) {
            throw stopError;
        }
    }

    @Override
    public long openStream(final long recordingId, final long fromOffset) {
        openedAt.add(fromOffset);
        return nextStreamId++;
    }

    @Override
    public byte[] readStream(final long streamId) throws IOException {
        onRead.run();
        if (!chunks.isEmpty()) {
            return chunks.poll();
        }
        if (readError != null) {
            throw readError;
        }
        return null;
    }

    @Override
    public void closeStream(final long streamId) {
        closedStreams.add(streamId);
    }

    @Override
    public void close() {
        closed = true;
    }
}
