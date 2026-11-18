package com.vaibhav.calc.api

import com.vaibhav.calc.core.Calculator
import com.vaibhav.calc.core.error.CalcResult
import com.vaibhav.calc.core.num.ArithFault
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Host changes are explicit. No external service, key, or network is needed to calculate. */
data class ServerConfig(val host: String = "127.0.0.1", val port: Int = 8080) {
    companion object {
        fun from(environment: Map<String, String> = System.getenv()): ServerConfig {
            val host = environment["CALC_HOST"] ?: "127.0.0.1"
            require(host.isNotBlank() && host.length <= 253 && host.none { it.isWhitespace() || it in "/\\?#@" }) {
                "CALC_HOST must be a hostname or IP address"
            }
            val port = environment["CALC_PORT"]?.let {
                it.toIntOrNull()?.takeIf { p -> p in 1..65535 }
                    ?: throw IllegalArgumentException("CALC_PORT must be an integer in 1..65535")
            } ?: 8080
            return ServerConfig(host, port)
        }
    }
}

fun main() {
    val config = ServerConfig.from()
    embeddedServer(CIO, port = config.port, host = config.host) { calcModule() }.start(wait = true)
}

fun Application.calcModule() {
    val permits = Semaphore(ApiLimits.CONCURRENT_CALCULATIONS)
    routing {
        get("/health") { call.respondText("ok") }
        get("/") { call.respondJson(ApiService.capabilities()) }
        get("/v1/capabilities") { call.respondJson(ApiService.capabilities()) }
        get("/v1/functions") { call.respondJson(ApiService.functions()) }
        get("/v1/constants") { call.respondJson(ApiService.constants()) }
        get("/v1/units") { call.respondJson(ApiService.units()) }
        get("/v1/tool-catalog") { call.respondJson(ApiTools.catalog()) }
        get("/eval") {
            call.handleApi(permits) {
                val input = call.request.queryParameters["expr"] ?: badRequest("missing query parameter 'expr'")
                checkedExpression(input, legacy = true)
                when (val result = Calculator().evaluateAnswer(input)) {
                    is CalcResult.Ok -> ApiResponse(mapOf("ok" to true, "expr" to input,
                        "exact" to result.value.exact, "decimal" to result.value.decimal))
                    is CalcResult.Err -> mathError(result.error, legacy = true)
                }
            }
        }
        for (operation in ApiService.operations) {
            post("/v1/$operation") {
                call.handleApi(permits) { ApiService(call.readJsonBody()).execute(operation) }
            }
        }
    }
}

private suspend fun ApplicationCall.handleApi(permits: Semaphore, block: suspend () -> ApiResponse) {
    try {
        if (!permits.tryAcquire()) {
            response.headers.append(HttpHeaders.RetryAfter, "1")
            throw ApiException(HttpStatusCode.TooManyRequests, "BUSY", "two calculations are already running; retry shortly")
        }
        val result = try { withContext(Dispatchers.Default) { block() } } finally { permits.release() }
        respondJson(result.body, result.status)
    } catch (e: ApiException) {
        respondJson(e.json(), e.status)
    } catch (e: JsonInputException) {
        respondJson(ApiException(HttpStatusCode.BadRequest, "INVALID_JSON", e.message ?: "invalid JSON").json(), HttpStatusCode.BadRequest)
    } catch (e: TimeoutCancellationException) {
        respondJson(ApiException(HttpStatusCode.RequestTimeout, "REQUEST_TIMEOUT", "request body was not received within five seconds").json(), HttpStatusCode.RequestTimeout)
    } catch (e: CancellationException) {
        throw e
    } catch (e: ArithFault) {
        val kind = when (e) {
            is ArithFault.DivideByZero -> "DIVIDE_BY_ZERO"
            is ArithFault.Domain -> "DOMAIN"
            is ArithFault.Overflow -> "OVERFLOW"
            is ArithFault.Underflow -> "UNDERFLOW"
            is ArithFault.Unsupported -> "UNSUPPORTED"
        }
        respondJson(ApiException(HttpStatusCode.UnprocessableEntity, kind, e.message ?: "calculation failed").json(), HttpStatusCode.UnprocessableEntity)
    } catch (e: Exception) {
        // Do not log formulas, request bodies, or register values. Never turn a failure into an empty result.
        application.log.error("Calculator API operation failed (${e::class.java.simpleName})")
        respondJson(ApiException(HttpStatusCode.InternalServerError, "INTERNAL_ERROR", "calculation failed unexpectedly", recoverable = false).json(), HttpStatusCode.InternalServerError)
    }
}

private suspend fun ApplicationCall.respondJson(body: Map<String, Any?>, status: HttpStatusCode = HttpStatusCode.OK) {
    response.headers.append(HttpHeaders.CacheControl, "no-store")
    response.headers.append("X-Content-Type-Options", "nosniff")
    respondText(Json.encode(body), ContentType.Application.Json, status)
}

private suspend fun ApplicationCall.readJsonBody(): ApiRequest {
    val contentType = request.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()?.lowercase()
    if (contentType != "application/json") throw ApiException(HttpStatusCode.UnsupportedMediaType, "CONTENT_TYPE", "Content-Type must be application/json")
    val encoding = request.headers[HttpHeaders.ContentEncoding]
    if (encoding != null && !encoding.equals("identity", ignoreCase = true)) {
        throw ApiException(HttpStatusCode.UnsupportedMediaType, "CONTENT_ENCODING", "compressed request bodies are not supported")
    }
    request.headers[HttpHeaders.ContentLength]?.let {
        val size = it.toLongOrNull() ?: badRequest("invalid Content-Length")
        if (size < 0) badRequest("invalid Content-Length")
        if (size > ApiLimits.BODY_BYTES) bodyTooLarge()
    }
    val bytes = withTimeout(5_000) {
        val input = receiveChannel()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4_096)
        while (true) {
            val read = input.readAvailable(buffer)
            if (read < 0) break
            if (output.size() + read > ApiLimits.BODY_BYTES) bodyTooLarge()
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }
    val text = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        badRequest("request body must be valid UTF-8")
    }
    return ApiRequest(Json.parse(text))
}

private fun bodyTooLarge(): Nothing = throw ApiException(HttpStatusCode.PayloadTooLarge, "BODY_TOO_LARGE", "request body exceeds ${ApiLimits.BODY_BYTES} bytes")
