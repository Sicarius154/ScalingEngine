package stream

import cats.effect.{ExitCode, IO}

trait AnomalyStream {
  def runForever(): IO[ExitCode]
}
