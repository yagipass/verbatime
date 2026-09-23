package io.github.yagipass.verbatime.examples.grpc;

import io.github.yagipass.verbatime.examples.grpc.proto.OrderRequest;
import io.github.yagipass.verbatime.examples.grpc.proto.OrdersGrpc;
import io.github.yagipass.verbatime.examples.grpc.proto.Receipt;
import io.github.yagipass.verbatime.examples.workload.OrderService;
import io.github.yagipass.verbatime.examples.workload.OutOfStockException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

public final class OrdersService extends OrdersGrpc.OrdersImplBase {

    private final OrderService orders;

    public OrdersService(final OrderService orders) {
        this.orders = orders;
    }

    @Override
    public void placeOrder(final OrderRequest request, final StreamObserver<Receipt> response) {
        final String sku = request.getSku().isEmpty() ? "widget" : request.getSku();
        final int qty = request.getQty() == 0 ? 1 : request.getQty();
        try {
            final io.github.yagipass.verbatime.examples.workload.Receipt receipt = orders.placeOrder(sku, qty);
            response.onNext(Receipt.newBuilder().setSku(receipt.sku()).setQty(receipt.qty()).setCents(receipt.cents()).setTxId(receipt.txId()).build());
            response.onCompleted();
        } catch (final OutOfStockException e) {
            response.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
