package com.vaibhav.calc.android

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SessionWriteCoordinatorSpec : StringSpec({
    "a newer ordinary snapshot explicitly supersedes an older queued write" {
        val gate = SessionWriteCoordinator(); val owner = gate.openOwner()
        val old = gate.ticket(owner); val latest = gate.ticket(owner)
        var disk = "initial"
        gate.write(owner, old) { disk = "old" } shouldBe false
        gate.write(owner, latest) { disk = "latest" } shouldBe true
        disk shouldBe "latest"
    }

    "lifecycle flushes cannot supersede or overwrite a reserved reviewed save" {
        val gate = SessionWriteCoordinator(); val owner = gate.openOwner()
        val queued = gate.ticket(owner)
        val reviewed = gate.reserve(owner)
        var disk = "prior"
        val stopFlush = gate.ticket(owner)
        gate.write(owner, queued) { disk = "queued old" } shouldBe false
        gate.write(owner, stopFlush) { disk = "stopped old" } shouldBe false
        gate.writeReserved(owner, reviewed) { disk = "reviewed" }
        // Reservation persists until the UI has published the same snapshot.
        gate.write(owner, gate.ticket(owner)) { disk = "disposed old" } shouldBe false
        disk shouldBe "reviewed"
        gate.release(owner, reviewed)
        gate.write(owner, gate.ticket(owner)) { disk = "published reviewed" } shouldBe true
        disk shouldBe "published reviewed"
    }

    "activity recreation invalidates old stores including their reserved save" {
        val gate = SessionWriteCoordinator(); val oldOwner = gate.openOwner()
        val reserved = gate.reserve(oldOwner)
        val newOwner = gate.openOwner()
        var writes = 0
        shouldThrow<IOException> { gate.writeReserved(oldOwner, reserved) { writes++ } }
        shouldThrow<IOException> { gate.ticket(oldOwner) }
        gate.release(oldOwner, reserved)
        gate.write(newOwner, gate.ticket(newOwner)) { writes++ } shouldBe true
        writes shouldBe 1
    }

    "an IO failure is not acknowledged and a released reservation permits retry" {
        val gate = SessionWriteCoordinator(); val owner = gate.openOwner()
        val first = gate.reserve(owner)
        try {
            shouldThrow<IOException> { gate.writeReserved(owner, first) { throw IOException("full") } }
        } finally { gate.release(owner, first) }
        val retry = gate.reserve(owner)
        var disk = "prior"
        gate.writeReserved(owner, retry) { disk = "reviewed" }
        disk shouldBe "reviewed"
        gate.release(owner, retry)
        shouldThrow<IOException> { gate.writeReserved(owner, retry) { disk = "duplicate" } }
        disk shouldBe "reviewed"
    }

    "delete invalidates queued snapshots and refuses an in-flight reviewed save" {
        val gate = SessionWriteCoordinator(); val owner = gate.openOwner()
        val ticket = gate.ticket(owner)
        var disk = "prior"
        gate.delete(owner) { disk = "deleted" }
        gate.write(owner, ticket) { disk = "old" } shouldBe false
        disk shouldBe "deleted"
        val reviewed = gate.reserve(owner)
        shouldThrow<IOException> { gate.delete(owner) { disk = "deleted during save" } }
        gate.release(owner, reviewed)
        disk shouldBe "deleted"
    }

    "slow disk IO does not block lifecycle tickets or replacement owner activation" {
        val gate = SessionWriteCoordinator(); val oldOwner = gate.openOwner()
        val reservation = gate.reserve(oldOwner)
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(3)
        var disk = "prior"
        try {
            val writer = executor.submit {
                gate.writeReserved(oldOwner, reservation) {
                    entered.countDown()
                    check(finish.await(5, TimeUnit.SECONDS)) { "Test did not release simulated IO" }
                    disk = "reviewed"
                }
            }
            entered.await(3, TimeUnit.SECONDS) shouldBe true
            val replacement = executor.submit<Long> {
                gate.ticket(oldOwner) shouldBe -1L
                val next = gate.openOwner()
                gate.ticket(next) // This short state operation must also finish during IO.
                next
            }.get(3, TimeUnit.SECONDS)
            val restored = executor.submit<String> { gate.read(replacement) { disk } }
            finish.countDown()
            writer.get(3, TimeUnit.SECONDS)
            restored.get(3, TimeUnit.SECONDS) shouldBe "reviewed"
            gate.write(oldOwner, reservation) { disk = "obsolete" } shouldBe false
            disk shouldBe "reviewed"
        } finally {
            finish.countDown()
            executor.shutdownNow()
        }
    }

    "an ordinary snapshot already being written finishes before a newer queued snapshot" {
        val gate = SessionWriteCoordinator(); val owner = gate.openOwner()
        val firstTicket = gate.ticket(owner)
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        var disk = "prior"
        try {
            val first = executor.submit<Boolean> {
                gate.write(owner, firstTicket) {
                    entered.countDown()
                    check(finish.await(5, TimeUnit.SECONDS)) { "Test did not release simulated IO" }
                    disk = "first"
                }
            }
            entered.await(3, TimeUnit.SECONDS) shouldBe true
            val latestTicket = executor.submit<Long> { gate.ticket(owner) }.get(3, TimeUnit.SECONDS)
            val latest = executor.submit<Boolean> { gate.write(owner, latestTicket) { disk = "latest" } }
            finish.countDown()
            first.get(3, TimeUnit.SECONDS) shouldBe true
            latest.get(3, TimeUnit.SECONDS) shouldBe true
            disk shouldBe "latest"
        } finally {
            finish.countDown()
            executor.shutdownNow()
        }
    }
})
