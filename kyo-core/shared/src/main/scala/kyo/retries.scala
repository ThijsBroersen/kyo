package kyo

import scala.concurrent.duration.*
import scala.util.*

object Retries:

    case class Policy(backoff: Int => Duration, limit: Int):
        def exponential(
            startBackoff: Duration,
            factor: Int = 2,
            maxBackoff: Duration = Duration.Inf
        ): Policy =
            backoff { i =>
                (startBackoff * factor * (i + 1)).min(maxBackoff)
            }
        def backoff(f: Int => Duration): Policy =
            copy(backoff = f)
        def limit(v: Int): Policy =
            copy(limit = v)
    end Policy

    object Policy:
        val default = Policy(_ => Duration.Zero, 3)

    def apply[T: Flat, S](policy: Policy)(v: => T < S): T < (Fibers & S) =
        apply(_ => policy)(v)

    def apply[T: Flat, S](builder: Policy => Policy)(v: => T < S): T < (Fibers & S) =
        val b = builder(Policy.default)
        def loop(attempt: Int): T < (Fibers & S) =
            IOs.attempt[T, S](v).map {
                case Failure(ex) =>
                    if attempt < b.limit then
                        Fibers.sleep(b.backoff(attempt)).andThen {
                            loop(attempt + 1)
                        }
                    else
                        IOs.fail(ex)
                case Success(value) =>
                    value
            }
        loop(0)
    end apply
end Retries
