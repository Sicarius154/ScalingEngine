package application

import akka.actor.ActorSystem
import akka.dispatch.Dispatcher
import config.Config
import cats.effect.{Timer, IO, ExitCode, ContextShift}
import kube.HTTPKubernetesAPI
import org.slf4j.{Logger, LoggerFactory}
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import servicegraph.ServiceDependencyGraph
import skuber.api.client.KubernetesClient
import skuber.k8sInit
import stream.PrometheusAnomalyStream

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class Application()(implicit
    ec: ExecutionContext,
    cs: ContextShift[IO],
    timer: Timer[IO]
) {
  private val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

  private implicit val system: ActorSystem = ActorSystem()
  private implicit val dispatcher: ExecutionContextExecutor = system.dispatcher
  private implicit val k8s: KubernetesClient = k8sInit

  def execute(): IO[ExitCode] =
    withKubernetesAPI() { kubernetesAPI =>
      val conf = loadConfig
      for {
        serviceDefinitions <-
          HardcodedServiceDefinitionLoader.loadAll(conf.serviceDefinitions.path)
        serviceDependencyGraph = ServiceDependencyGraph(serviceDefinitions)
        res <- PrometheusAnomalyStream(
          serviceDependencyGraph,
          conf.streamConfig,
          kubernetesAPI
        ).runForever()
      } yield res
    }

  private def withKubernetesAPI(
  )(f: HTTPKubernetesAPI => IO[ExitCode]): IO[ExitCode] =
    HTTPKubernetesAPI
      .asResource()
      .use(f(_))

  private def loadConfig: Config =
    ConfigSource.default.loadOrThrow[Config]
}
