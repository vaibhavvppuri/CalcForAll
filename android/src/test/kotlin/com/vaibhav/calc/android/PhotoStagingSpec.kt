package com.vaibhav.calc.android

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import java.io.IOException

class PhotoStagingSpec : StringSpec({
    "a completed current import publishes the exact prepared object once" {
        val old = Any(); val replacement = Any()
        var displayed = old; var publications = 0
        val result = PhotoStaging.prepare(7, { 7 }, { replacement }) { displayed = it; publications++ }
        (result === replacement) shouldBe true
        (displayed === replacement) shouldBe true
        publications shouldBe 1
    }

    "failed decoding leaves the previous image and manual correction untouched" {
        val old = Any(); var displayed = old; var correction = "sqrt(16)"
        runCatching {
            PhotoStaging.prepare<Any>(4, { 4 }, { throw IOException("bad image") }) {
                displayed = it; correction = ""
            }
        }.exceptionOrNull().shouldBeInstanceOf<IOException>()
        (displayed === old) shouldBe true
        correction shouldBe "sqrt(16)"
    }

    "a superseded import cannot publish after its decoding finishes" {
        withTimeout(5000) { supervisorScope {
            val old = Any(); val replacement = Any()
            var revision = 1; var displayed = old; var correction = "12+34"
            val finish = CompletableDeferred<Unit>()
            val importing = async(start = CoroutineStart.UNDISPATCHED) {
                PhotoStaging.prepare(1, { revision }, { finish.await(); replacement }) {
                    displayed = it; correction = ""
                }
            }
            revision = 2
            finish.complete(Unit)
            importing.await() shouldBe null
            (displayed === old) shouldBe true
            correction shouldBe "12+34"
        } }
    }

    "even a decoder swallowing cancellation cannot publish a cancelled import" {
        withTimeout(5000) { supervisorScope {
            val old = Any(); val replacement = Any()
            var displayed = old; var correction = "1/3"; var decoderReturned = false
            val importing = async(start = CoroutineStart.UNDISPATCHED) {
                PhotoStaging.prepare(1, { 1 }, {
                    try { delay(Long.MAX_VALUE) } catch (_: CancellationException) { }
                    decoderReturned = true
                    replacement
                }) { displayed = it; correction = "" }
            }
            importing.cancelAndJoin()
            decoderReturned shouldBe true
            (displayed === old) shouldBe true
            correction shouldBe "1/3"
        } }
    }

    "already obsolete work does not decode or replace a current draft" {
        var decoded = false; var published = false
        PhotoStaging.prepare(1, { 2 }, { decoded = true; Any() }) { published = true } shouldBe null
        decoded shouldBe false
        published shouldBe false
    }

    "a cancelled blocking preparation retains the shared lane until its actual completion" {
        withTimeout(5000) { supervisorScope {
            val finish = CompletableDeferred<Unit>()
            var firstCompleted = false; var secondStarted = false
            val first = async(start = CoroutineStart.UNDISPATCHED) {
                PhotoStaging.imageOperation { withContext(NonCancellable) {
                    finish.await(); firstCompleted = true; "first"
                } }
            }
            try {
                first.cancel()
                val second = async(start = CoroutineStart.UNDISPATCHED) {
                    PhotoStaging.imageOperation { secondStarted = true; "second" }
                }
                secondStarted shouldBe false
                firstCompleted shouldBe false
                finish.complete(Unit)
                first.join()
                second.await() shouldBe "second"
                firstCompleted shouldBe true
            } finally { finish.complete(Unit); first.cancelAndJoin() }
        } }
    }

    "a cancelled queued preparation never enters native image work" {
        withTimeout(5000) { supervisorScope {
            val finish = CompletableDeferred<Unit>()
            var cancelledStarted = false; var nextStarted = false
            val active = async(start = CoroutineStart.UNDISPATCHED) {
                PhotoStaging.imageOperation { finish.await(); "active" }
            }
            try {
                val cancelled = async(start = CoroutineStart.UNDISPATCHED) {
                    PhotoStaging.imageOperation { cancelledStarted = true; "cancelled" }
                }
                cancelled.cancelAndJoin()
                val next = async(start = CoroutineStart.UNDISPATCHED) {
                    PhotoStaging.imageOperation { nextStarted = true; "next" }
                }
                cancelledStarted shouldBe false; nextStarted shouldBe false
                finish.complete(Unit)
                active.await() shouldBe "active"
                next.await() shouldBe "next"
                cancelledStarted shouldBe false
            } finally { finish.complete(Unit); active.cancelAndJoin() }
        } }
    }

    "image memory refusal keeps the old draft and releases the lane for a retry" {
        val old = Any(); var displayed = old; var correction = "12+34"
        val problem = runCatching {
            PhotoStaging.prepare<Any>(1, { 1 }, { throw OutOfMemoryError("synthetic allocation failure") }) {
                displayed = it; correction = ""
            }
        }.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
        problem.message!! shouldContain "memory"
        problem.message!! shouldContain "previous draft"
        (displayed === old) shouldBe true
        correction shouldBe "12+34"
        PhotoStaging.imageOperation { "retry" } shouldBe "retry"
    }

    "publication memory errors and unrelated errors are not reclassified as image refusals" {
        runCatching {
            PhotoStaging.prepare(1, { 1 }, { Any() }) { throw OutOfMemoryError("publication") }
        }.exceptionOrNull().shouldBeInstanceOf<OutOfMemoryError>()
        runCatching { PhotoStaging.imageOperation { throw AssertionError("unrelated error") } }
            .exceptionOrNull().shouldBeInstanceOf<AssertionError>()
        PhotoStaging.imageOperation { "lane released" } shouldBe "lane released"
    }
})
