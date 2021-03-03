package application

import config.Config
import cats.effect.{Timer, IO, ExitCode, ContextShift}
import org.slf4j.{Logger, LoggerFactory}
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import servicegraph.ServiceDependencyGraph
import stream.PrometheusAnomalyStream

import scala.concurrent.ExecutionContext

class Application()(implicit
    ec: ExecutionContext,
    cs: ContextShift[IO],
    timer: Timer[IO]
) {
  private val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

  def execute(): IO[ExitCode] = {
    val conf = loadConfig
    for {
      serviceDefinitions <-
        HardcodedServiceDefinitionLoader.loadAll(conf.serviceDefinitions.path)
      serviceDependencyGraph = ServiceDependencyGraph(serviceDefinitions)
      res <- PrometheusAnomalyStream(serviceDependencyGraph, conf.streamConfig)
        .runForever()
    } yield res
  }

  private def loadConfig: Config =
    ConfigSource.default.loadOrThrow[Config]
}
