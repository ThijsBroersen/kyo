package kyoTest

import java.io.Closeable
import kyo.*

class resourcesTest extends KyoTest:

    case class Resource(id: Int, var closes: Int = 0) extends Closeable derives CanEqual:
        var acquires = 0
        def apply() =
            acquires += 1
            this
        def close() = closes += 1
    end Resource

    case class EffectfulResource(id: Int, closes: AtomicInt):
        def close: Unit < IOs =
            closes.incrementAndGet.unit

    end EffectfulResource
    object EffectfulResource:
        def apply(id: Int): EffectfulResource < IOs =
            for
                cl <- Atomics.initInt(0)
            yield EffectfulResource(id, cl)
    end EffectfulResource

    "acquire + close" in run {
        val r1 = Resource(1)
        val r2 = Resource(2)
        IOs.run {
            Resources.run(Resources.acquire(r1()))
        }
        assert(r1.closes == 1)
        assert(r2.closes == 0)
        assert(r1.acquires == 1)
        assert(r2.acquires == 0)
    }

    "acquire + tranform + close" in run {
        val r1 = Resource(1)
        val r2 = Resource(2)
        IOs.run {
            Resources.run(Resources.acquire(r1()).map(_ => assert(r1.closes == 0)))
        }
        assert(r1.closes == 1)
        assert(r2.closes == 0)
        assert(r1.acquires == 1)
        assert(r2.acquires == 0)
    }

    "acquire + effectful tranform + close" in run {
        val r1 = Resource(1)
        val r2 = Resource(2)
        val r =
            IOs.runLazy {
                Resources.run[Int, IOs & Envs[Int]](Resources.acquire(r1()).map { _ =>
                    assert(r1.closes == 0)
                    Envs[Int].get
                })
            }
        assert(r1.closes == 0)
        assert(r2.closes == 0)
        assert(r1.acquires == 1)
        assert(r2.acquires == 0)
        Envs[Int].run(1)(r)
        assert(r1.closes == 1)
        assert(r2.closes == 0)
        assert(r1.acquires == 1)
        assert(r2.acquires == 0)
    }

    "two acquires + close" in run {
        val r1 = Resource(1)
        val r2 = Resource(2)
        IOs.run {
            Resources.run(Resources.acquire(r1()).map(_ => Resources.acquire(r2())))
        }
        assert(r1.closes == 1)
        assert(r2.closes == 1)
        assert(r1.acquires == 1)
        assert(r2.acquires == 1)
    }

    "two acquires + for-comp + close" in run {
        val r1 = Resource(1)
        val r2 = Resource(2)
        val r: Int =
            IOs.run {
                Resources.run {
                    for
                        r1 <- Resources.acquire(r1())
                        i1 = r1.id * 3
                        r2 <- Resources.acquire(r2())
                        i2 = r2.id * 5
                    yield i1 + i2
                }
            }
        assert(r == 13)
        assert(r1.closes == 1)
        assert(r2.closes == 1)
        assert(r1.acquires == 1)
        assert(r2.acquires == 1)
    }

    "two acquires + effectful for-comp + close" in run {
        val r1 = Resource(1)
        val r2 = Resource(2)
        val r: Int < Envs[Int] =
            IOs.runLazy {
                Resources.run[Int, IOs & Envs[Int]] {
                    val io: Int < (Resources & IOs & Envs[Int]) =
                        for
                            r1 <- Resources.acquire(r1())
                            i1 <- Envs[Int].get.map(_ * r1.id)
                            r2 <- Resources.acquire(r2())
                            i2 <- Envs[Int].get.map(_ * r2.id)
                        yield i1 + i2
                    io
                }
            }
        assert(r1.closes == 0)
        assert(r2.closes == 0)
        assert(r1.acquires == 1)
        assert(r2.acquires == 0)
        Envs[Int].run(3)(r)
        assert(r1.closes == 1)
        assert(r2.closes == 1)
        assert(r1.acquires == 1)
        assert(r2.acquires == 1)
    }

    "nested" in run {
        val r1 = Resource(1)
        val r  = IOs.run(Resources.run(Resources.run(Resources.acquire(r1()))))
        assert(r == r1)
        assert(r1.acquires == 1)
        assert(r1.closes == 1)
    }

    "effectful acquireRelease" in run {
        val finalizedResource = IOs.run {
            Resources.run {
                for
                    r          <- Resources.acquireRelease(EffectfulResource(1))(_.close)
                    closeCount <- r.closes.get
                yield
                    assert(closeCount == 0)
                    r
            }
        }
        finalizedResource.closes.get.map(i => assert(i == 1))
    }
end resourcesTest
