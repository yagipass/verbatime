package io.github.yagipass.verbatime.examples.ktor

import io.github.yagipass.verbatime.examples.workload.OrderService
import io.github.yagipass.verbatime.examples.workload.OutOfStockException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val orders = OrderService()

fun main(args: Array<String>) {
    val port = 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    monitor.subscribe(ApplicationStarted) { warmUp() }
    routing {
        get("/healthz") {
            call.respondText("ok\n")
        }
        get("/orders") {
            val sku = call.request.queryParameters["sku"] ?: "widget"
            val qty = call.request.queryParameters["qty"]?.toIntOrNull()
            if (qty == null) {
                call.respondText("qty must be an integer\n", status = HttpStatusCode.BadRequest)
                return@get
            }
            try {
                val receipt = withContext(Dispatchers.IO) { orders.placeOrder(sku, qty) }
                call.respondText(receipt.toText() + "\n")
            } catch (e: OutOfStockException) {
                call.respondText((e.message ?: "out of stock") + "\n", status = HttpStatusCode.Conflict)
            }
        }
    }
}

private fun warmUp() {
    orders.placeOrder("warmup", 1)
}
