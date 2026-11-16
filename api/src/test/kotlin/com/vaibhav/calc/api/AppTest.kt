package com.vaibhav.calc.api

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class AppTest : StringSpec({

    "GET /health returns ok" {
        testApplication {
            application { calcModule() }
            val r = client.get("/health")
            r.status shouldBe HttpStatusCode.OK
            r.bodyAsText() shouldBe "ok"
        }
    }

    "GET /eval computes the same exact result as core" {
        testApplication {
            application { calcModule() }
            val r = client.get("/eval?expr=0.1%2B0.2") // 0.1+0.2
            r.status shouldBe HttpStatusCode.OK
            val body = r.bodyAsText()
            // D-025: the exact form of 0.1+0.2 is 0.3, not 3/10. Both fields agree
            // here precisely because the value terminates — which is the ruling.
            body shouldContain "\"exact\":\"0.3\""
            body shouldContain "\"decimal\":\"0.3\""
        }
    }

    "GET /eval surfaces a typed error as 400" {
        testApplication {
            application { calcModule() }
            val r = client.get("/eval?expr=1%2F0") // 1/0
            r.status shouldBe HttpStatusCode.BadRequest
            r.bodyAsText() shouldContain "DIVIDE_BY_ZERO"
        }
    }
})
