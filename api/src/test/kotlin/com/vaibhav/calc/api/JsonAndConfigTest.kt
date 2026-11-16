package com.vaibhav.calc.api

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class JsonAndConfigTest : StringSpec({
    "JSON string escapes and supplementary Unicode round trip" {
        val value = "line\n\t\"\\\b\r\u000c \uD835\uDF0B"
        Json.parse(Json.encode(mapOf("text" to value))) shouldBe JsonValue.Obj(mapOf("text" to JsonValue.Str(value)))
    }
    "JSON numbers retain their text and require the JSON grammar" {
        Json.parse("-9007199254740993.125e+7") shouldBe JsonValue.Number("-9007199254740993.125e+7")
        for (bad in listOf("01", "+1", ".1", "1.", "1e", "--1", "NaN", "Infinity", "true false")) {
            shouldThrow<JsonInputException> { Json.parse(bad) }
        }
    }
    "JSON duplicate names trailing commas and deep nesting are rejected" {
        for (bad in listOf("{\"x\":1,\"x\":2}", "[1,]", "{\"x\":1,}", "[".repeat(34) + "0" + "]".repeat(34))) {
            shouldThrow<JsonInputException> { Json.parse(bad) }
        }
    }
    "JSON will not silently convert a floating-point output value" {
        shouldThrow<IllegalStateException> { Json.encode(mapOf("value" to 0.1)) }
    }
    "server binds loopback unless explicitly configured" {
        ServerConfig.from(emptyMap()) shouldBe ServerConfig("127.0.0.1", 8080)
        ServerConfig.from(mapOf("CALC_HOST" to "::1", "CALC_PORT" to "9000")) shouldBe ServerConfig("::1", 9000)
    }
    "invalid host and port configuration fails rather than exposing a fallback listener" {
        for (bad in listOf("", "0", "65536", "not-a-port")) {
            shouldThrow<IllegalArgumentException> { ServerConfig.from(mapOf("CALC_PORT" to bad)) }
        }
        for (bad in listOf("", "https://example.com", "localhost/path", "host name")) {
            shouldThrow<IllegalArgumentException> { ServerConfig.from(mapOf("CALC_HOST" to bad)) }
        }
    }
})
