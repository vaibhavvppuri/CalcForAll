package com.vaibhav.calc.android

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PendingLifecycleSpec : StringSpec({
    "replacement load preserves the latest lifecycle checkpoint queued behind slow older IO" {
        val gate = SessionWriteCoordinator(); val original = gate.openOwner()
        val priorTicket = gate.ticket(original)
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        var disk = "initial"
        try {
            val oldWrite = executor.submit<Boolean> {
                gate.write(original, priorTicket) {
                    entered.countDown()
                    check(finish.await(5, TimeUnit.SECONDS))
                    disk = "older"
                }
            }
            entered.await(3, TimeUnit.SECONDS) shouldBe true
            val pending = gate.registerLifecycle(original) { disk = "latest draft and history" }
            val replacement = gate.openOwner()
            val loaded = executor.submit<String> { gate.read(replacement) { disk } }
            finish.countDown()
            oldWrite.get(3, TimeUnit.SECONDS) shouldBe true
            loaded.get(3, TimeUnit.SECONDS) shouldBe "latest draft and history"
            gate.drainLifecycle(pending) shouldBe false
        } finally {
            finish.countDown()
            executor.shutdownNow()
        }
    }

    "async checkpoint drain racing replacement load executes the checkpoint only once" {
        val gate = SessionWriteCoordinator(); val original = gate.openOwner()
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        var writes = 0; var disk = "old"
        try {
            val token = gate.registerLifecycle(original) {
                writes++
                entered.countDown()
                check(finish.await(5, TimeUnit.SECONDS))
                disk = "latest"
            }
            val drain = executor.submit<Boolean> { gate.drainLifecycle(token) }
            entered.await(3, TimeUnit.SECONDS) shouldBe true
            val replacement = gate.openOwner()
            val loaded = executor.submit<String> { gate.read(replacement) { disk } }
            finish.countDown()
            drain.get(3, TimeUnit.SECONDS) shouldBe true
            loaded.get(3, TimeUnit.SECONDS) shouldBe "latest"
            writes shouldBe 1
        } finally {
            finish.countDown()
            executor.shutdownNow()
        }
    }

    "new ordinary snapshots reviewed saves and explicit deletion supersede pending checkpoints" {
        for (next in listOf("ordinary", "reviewed", "delete")) {
            val gate = SessionWriteCoordinator(); val owner = gate.openOwner()
            var disk = "old"
            val pending = gate.registerLifecycle(owner) { disk = "obsolete lifecycle snapshot" }
            when (next) {
                "ordinary" -> gate.write(owner, gate.ticket(owner)) { disk = next }
                "reviewed" -> {
                    val reservation = gate.reserve(owner)
                    gate.registerLifecycle(owner) { disk = "forbidden lifecycle snapshot" } shouldBe -1L
                    gate.writeReserved(owner, reservation) { disk = next }
                    gate.release(owner, reservation)
                }
                else -> gate.delete(owner) { disk = next }
            }
            gate.drainLifecycle(pending) shouldBe false
            gate.read(gate.openOwner()) { disk } shouldBe next
        }
    }

    "completion of an older admitted checkpoint cannot clear a newer registered checkpoint" {
        val gate = SessionWriteCoordinator(); val owner = gate.openOwner()
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        var disk = "initial"
        try {
            val first = gate.registerLifecycle(owner) {
                entered.countDown()
                check(finish.await(5, TimeUnit.SECONDS))
                disk = "first"
            }
            val writer = executor.submit<Boolean> { gate.drainLifecycle(first) }
            entered.await(3, TimeUnit.SECONDS) shouldBe true
            val latest = gate.registerLifecycle(owner) { disk = "latest" }
            finish.countDown()
            writer.get(3, TimeUnit.SECONDS) shouldBe true
            gate.drainLifecycle(latest) shouldBe true
            disk shouldBe "latest"
        } finally {
            finish.countDown()
            executor.shutdownNow()
        }
    }

    "failed pending IO remains retryable and replacement load cannot silently return older data" {
        val gate = SessionWriteCoordinator(); val original = gate.openOwner()
        var unavailable = true; var disk = "old"; var reads = 0
        val token = gate.registerLifecycle(original) {
            if (unavailable) throw IOException("storage unavailable")
            disk = "latest"
        }
        shouldThrow<IOException> { gate.drainLifecycle(token) }
        val replacement = gate.openOwner()
        shouldThrow<IOException> { gate.read(replacement) { reads++; disk } }
        reads shouldBe 0
        disk shouldBe "old"
        unavailable = false
        gate.read(replacement) { reads++; disk } shouldBe "latest"
        reads shouldBe 1
        gate.drainLifecycle(token) shouldBe false
        shouldThrow<IOException> { gate.registerLifecycle(original) { disk = "stale owner" } }
    }
})
