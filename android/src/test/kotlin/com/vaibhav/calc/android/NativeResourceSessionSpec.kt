package com.vaibhav.calc.android

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class NativeResourceSessionSpec : StringSpec({
    "three sequential tasks reuse one lazily created resource until the batch owner closes" {
        val value = Any(); var created = 0; var closed = 0
        val session = NativeResourceSession({ created++; value }, { closed++ })
        created shouldBe 0
        repeat(3) { val lease = session.acquire(); (lease.resource === value) shouldBe true; lease.release(); closed shouldBe 0 }
        created shouldBe 1
        session.closeOwner(); closed shouldBe 1
        session.closeOwner(); closed shouldBe 1
    }
    "cancelled ownership cannot close a resource while native work retains its lease" {
        var closed = 0
        val session = NativeResourceSession({ Any() }, { closed++ })
        val lease = session.acquire()
        session.closeOwner(); closed shouldBe 0
        shouldThrow<IllegalStateException> { session.acquire() }
        lease.release(); closed shouldBe 1
    }
    "closing before the first task creates no resource" {
        var created = 0; var closed = 0
        val session = NativeResourceSession({ created++; Any() }, { closed++ })
        session.closeOwner()
        shouldThrow<IllegalStateException> { session.acquire() }
        created shouldBe 0; closed shouldBe 0
    }
    "duplicate old completion cannot release a newer native task" {
        var closed = 0
        val session = NativeResourceSession({ Any() }, { closed++ })
        val first = session.acquire(); first.release()
        val second = session.acquire()
        session.closeOwner()
        first.release(); closed shouldBe 0
        second.release(); closed shouldBe 1
        second.release(); closed shouldBe 1
    }
    "factory failure releases admission and a later attempt can create the resource" {
        var attempts = 0; var closed = 0
        val session = NativeResourceSession({ if (++attempts == 1) throw IOException("create failed"); Any() }, { closed++ })
        shouldThrow<IOException> { session.acquire() }
        val retry = session.acquire(); retry.release(); session.closeOwner()
        attempts shouldBe 2; closed shouldBe 1
    }
    "close failures remain observable without reusing or disposing the resource twice" {
        var closed = 0
        val session = NativeResourceSession({ Any() }, { closed++; throw IOException("close failed") })
        val lease = session.acquire(); session.closeOwner()
        shouldThrow<IOException> { lease.release() }
        lease.release(); session.closeOwner()
        closed shouldBe 1
        shouldThrow<IllegalStateException> { session.acquire() }
    }
    "concurrent task admission is refused without changing the admitted lease" {
        var closed = 0
        val session = NativeResourceSession({ Any() }, { closed++ })
        val admitted = session.acquire()
        shouldThrow<IllegalStateException> { session.acquire() }
        session.closeOwner(); closed shouldBe 0
        admitted.release(); closed shouldBe 1
    }
    "owner cancellation during resource creation stays responsive and disposes the completed resource" {
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val closed = AtomicInteger()
        val session = NativeResourceSession({ entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)); Any() }, { closed.incrementAndGet(); Unit })
        try {
            val creating = executor.submit<NativeResourceSession.Lease<Any>> { session.acquire() }
            entered.await(3, TimeUnit.SECONDS) shouldBe true
            executor.submit { session.closeOwner() }.get(3, TimeUnit.SECONDS)
            closed.get() shouldBe 0
            finish.countDown()
            shouldThrow<ExecutionException> { creating.get(3, TimeUnit.SECONDS) }.cause.shouldBeInstanceOf<CancellationException>()
            closed.get() shouldBe 1
            session.closeOwner(); closed.get() shouldBe 1
        } finally { finish.countDown(); executor.shutdownNow() }
    }
})
