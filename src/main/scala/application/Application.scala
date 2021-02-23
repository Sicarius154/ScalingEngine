package application

import config.Config
import cats.effect.{ContextShift, Timer, ExitCode, IO}
import org.slf4j.{Logger, LoggerFactory}
import pureconfig.ConfigSource
import pureconfig.generic.auto._

import scala.concurrent.ExecutionContext

class Application()(implicit
    ec: ExecutionContext,
    cs: ContextShift[IO],
    timer: Timer[IO]
) {
  private val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

  def execute(): IO[ExitCode] = IO(ExitCode.Success)

  private def loadConfig: Config =
    ConfigSource.default.loadOrThrow[Config]
}
