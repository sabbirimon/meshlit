package com.meshlit.core.observability

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OtlpLoopbackTest {
    @Test fun realHttpExporterSendsTraceAndMetricProtobufWithPrivateMetadata()=runTest {
        val requests=CopyOnWriteArrayList<Pair<String,ByteArray>>()
        val latch=CountDownLatch(2)
        val server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/") { ex->
            requests.add(ex.requestURI.path to ex.requestBody.readBytes());ex.responseHeaders.add("Content-Type","application/x-protobuf")
            ex.sendResponseHeaders(200,0);ex.responseBody.close();ex.close();latch.countDown()
        }
        server.start();val controller=TracingController()
        try {
            controller.reconfigure(TracingMode.Otel,"http://127.0.0.1:${server.address.port}")
            controller.record(AuditRecord(source=AuditSource.DEVICE,action="device.sample",measurements=mapOf("battery_percent" to 50.0)))
            controller.tracer("test").spanBuilder("inference.run").startSpan().apply{setAttribute("prompt","PRIVATE_TEST_PROMPT");recordException(Exception("PRIVATE_TEST_SECRET"));end()}
            assertTrue(controller.flush());assertTrue(latch.await(15,TimeUnit.SECONDS))
            assertTrue(requests.any{it.first=="/v1/traces" && it.second.isNotEmpty()})
            assertTrue(requests.any{it.first=="/v1/metrics" && it.second.isNotEmpty()})
            assertFalse(requests.any{it.second.toString(Charsets.ISO_8859_1).contains("PRIVATE_TEST")})
        } finally {controller.reconfigure(TracingMode.Off);server.stop(0)}
    }
}
