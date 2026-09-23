package io.github.yagipass.verbatime.examples.workload;

import java.util.concurrent.atomic.AtomicLong;

public final class PaymentGateway {

    private final AtomicLong seq = new AtomicLong();

    public String charge(final long cents) {
        Work.io(8);
        final long id = seq.incrementAndGet();
        return "tx-" + id + "-" + Long.toHexString(Work.cpu("charge:" + cents, 1_000));
    }
}
