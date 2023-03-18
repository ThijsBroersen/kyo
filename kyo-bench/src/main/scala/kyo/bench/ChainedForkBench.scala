package kyo.bench

import org.openjdk.jmh.annotations._
import scala.util._
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import java.util.ArrayDeque
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.Executors
import kyo.concurrent.scheduler.Scheduler
import org.openjdk.jmh.infra.Blackhole
import kyo.core._
import kyo.concurrent.fibers._
import kyo.ios.IOs
import java.util.concurrent.locks.LockSupport
import cats.effect.kernel.Deferred
import cats.effect.IO

import kyo.bench.Bench

import kyo.concurrent.fibers
import kyo.concurrent.scheduler.Scheduler
import kyo.concurrent.fibers

class ChainedForkBench extends Bench[Int] {

  val depth = 10000

  def catsBench() = {
    import cats.effect.{Deferred, IO}
    import cats.effect.unsafe.IORuntime

    def iterate(deferred: Deferred[IO, Unit], n: Int): IO[Any] =
      if (n <= 0) deferred.complete(())
      else IO.unit.flatMap(_ => iterate(deferred, n - 1).start)

    for {
      deferred <- IO.deferred[Unit]
      _        <- iterate(deferred, depth).start
      _        <- deferred.get
    } yield 0
  }

  def kyoBench() = Fibers.block(Fibers.fork(kyoBenchFiber()))

  override def kyoBenchFiber() = {
    import kyo.core._
    import kyo.concurrent.fibers._
    import kyo.ios._

    def iterate(p: Promise[Unit], n: Int): Unit > IOs =
      if (n <= 0) p.complete(()).unit
      else Fibers.forkFiber(iterate(p, n - 1)).unit

    for {
      p <- Fibers.promise[Unit]
      _ <- Fibers.forkFiber(iterate(p, depth))
      _ <- p.join
    } yield 0
  }

  def zioBench() = {
    import zio.{Promise, ZIO}

    def iterate(promise: Promise[Nothing, Unit], n: Int): ZIO[Any, Nothing, Any] =
      if (n <= 0) promise.succeed(())
      else ZIO.unit.flatMap(_ => iterate(promise, n - 1).forkDaemon)

    for {
      promise <- Promise.make[Nothing, Unit]
      _       <- iterate(promise, depth).forkDaemon
      _       <- promise.await
    } yield 0
  }
}
