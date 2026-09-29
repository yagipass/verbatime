package io.github.yagipass.verbatime.agent.probe;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;

import io.github.yagipass.verbatime.format.Vbtm;

final class GcPauses {

    private static final Object LOCK = new Object();

    private static final List<NotificationEmitter> subscribed = new ArrayList<>();

    private static NotificationListener listener;

    private static volatile TraceFileWriter sink;

    private GcPauses() {
    }

    static void attach(TraceFileWriter w) {
        synchronized (LOCK) {
            detachLocked();
            sink = w;
            NotificationListener l = (n, handback) -> onNotification(n);
            listener = l;
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (!(gc instanceof NotificationEmitter emitter) || !reportsPauses(gc.getName())) {
                    continue;
                }
                try {
                    emitter.addNotificationListener(l, null, null);
                    subscribed.add(emitter);
                } catch (RuntimeException e) {
                    Log.warn("cannot subscribe to GC notifications of " + gc.getName() + ": " + e);
                }
            }
            if (subscribed.isEmpty()) {
                Log.warn("no garbage collector reports pauses, so GC bands will be missing from this recording");
            }
        }
    }

    static void detach() {
        synchronized (LOCK) {
            detachLocked();
        }
    }

    private static void detachLocked() {
        sink = null;
        for (NotificationEmitter e : subscribed) {
            try {
                e.removeNotificationListener(listener);
            } catch (ListenerNotFoundException | RuntimeException ex) {
                Log.warn("cannot unsubscribe from GC notifications: " + ex);
            }
        }
        subscribed.clear();
        listener = null;
    }

    static boolean reportsPauses(String beanName) {
        return !beanName.contains("Cycles");
    }

    static int actionCode(String gcAction) {
        if (gcAction == null) {
            return Vbtm.GC_ACTION_UNKNOWN;
        }
        if (gcAction.contains("minor")) {
            return Vbtm.GC_ACTION_MINOR;
        }
        if (gcAction.contains("major")) {
            return Vbtm.GC_ACTION_MAJOR;
        }
        return Vbtm.GC_ACTION_UNKNOWN;
    }

    private static void onNotification(Notification n) {
        if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(n.getType())) {
            return;
        }
        TraceFileWriter w = sink;
        if (w == null) {
            return;
        }
        try {
            GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) n.getUserData());
            GcInfo gc = info.getGcInfo();
            w.writeGc(gc.getStartTime(), gc.getDuration(), actionCode(info.getGcAction()), nonNull(info.getGcName()), nonNull(info.getGcCause()));
        } catch (RuntimeException e) {
            Log.warn("dropping a malformed GC notification: " + e);
        }
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }
}
