package com.vaibhav.calc.api

import io.kotest.core.spec.style.StringSpec
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.testApplication
import java.math.BigDecimal

private suspend fun HttpClient.tool(path: String, body: String): HttpResponse = post("/v1/$path") {
    contentType(ContentType.Application.Json); setBody(body)
}
private suspend fun HttpResponse.tree(): JsonValue = Json.parse(bodyAsText())
private fun JsonValue.at(name: String): JsonValue = (this as JsonValue.Obj).fields.getValue(name)
private fun JsonValue.word(): String = (this as JsonValue.Str).value
private fun JsonValue.list(): List<JsonValue> = (this as JsonValue.Arr).values
private suspend fun HttpResponse.answer(): JsonValue {
    status shouldBe HttpStatusCode.OK
    return tree().at("result")
}
private fun JsonValue.exact(): String = at("exact").word()

class ApiToolsTest : StringSpec({
    "tool discovery preserves the v1 wire contract and lists required fields" {
        testApplication {
            application { calcModule() }
            val caps = client.get("/v1/capabilities").tree()
            caps.at("apiVersion") shouldBe JsonValue.Number("1")
            caps.at("releaseVersion").word() shouldBe "2.0.0"
            val operations = caps.at("operations").list().map { it.word() }
            operations.containsAll(listOf("evaluate", "finance", "dates", "taylor", "bitwise", "series")) shouldBe true
            val catalog = client.get("/v1/tool-catalog")
            catalog.status shouldBe HttpStatusCode.OK
            catalog.bodyAsText() shouldContain "annualRate"
            catalog.headers[HttpHeaders.CacheControl] shouldBe "no-store"
        }
    }

    "finance preserves exact tax and fractional shares instead of rounding to money" {
        testApplication {
            application { calcModule() }
            val tax = client.tool("finance", """{"operation":"addTax","amount":"1000","rate":"18"}""").answer()
            tax.at("tax").exact() shouldBe "180"
            tax.at("gross").exact() shouldBe "1180"
            val inverse = client.tool("finance", """{"operation":"removeTax","amount":"1180","rate":"18"}""").answer()
            inverse.at("net").exact() shouldBe "1000"
            val shares = client.tool("finance", """{"operation":"shares","amount":"10","ratio":["1","2"]}""").answer()
            shares.at("parts").list().map { it.exact() } shouldBe listOf("10/3", "20/3")
            shares.at("simplestRatio").list().map { it.word() } shouldBe listOf("1", "2")
        }
    }

    "zero-rate loans savings and schedule use independent known totals" {
        testApplication {
            application { calcModule() }
            val loan = client.tool("finance", """{"operation":"loan","principal":"1200","annualRate":"0","months":12}""").answer()
            loan.at("payment").exact() shouldBe "100"
            loan.at("totalInterest").exact() shouldBe "0"
            val savings = client.tool("finance", """{"operation":"sip","monthly":"100","annualRate":"0","months":12}""").answer()
            savings.at("futureValue").exact() shouldBe "1200"
            savings.at("paymentTiming").word() shouldBe "startOfMonth"
            val schedule = client.tool("finance", """{"operation":"schedule","principal":"1200","annualRate":"0","months":12}""").answer().list()
            schedule.size shouldBe 1
            schedule.single().at("principal").exact() shouldBe "1200"
            schedule.single().at("balance").exact() shouldBe "0"
        }
    }

    "tiny positive finance rates do not destroy the deposit or loan principal" {
        testApplication {
            application { calcModule() }
            val sip = client.tool("finance", """{"operation":"sip","monthly":"1000","annualRate":"1e-50","months":12}""").answer()
            BigDecimal(sip.at("futureValue").exact()).compareTo(BigDecimal("12000")) shouldBe 0
            (BigDecimal(sip.at("gain").exact()).signum() > 0) shouldBe true
            val loan = client.tool("finance", """{"operation":"affordableLoan","payment":"1000","annualRate":"1e-50","months":12}""").answer()
            BigDecimal(loan.at("principal").exact()).compareTo(BigDecimal("12000")) shouldBe 0
            (BigDecimal(loan.at("totalInterest").exact()).signum() > 0) shouldBe true
        }
    }

    "percentage fields are percentage units and require explicit literal rates" {
        testApplication {
            application { calcModule() }
            // 0.18 is 0.18 percent, not 18 percent; no UI suffix repair occurs in this typed API.
            val tax = client.tool("finance", """{"operation":"addTax","amount":"1000","rate":"0.18"}""").answer()
            tax.at("tax").exact() shouldBe "9/5"
            val invalid = client.tool("finance", """{"operation":"discount","price":"100","percent":"101"}""")
            invalid.status shouldBe HttpStatusCode.UnprocessableEntity
            invalid.tree().at("kind").word() shouldBe "DOMAIN"
        }
    }

    "dates are deterministic with leap-day and direction semantics" {
        testApplication {
            application { calcModule() }
            val between = client.tool("dates", """{"operation":"between","from":"2024-03-01","to":"28/02/2024"}""").answer()
            between.at("totalDays").word() shouldBe "2"
            between.at("forward") shouldBe JsonValue.Bool(false)
            val age = client.tool("dates", """{"operation":"age","birth":"2000-02-29","asOf":"2023-03-01"}""").answer()
            age.at("years") shouldBe JsonValue.Number("23")
            age.at("months") shouldBe JsonValue.Number("0")
            age.at("daysToNextBirthday").word() shouldBe "0"
            val leap = client.tool("dates", """{"operation":"shift","date":"2024-02-28","days":1}""").answer()
            leap.at("date").word() shouldBe "2024-02-29"
            client.tool("dates", """{"operation":"weekday","date":"31/02/2026"}""").status shouldBe HttpStatusCode.UnprocessableEntity
        }
    }

    "durations handle signed differences and midnight without long overflow" {
        testApplication {
            application { calcModule() }
            client.tool("duration", """{"operation":"add","a":"1:45:30","b":"0:50:45"}""").answer()
                .at("seconds").word() shouldBe "9375"
            client.tool("duration", """{"operation":"subtract","a":"0:30","b":"1:00"}""").answer()
                .at("seconds").word() shouldBe "-1800"
            client.tool("duration", """{"operation":"between","from":"23:30","to":"00:15"}""").answer()
                .at("seconds").word() shouldBe "2700"
            client.tool("duration", """{"operation":"format","seconds":"-9223372036854775808"}""")
                .status shouldBe HttpStatusCode.UnprocessableEntity
            client.tool("duration", """{"operation":"between","from":"24:00","to":"00:15"}""")
                .status shouldBe HttpStatusCode.UnprocessableEntity
        }
    }

    "words do not convert a rounded estimate into a certified integer" {
        testApplication {
            application { calcModule() }
            val value = client.tool("words", """{"operation":"indian","value":"1234567"}""").answer()
            value.at("text").word() shouldBe "twelve lakh thirty-four thousand five hundred and sixty-seven"
            client.tool("words", """{"operation":"international","value":"approx(12)"}""").status shouldBe HttpStatusCode.BadRequest
            client.tool("words", """{"operation":"rupees","rupees":"12","paise":100}""").status shouldBe HttpStatusCode.BadRequest
        }
    }

    "BMI has exact computation and explicit adult screening scope" {
        testApplication {
            application { calcModule() }
            val bmi = client.tool("bmi", """{"weightKg":"64","heightCm":"160"}""").answer()
            bmi.at("index").exact() shouldBe "25"
            bmi.at("band").word() shouldBe "OVERWEIGHT"
            bmi.at("scope").word() shouldContain "not a diagnosis"
            client.tool("bmi", """{"weightKg":"64","heightCm":"0"}""").status shouldBe HttpStatusCode.UnprocessableEntity
        }
    }

    "vector products and angle use shared exact math and the selected angle mode" {
        testApplication {
            application { calcModule() }
            client.tool("vector", """{"operation":"dot","a":[1,2,3],"b":[4,5,6]}""").answer().exact() shouldBe "32"
            client.tool("vector", """{"operation":"cross","a":[1,2,3],"b":[4,5,6]}""").answer()
                .list().map { it.exact() } shouldBe listOf("-3", "6", "-3")
            val angle = client.tool("vector", """{"operation":"angle","a":[1,0],"b":[0,1],"settings":{"angle":"DEG"}}""").answer()
            angle.exact() shouldBe "90"
            angle.at("angleMode").word() shouldBe "DEG"
            client.tool("vector", """{"operation":"projection","a":[1,0],"b":[0,0]}""").status shouldBe HttpStatusCode.UnprocessableEntity
        }
    }

    "shape validation precedes expensive numeric parsing" {
        testApplication {
            application { calcModule() }
            // Shape is rejected before trying the invalid scalar.
            client.tool("vector", """{"operation":"dot","a":["not_a_number"],"b":[1,2]}""")
                .status shouldBe HttpStatusCode.BadRequest
            client.tool("curve-fit", """{"operation":"quadratic","points":[["not_a_number",1],[2,3],[4]]}""")
                .status shouldBe HttpStatusCode.BadRequest
            val many = (1..17).joinToString(",")
            val response = client.tool("vector", """{"operation":"length","a":[$many]}""")
            response.status shouldBe HttpStatusCode.UnprocessableEntity
            response.tree().at("kind").word() shouldBe "RESOURCE_LIMIT"
        }
    }

    "new matrix operations and polynomial route keep exact roots and coefficients" {
        testApplication {
            application { calcModule() }
            val eigen = client.tool("matrix", """{"operation":"eigenvalues","a":[[2,0],[0,3]]}""").answer()
            eigen.at("values").list().map { it.exact() }.sorted() shouldBe listOf("2", "3")
            eigen.at("polynomial").list().map { it.exact() } shouldBe listOf("6", "-5", "1")
            val polynomial = client.tool("evaluate", """{"expr":"x^3-6*x^2+11*x-6=0"}""").answer()
            polynomial.at("roots").list().map { it.exact() }.sorted() shouldBe listOf("1", "2", "3")
            client.tool("matrix", """{"operation":"eigenvalues","a":[[1,2,3],[4,5,6]]}""").status shouldBe HttpStatusCode.UnprocessableEntity
        }
    }

    "number theory exposes deterministic factors and refuses uncertain whole inputs" {
        testApplication {
            application { calcModule() }
            val factors = client.tool("number-theory", """{"operation":"factorize","value":"360"}""").answer().list()
            factors.map { it.at("prime").word() } shouldBe listOf("2", "3", "5")
            factors.map { it.at("exponent") } shouldBe listOf(JsonValue.Number("3"), JsonValue.Number("2"), JsonValue.Number("1"))
            client.tool("number-theory", """{"operation":"lcm","values":[12,18]}""").answer().at("value").word() shouldBe "36"
            client.tool("number-theory", """{"operation":"factorize","value":"100000000000001"}""").status shouldBe HttpStatusCode.UnprocessableEntity
            client.tool("number-theory", """{"operation":"isPrime","value":"approx(97)"}""").status shouldBe HttpStatusCode.BadRequest
        }
    }

    "binomial tails match four equally likely coin outcomes" {
        testApplication {
            application { calcModule() }
            val p = client.tool("probability", """{"operation":"binomial","n":2,"k":1,"p":"1/2"}""").answer()
            p.at("exactly").exact() shouldBe "1/2"
            p.at("atMost").exact() shouldBe "3/4"
            p.at("above").exact() shouldBe "1/4"
            client.tool("probability", """{"operation":"normal","mean":0,"sigma":0,"x":0}""").status shouldBe HttpStatusCode.UnprocessableEntity
            val normal = client.tool("probability", """{"operation":"normal","mean":0,"sigma":1,"x":0}""").answer()
            normal.at("atMost").exact() shouldBe "1/2"
        }
    }

    "curve fitting returns model coefficients and preserves undefined fit quality" {
        testApplication {
            application { calcModule() }
            val fit = client.tool("curve-fit", """{"operation":"quadratic","points":[[0,1],[1,2],[2,5],[3,10]]}""").answer()
            fit.at("a").exact() shouldBe "1"
            fit.at("b").exact() shouldBe "0"
            fit.at("c").exact() shouldBe "1"
            fit.at("rSquared").exact() shouldBe "1"
            val constant = client.tool("curve-fit", """{"operation":"quadratic","points":[[0,2],[1,2],[2,2]]}""").answer()
            constant.at("rSquared") shouldBe JsonValue.Null
            client.tool("curve-fit", """{"operation":"power","points":[[0,1],[1,2]]}""").status shouldBe HttpStatusCode.UnprocessableEntity
        }
    }

    "quartiles state the method and return no invented mode for distinct data" {
        testApplication {
            application { calcModule() }
            val q = client.tool("quartiles", """{"data":[1,2,3,4,5]}""").answer()
            q.at("q1").exact() shouldBe "3/2"
            q.at("median").exact() shouldBe "3"
            q.at("q3").exact() shouldBe "9/2"
            q.at("modes").list().isEmpty() shouldBe true
            q.at("method").word() shouldContain "exclude the middle"
        }
    }

    "Taylor coefficients are exact where possible but the polynomial is only a local approximation" {
        testApplication {
            application { calcModule() }
            val t = client.tool("taylor", """{"expr":"sin(x)","center":"0","order":3,"settings":{"angle":"DEG"}}""").answer()
            t.at("coefficients").list().map { it.exact() } shouldBe listOf("0", "1", "0", "-1/6")
            t.at("angleMode").word() shouldBe "RAD"
            t.at("exactCoefficients") shouldBe JsonValue.Bool(true)
            t.at("approximateFunction") shouldBe JsonValue.Bool(true)
            client.tool("taylor", """{"expr":"sin(x)","center":"0","order":11}""").status shouldBe HttpStatusCode.BadRequest
        }
    }

    "limit estimates distinguish sides and never claim exact proof" {
        testApplication {
            application { calcModule() }
            val l = client.tool("limit", """{"expr":"sin(x)/x","point":"0","settings":{"angle":"DEG"}}""").answer()
            l.at("left").at("state").word() shouldBe "settles"
            l.at("right").at("state").word() shouldBe "settles"
            l.at("twoSided").at("value").at("approximate") shouldBe JsonValue.Bool(true)
            l.at("angleMode").word() shouldBe "RAD"
            l.at("warning").word() shouldContain "not a proof"
        }
    }

    "tables preserve undefined rows and register snapshots" {
        testApplication {
            application { calcModule() }
            val table = client.tool("table", """{"expr":"A/x","start":-1,"end":1,"step":1,"registers":{"A":2}}""").answer()
            val rows = table.at("rows").list()
            rows.map { it.at("state").word() } shouldBe listOf("defined", "undefined", "defined")
            rows.first().at("y").exact() shouldBe "-2"
            rows.last().at("y").exact() shouldBe "2"
            client.tool("table", """{"expr":"x","start":0,"end":200,"step":1}""").status shouldBe HttpStatusCode.UnprocessableEntity
            val missing = client.tool("table", """{"expr":"A*x","start":0,"end":1,"step":1}""").answer()
            missing.at("rows").list().all { it.at("state").word() == "unevaluated" } shouldBe true
        }
    }

    "finite sums and products are exact and refuse incomplete answers" {
        testApplication {
            application { calcModule() }
            client.tool("series", """{"operation":"sum","expr":"x^2","from":1,"to":10}""").answer()
                .at("value").exact() shouldBe "385"
            client.tool("series", """{"operation":"product","expr":"x","from":1,"to":5}""").answer()
                .at("value").exact() shouldBe "120"
            client.tool("series", """{"operation":"sum","expr":"1/x","from":-1,"to":1}""").status shouldBe HttpStatusCode.UnprocessableEntity
            client.tool("series", """{"operation":"product","expr":"x","from":1,"to":201}""").status shouldBe HttpStatusCode.UnprocessableEntity
        }
    }

    "bitwise operations have explicit bases and unsigned word boundaries" {
        testApplication {
            application { calcModule() }
            val bits = client.tool("bitwise", """{"operation":"xor","a":"1100","b":"1010","base":2}""").answer()
            bits.at("decimal").word() shouldBe "6"
            bits.at("binary").word() shouldBe "110"
            client.tool("bitwise", """{"operation":"not","a":"0","bits":8}""").answer().at("decimal").word() shouldBe "255"
            client.tool("bitwise", """{"operation":"not","a":"256","bits":8}""").status shouldBe HttpStatusCode.UnprocessableEntity
            client.tool("bitwise", """{"operation":"not","a":"0","bits":9}""").status shouldBe HttpStatusCode.UnprocessableEntity
        }
    }

    "new routes reject wrong types unused fields and structural overload then remain healthy" {
        testApplication {
            application { calcModule() }
            val cases = listOf(
                "finance" to """{"operation":"loan","principal":1200,"annualRate":0,"months":"12"}""",
                "finance" to """{"operation":"loan","principal":1200,"annualRate":0,"months":12,"rate":3}""",
                "words" to """{"operation":"international","value":true}""",
                "bmi" to """{"operation":"ignored","weightKg":64,"heightCm":160}""",
                "series" to """{"operation":"sum","expr":"x","from":1}""",
            )
            for ((path, body) in cases) withClue(path) {
                client.tool(path, body).status shouldBe HttpStatusCode.BadRequest
            }
            val deep = "1+".repeat(100) + "x"
            client.tool("taylor", Json.encode(mapOf("expr" to deep, "center" to "0", "order" to 1)))
                .status shouldBe HttpStatusCode.UnprocessableEntity
            client.tool("finance", Json.encode(mapOf("operation" to "portion", "percent" to "1".repeat(257), "amount" to "1")))
                .status shouldBe HttpStatusCode.UnprocessableEntity
            client.tool("evaluate", """{"expr":"2+3"}""").answer().at("value").exact() shouldBe "5"
        }
    }
    "normal upper tails retain tiny positive probabilities" {
        testApplication {
            application { calcModule() }
            val tail = client.tool("probability", """{"operation":"normal","mean":0,"sigma":1,"x":20}""").answer()
            val upper = BigDecimal(tail.at("above").exact())
            // Independent erfc reference: erfc(20/sqrt(2))/2 = 2.753624118606...e-89.
            (upper > BigDecimal("2.7536e-89") && upper < BigDecimal("2.7537e-89")) shouldBe true
            tail.at("above").at("approximate") shouldBe JsonValue.Bool(true)
        }
    }

    "bounded and unknown estimates retain reusable inputs and explicit uncertainty" {
        testApplication {
            application { calcModule() }
            val bounded = client.tool("evaluate", """{"expr":"approx(10,0.1)+2"}""").answer().at("value")
            bounded.at("approximate") shouldBe JsonValue.Bool(true)
            bounded.at("uncertainty").at("kind").word() shouldBe "bounded"
            val bound = BigDecimal(bounded.at("uncertainty").at("absoluteError").word())
            (bound >= BigDecimal("0.1") && bound <= BigDecimal("0.100000000000000000001")) shouldBe true
            val reusable = bounded.at("input").word()
            reusable shouldContain "approx("
            val again = client.tool("evaluate", Json.encode(mapOf("expr" to reusable))).answer().at("value")
            again.at("uncertainty") shouldBe bounded.at("uncertainty")
            again.exact() shouldBe bounded.exact()
            val unknown = client.tool("evaluate", """{"expr":"approx(10)"}""").answer().at("value")
            unknown.at("uncertainty").at("kind").word() shouldBe "unknown"
            unknown.at("approximate") shouldBe JsonValue.Bool(true)
            val exact = client.tool("evaluate", """{"expr":"1/3"}""").answer().at("value")
            exact.at("uncertainty").at("kind").word() shouldBe "exact"
            exact.at("input").word() shouldBe "1/3"
        }
    }

    "complex and quantity components keep individual uncertainty metadata" {
        testApplication {
            application { calcModule() }
            val complex = client.tool("evaluate", """{"expr":"approx(3,0.1)+2i","settings":{"complexEnabled":true}}""").answer().at("value")
            complex.at("kind").word() shouldBe "complex"
            complex.at("real").at("uncertainty").at("kind").word() shouldBe "bounded"
            complex.at("imaginary").at("uncertainty").at("kind").word() shouldBe "exact"
            complex.at("input").word() shouldContain "approx("
            val quantity = client.tool("evaluate", """{"expr":"approx(10,0.1) m"}""").answer().at("value")
            quantity.at("kind").word() shouldBe "quantity"
            quantity.at("coefficient").at("uncertainty").at("kind").word() shouldBe "bounded"
            quantity.at("input").word() shouldContain "approx("
        }
    }

    "point-only tools refuse uncertain classification and domain parameters" {
        testApplication {
            application { calcModule() }
            val cases = listOf(
                "finance" to """{"operation":"loan","principal":1200,"annualRate":"approx(0,1)","months":12}""",
                "bmi" to """{"weightKg":"approx(64,0.1)","heightCm":160}""",
                "probability" to """{"operation":"binomial","n":2,"k":1,"p":"approx(0.5)"}""",
                "quartiles" to """{"data":[1,"approx(2)",3]}""",
                "table" to """{"expr":"x","start":0,"end":1,"step":"approx(0.1)"}""",
            )
            for ((path, body) in cases) {
                val response = client.tool(path, body)
                withClue(path) {
                    response.status shouldBe HttpStatusCode.UnprocessableEntity
                    response.tree().at("kind").word() shouldBe "UNSUPPORTED"
                }
            }
            client.tool("evaluate", """{"expr":"2+3"}""").answer().at("value").exact() shouldBe "5"
        }
    }

})
