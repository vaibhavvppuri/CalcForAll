package com.vaibhav.calc.api

import io.kotest.core.spec.style.StringSpec
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.server.testing.testApplication

private suspend fun HttpClient.calculate(operation: String, body: String): HttpResponse = post("/v1/$operation") {
    contentType(ContentType.Application.Json)
    setBody(body)
}

private suspend fun HttpResponse.json(): JsonValue.Obj = Json.parse(bodyAsText()) as JsonValue.Obj
private fun JsonValue.field(name: String): JsonValue = (this as JsonValue.Obj).fields.getValue(name)
private fun JsonValue.text(): String = (this as JsonValue.Str).value
private fun JsonValue.items(): List<JsonValue> = (this as JsonValue.Arr).values

class ApiV1Test : StringSpec({
    "capabilities and registries are available without mutable state" {
        testApplication {
            application { calcModule() }
            for (path in listOf("/", "/v1/capabilities", "/v1/functions", "/v1/constants", "/v1/units")) {
                val response = client.get(path)
                response.status shouldBe HttpStatusCode.OK
                response.headers[HttpHeaders.CacheControl] shouldBe "no-store"
                response.headers["X-Content-Type-Options"] shouldBe "nosniff"
                response.json().field("ok") shouldBe JsonValue.Bool(true)
            }
            client.get("/v1/functions").bodyAsText() shouldContain "sin"
            client.get("/v1/constants").bodyAsText() shouldContain "uncertainty"
            client.get("/v1/units").bodyAsText() shouldContain "affine"
        }
    }

    "scalar evaluation retains exact rational and arbitrary integer values" {
        testApplication {
            application { calcModule() }
            val fraction = client.calculate("evaluate", """{"expr":"1/3"}""")
            fraction.status shouldBe HttpStatusCode.OK
            fraction.json().field("result").field("value").field("exact").text() shouldBe "1/3"
            val large = client.calculate("evaluate", """{"expr":"9007199254740993+1","settings":{"grouping":"NONE"}}""")
            large.json().field("result").field("value").field("exact").text() shouldBe "9007199254740994"
        }
    }

    "equations use the routing engine and ordered exact roots" {
        testApplication {
            application { calcModule() }
            val response = client.calculate("evaluate", """{"expr":"x^2-5x+6=0"}""")
            response.status shouldBe HttpStatusCode.OK
            val result = response.json().field("result")
            result.field("kind").text() shouldBe "roots"
            result.field("roots").items().map { it.field("exact").text() } shouldBe listOf("2", "3")
        }
    }

    "outcomes distinguish exact storage from approximate displayed results" {
        testApplication {
            application { calcModule() }
            client.calculate("evaluate", """{"expr":"1/3"}""")
                .json().field("result").field("approximate") shouldBe JsonValue.Bool(false)
            client.calculate("evaluate", """{"expr":"1/3","settings":{"exactForm":"DECIMAL"}}""")
                .json().field("result").field("approximate") shouldBe JsonValue.Bool(true)
            client.calculate("evaluate", """{"expr":"sin(1)","settings":{"angle":"RAD"}}""")
                .json().field("result").field("approximate") shouldBe JsonValue.Bool(true)
        }
    }

    "explicit approximate input retains estimate provenance through the evaluation API" {
        testApplication {
            application { calcModule() }
            val result = client.calculate("evaluate", """{"expr":"approx(1/3)"}""")
                .json().field("result")
            result.field("approximate") shouldBe JsonValue.Bool(true)
            result.field("value").field("approximate") shouldBe JsonValue.Bool(true)
            client.get("/v1/functions").bodyAsText() shouldContain "approx"
        }
    }

    "supplied registers work and do not leak into later requests" {
        testApplication {
            application { calcModule() }
            val response = client.calculate("evaluate", """{"expr":"2A+1","registers":{"A":"5"}}""")
            response.status shouldBe HttpStatusCode.OK
            response.json().field("result").field("value").field("exact").text() shouldBe "11"
            client.calculate("evaluate", """{"expr":"A"}""").status shouldBe HttpStatusCode.UnprocessableEntity
        }
    }

    "angle and display settings are request-local" {
        testApplication {
            application { calcModule() }
            client.calculate("evaluate", """{"expr":"sin(pi/2)","settings":{"angle":"RAD"}}""")
                .json().field("result").field("value").field("exact").text() shouldBe "1"
            client.calculate("evaluate", """{"expr":"sin(30)"}""")
                .json().field("result").field("value").field("exact").text() shouldBe "1/2"
        }
    }

    "machine exact values survive localized rounded presentation and can be evaluated again" {
        testApplication {
            application { calcModule() }
            for (expr in listOf("1/3", "1234567", "(1/3) m", "1/3+i/7")) {
                val body = Json.encode(mapOf("expr" to expr, "settings" to mapOf("exactForm" to "DECIMAL",
                    "displayPrecision" to 4, "grouping" to "INDIAN", "numerals" to "DEVANAGARI", "complexEnabled" to true)))
                val first = client.calculate("evaluate", body).json().field("result").field("value")
                val exact = first.field("exact").text()
                exact.none { it in '\u0966'..'\u096f' || it == ',' } shouldBe true
                val repeated = client.calculate("evaluate", Json.encode(mapOf("expr" to exact,
                    "settings" to mapOf("complexEnabled" to true)))).json().field("result").field("value")
                repeated.field("exact") shouldBe first.field("exact")
            }
            val third = client.calculate("evaluate", """{"expr":"1/3","settings":{"exactForm":"DECIMAL","displayPrecision":4}}""")
            third.json().field("result").field("value").field("exact").text() shouldBe "1/3"
            val approximate = client.calculate("evaluate", """{"expr":"sin(1)","settings":{"angle":"RAD","displayPrecision":4}}""")
                .json().field("result").field("value")
            approximate.field("approximate") shouldBe JsonValue.Bool(true)
            (approximate.field("exact").text().length > approximate.field("decimal").text().length) shouldBe true
        }
    }

    "unit conversion supports affine temperature and rejects mismatched dimensions" {
        testApplication {
            application { calcModule() }
            val response = client.calculate("convert", """{"value":"0","from":"degC","to":"degF"}""")
            response.status shouldBe HttpStatusCode.OK
            response.json().field("result").field("exact").text() shouldBe "32"
            val wrong = client.calculate("convert", """{"value":1,"from":"m","to":"kg"}""")
            wrong.status shouldBe HttpStatusCode.UnprocessableEntity
            wrong.json().field("kind").text() shouldBe "DOMAIN"
        }
    }

    "matrix inverse and determinant keep exact fractions" {
        testApplication {
            application { calcModule() }
            val inverse = client.calculate("matrix", """{"operation":"inverse","a":[[2,0],[0,4]],"settings":{"exactForm":"FRACTION"}}""")
            inverse.status shouldBe HttpStatusCode.OK
            val rows = inverse.json().field("result").field("cells").items()
            rows[0].items()[0].field("exact").text() shouldBe "1/2"
            rows[1].items()[1].field("exact").text() shouldBe "1/4"
            client.calculate("matrix", """{"operation":"determinant","a":[[1,2],[3,4]]}""")
                .json().field("result").field("exact").text() shouldBe "-2"
        }
    }

    "matrix arithmetic and rank routes map their output structures" {
        testApplication {
            application { calcModule() }
            val multiply = client.calculate("matrix", """{"operation":"multiply","a":[[1,2]],"b":[[3],[4]]}""")
            multiply.status shouldBe HttpStatusCode.OK
            multiply.json().field("result").field("cells").items()[0].items()[0].field("exact").text() shouldBe "11"
            client.calculate("matrix", """{"operation":"rank","a":[[1,2],[2,4]]}""")
                .json().field("result") shouldBe JsonValue.Number("1")
        }
    }

    "singular inverse is an error and ragged matrices are invalid input" {
        testApplication {
            application { calcModule() }
            client.calculate("matrix", """{"operation":"inverse","a":[[1,2],[2,4]]}""").status shouldBe HttpStatusCode.UnprocessableEntity
            client.calculate("matrix", """{"operation":"transpose","a":[[1],[2,3]]}""").status shouldBe HttpStatusCode.BadRequest
        }
    }

    "statistics preserve sample and population distinctions" {
        testApplication {
            application { calcModule() }
            val response = client.calculate("statistics", """{"data":[1,"2",3]}""")
            response.status shouldBe HttpStatusCode.OK
            val result = response.json().field("result")
            result.field("mean").field("exact").text() shouldBe "2"
            result.field("populationVariance").field("exact").text() shouldBe "2/3"
            result.field("sampleVariance").field("exact").text() shouldBe "1"
            client.calculate("statistics", """{"data":[5]}""")
                .json().field("result").field("sampleVariance") shouldBe JsonValue.Null
        }
    }

    "regression returns a fitted line and correlation" {
        testApplication {
            application { calcModule() }
            val response = client.calculate("regression", """{"points":[[0,1],[1,3],[2,5]]}""")
            response.status shouldBe HttpStatusCode.OK
            val result = response.json().field("result")
            result.field("slope").field("exact").text() shouldBe "2"
            result.field("intercept").field("exact").text() shouldBe "1"
            result.field("rSquared").field("exact").text() shouldBe "1"
        }
    }

    "base conversion has no floating-point rounding" {
        testApplication {
            application { calcModule() }
            val response = client.calculate("base", """{"value":"9007199254740993","from":10,"to":16}""")
            response.status shouldBe HttpStatusCode.OK
            response.json().field("result").text() shouldBe "20000000000001"
            client.calculate("base", """{"value":"102","from":2,"to":10}""").status shouldBe HttpStatusCode.UnprocessableEntity
        }
    }

    "a constant regression response reports undefined correlation without discarding its fit" {
        testApplication {
            application { calcModule() }
            val response = client.calculate("regression", """{"points":[[0,5],[1,5],[2,5]]}""")
            response.status shouldBe HttpStatusCode.OK
            val result = response.json().field("result")
            result.field("slope").field("exact").text() shouldBe "0"
            result.field("intercept").field("exact").text() shouldBe "5"
            result.field("r") shouldBe JsonValue.Null
            result.field("rSquared") shouldBe JsonValue.Null
        }
    }

    "sampling keeps undefined points separate from valid values" {
        testApplication {
            application { calcModule() }
            val response = client.calculate("graph/sample", """{"expr":"1/x","xMin":-1,"xMax":1,"samples":3}""")
            response.status shouldBe HttpStatusCode.OK
            val samples = response.json().field("samples").items()
            samples.map { it.field("state").text() } shouldBe listOf("defined", "undefined", "defined")
            samples[1].field("kind").text() shouldBe "DIVIDE_BY_ZERO"
        }
    }

    "graph roots and intersections include verification residuals" {
        testApplication {
            application { calcModule() }
            val response = client.calculate("graph/roots", """{"expr":"x^2-1","xMin":-2,"xMax":2}""")
            response.status shouldBe HttpStatusCode.OK
            response.json().field("roots").items().map { it.field("x").field("exact").text() } shouldBe listOf("-1", "1")
            response.json().field("roots").items().all { it.field("residual").field("exact").text() == "0" } shouldBe true
            response.json().field("roots").items().all { it.field("approximate") == JsonValue.Bool(false) } shouldBe true
            val cross = client.calculate("graph/intersections", """{"expr":"x","other":"2-x","xMin":0,"xMax":2}""")
            cross.status shouldBe HttpStatusCode.OK
            cross.json().field("roots").items().single().field("x").field("exact").text() shouldBe "1"
        }
    }

    "a pole is never advertised as a verified root" {
        testApplication {
            application { calcModule() }
            val response = client.calculate("graph/roots", """{"expr":"1/x","xMin":-1,"xMax":1,"samples":32}""")
            val result = response.json()
            if (response.status == HttpStatusCode.OK) result.field("roots").items() shouldBe emptyList()
            else {
                response.status shouldBe HttpStatusCode.UnprocessableEntity
                val kind = result.field("kind").text()
                (kind == "UNVERIFIED" || kind == "UNSUPPORTED") shouldBe true
                if (kind == "UNVERIFIED") {
                    result.field("verified").items() shouldBe emptyList()
                    result.field("candidates").items().all { it.field("approximate") == JsonValue.Bool(true) } shouldBe true
                } else result.field("error").text().isNotBlank() shouldBe true
            }
        }
    }

    "a rational bisection coordinate is still labelled an approximate root" {
        testApplication {
            application { calcModule() }
            val response = client.calculate("graph/roots", """{"expr":"x^3-2","xMin":0,"xMax":2,"samples":64}""")
            response.status shouldBe HttpStatusCode.OK
            val body = response.json()
            body.field("method").text().startsWith("numeric") shouldBe true
            body.field("approximate") shouldBe JsonValue.Bool(true)
            val root = body.field("roots").items().single()
            root.field("approximate") shouldBe JsonValue.Bool(true)
            root.field("x").field("approximate") shouldBe JsonValue.Bool(true)
        }
    }

    "surface grids have exact row-major coordinates and two unknown bindings" {
        testApplication {
            application { calcModule() }
            val response = client.calculate("graph/surface", """{"expr":"x+2*y","xMin":-1,"xMax":1,"yMin":-1,"yMax":1,"columns":3,"rows":3}""")
            response.status shouldBe HttpStatusCode.OK
            val result = response.json()
            result.field("order").text() shouldBe "row-major"
            val points = result.field("samples").items()
            points.size shouldBe 9
            points.first().field("z").field("exact").text() shouldBe "-3"
            points[4].field("z").field("exact").text() shouldBe "0"
            points.last().field("z").field("exact").text() shouldBe "3"
            result.field("warnings").items().isNotEmpty() shouldBe true
        }
    }

    "surface poles remain gaps and grid work is bounded" {
        testApplication {
            application { calcModule() }
            val response = client.calculate("graph/surface", """{"expr":"1/(x-y)","xMin":-1,"xMax":1,"yMin":-1,"yMax":1,"columns":3,"rows":3}""")
            response.status shouldBe HttpStatusCode.OK
            response.json().field("counts").field("undefined") shouldBe JsonValue.Number("3")
            client.calculate("graph/surface", """{"expr":"x+y","columns":33}""").status shouldBe HttpStatusCode.BadRequest
        }
    }

    "symbolic output preserves domain and calculus metadata" {
        testApplication {
            application { calcModule() }
            val response = client.calculate("symbolic", """{"operation":"integrate","expr":"2*x"}""")
            response.status shouldBe HttpStatusCode.OK
            val result = response.json().field("result")
            result.field("angleMode").text() shouldBe "RAD"
            result.field("constantOfIntegration") shouldBe JsonValue.Bool(true)
            result.field("display").text() shouldContain "+ C"
            result.field("domainRestricted") shouldBe JsonValue.Bool(false)
            val restricted = client.calculate("symbolic", """{"operation":"simplify","expr":"x/x"}""")
                .json().field("result")
            restricted.field("domainRestricted") shouldBe JsonValue.Bool(true)
            restricted.field("conditions").items().isNotEmpty() shouldBe true
        }
    }

    "invalid JSON never reaches the calculator" {
        testApplication {
            application { calcModule() }
            for (body in listOf("", "{", "[]", "null", "{\"expr\":\"1\",\"expr\":\"2\"}", "{\"expr\":\"1\",}", "{\"expr\":\"\\uD800\"}")) {
                client.calculate("evaluate", body).status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    "definite integration exposes its estimate and uncertainty instead of exactness claims" {
        testApplication {
            application { calcModule() }
            val response = client.calculate("integrate", """{"expr":"3*x^2","lower":0,"upper":1}""")
            response.status shouldBe HttpStatusCode.OK
            val result = response.json().field("result")
            result.field("value").field("decimal").text() shouldBe "1"
            result.field("approximate") shouldBe JsonValue.Bool(true)
            result.field("angleMode").text() shouldBe "RAD"
            result.field("method").text() shouldContain "Gauss-Kronrod"
            result.field("warnings").items().isNotEmpty() shouldBe true
        }
    }

    "definite integration reverses bounds and refuses an internal pole" {
        testApplication {
            application { calcModule() }
            client.calculate("integrate", """{"expr":"1","lower":1,"upper":0}""")
                .json().field("result").field("value").field("decimal").text() shouldBe "-1"
            client.calculate("integrate", """{"expr":"1/x","lower":-1,"upper":1}""")
                .status shouldBe HttpStatusCode.UnprocessableEntity
        }
    }

    "integration budgets are bounded by the server" {
        testApplication {
            application { calcModule() }
            client.calculate("integrate", """{"expr":"x","lower":0,"upper":1,"maxEvaluations":999999}""")
                .status shouldBe HttpStatusCode.BadRequest
            client.calculate("integrate", """{"expr":"x","lower":0,"upper":1,"absoluteTolerance":"1e-100"}""")
                .status shouldBe HttpStatusCode.BadRequest
        }
    }

    "unknown fields and wrong value types are rejected" {
        testApplication {
            application { calcModule() }
            for (body in listOf("""{"expr":2}""", """{"expr":"1","model":"paid"}""", """{"expr":"1","registers":{"x":2}}""", """{"expr":"1","settings":{"angle":"turns"}}""")) {
                client.calculate("evaluate", body).status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    "precision and expression nesting are bounded before evaluation" {
        testApplication {
            application { calcModule() }
            client.calculate("evaluate", """{"expr":"1/3","settings":{"computePrecision":1000000}}""").status shouldBe HttpStatusCode.BadRequest
            client.calculate("evaluate", """{"expr":"1/3","settings":{"displayPrecision":40,"computePrecision":40}}""").status shouldBe HttpStatusCode.BadRequest
            val deep = "(".repeat(65) + "1" + ")".repeat(65)
            val response = client.calculate("evaluate", Json.encode(mapOf("expr" to deep)))
            response.status shouldBe HttpStatusCode.UnprocessableEntity
            response.json().field("kind").text() shouldBe "RESOURCE_LIMIT"
            client.calculate("evaluate", Json.encode(mapOf("expr" to "1".repeat(4097)))).status shouldBe HttpStatusCode.UnprocessableEntity
        }
    }

    "large collections and invalid graph ranges are refused" {
        testApplication {
            application { calcModule() }
            client.calculate("statistics", Json.encode(mapOf("data" to List(1001) { 1 }))).status shouldBe HttpStatusCode.UnprocessableEntity
            client.calculate("graph/sample", """{"expr":"x","xMin":1,"xMax":1}""").status shouldBe HttpStatusCode.BadRequest
            client.calculate("graph/sample", """{"expr":"x","samples":1001}""").status shouldBe HttpStatusCode.BadRequest
            client.calculate("graph/sample", """{"expr":"x","xMin":"-1e13","xMax":1}""").status shouldBe HttpStatusCode.UnprocessableEntity
        }
    }

    "HTTP media types encoding and body size are enforced" {
        testApplication {
            application { calcModule() }
            client.post("/v1/evaluate") { setBody("expr=1") }.status shouldBe HttpStatusCode.UnsupportedMediaType
            client.post("/v1/evaluate") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.ContentEncoding, "gzip")
                setBody("{}")
            }.status shouldBe HttpStatusCode.UnsupportedMediaType
            client.calculate("evaluate", " ".repeat(ApiLimits.BODY_BYTES + 1)).status shouldBe HttpStatusCode.PayloadTooLarge
        }
    }

    "invalid UTF-8 and duplicate escaped keys are refused at the boundary" {
        testApplication {
            application { calcModule() }
            client.post("/v1/evaluate") {
                contentType(ContentType.Application.Json)
                setBody(byteArrayOf(0x7B, 0x22, 0xC3.toByte(), 0x28, 0x22, 0x3A, 0x31, 0x7D))
            }.status shouldBe HttpStatusCode.BadRequest
            client.calculate("evaluate", """{"expr":"1","\u0065xpr":"2"}""")
                .status shouldBe HttpStatusCode.BadRequest
        }
    }

    "guarded extreme arithmetic returns a typed error and leaves later requests usable" {
        testApplication {
            application { calcModule() }
            // Each expression is beyond a pre-allocation guard verified in NumericLimits.
            for (expression in listOf("exp(10001)", "2^1000001", "1e20001")) {
                withClue("expression = $expression") {
                    val response = client.calculate("evaluate", Json.encode(mapOf("expr" to expression)))
                    response.status shouldBe HttpStatusCode.UnprocessableEntity
                    response.json().field("ok") shouldBe JsonValue.Bool(false)
                }
            }
            client.calculate("evaluate", """{"expr":"2+2"}""")
                .json().field("result").field("value").field("exact").text() shouldBe "4"
        }
    }

    "typed math errors have positions and no exception stack traces" {
        testApplication {
            application { calcModule() }
            val response = client.calculate("evaluate", """{"expr":"1/0"}""")
            response.status shouldBe HttpStatusCode.UnprocessableEntity
            val result = response.json()
            result.field("kind").text() shouldBe "DIVIDE_BY_ZERO"
            result.field("ok") shouldBe JsonValue.Bool(false)
            result.fields.containsKey("position") shouldBe true
            result.fields.containsKey("stackTrace") shouldBe false
        }
    }
})
