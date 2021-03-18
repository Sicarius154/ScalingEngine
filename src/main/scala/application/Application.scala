package application

import akka.actor.ActorSystem
import akka.dispatch.Dispatcher
import config.Config
import cats.effect.{ContextShift, ExitCode, IO, Timer}
import domain.ServiceDefinition
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

  implicit private val system: ActorSystem = ActorSystem()
  implicit private val dispatcher: ExecutionContextExecutor = system.dispatcher
  implicit private val k8s: KubernetesClient = k8sInit

  def execute(): IO[ExitCode] =
    withKubernetesAPI() { kubernetesAPI =>
      val conf = loadConfig
      for {
        serviceDefinitions <-
          HardcodedServiceDefinitionLoader.loadAll(conf.serviceDefinitions.path)
        serviceDependencyGraph = ServiceDependencyGraph(serviceDefinitions)
        generatedServiceMaxReplicaMap = serviceMaxReplicaMap(serviceDefinitions)
        generatedServiceMinReplicaMap = serviceMinReplicaMap(serviceDefinitions)
        res <- PrometheusAnomalyStream(
          serviceDependencyGraph,
          conf.streamConfig,
          kubernetesAPI,
          generatedServiceMaxReplicaMap,
          generatedServiceMinReplicaMap
        ).runForever()
      } yield res
    }

  private def serviceMaxReplicaMap(definitions: Seq[ServiceDefinition]): Map[String, Int] =
    definitions.map { service =>
      service.serviceName -> service.maxReplicas
    }.toMap

  private def serviceMinReplicaMap(definitions: Seq[ServiceDefinition]): Map[String, Int] =
    definitions.map { service =>
      service.serviceName -> service.minReplicas
    }.toMap

  private def withKubernetesAPI(
  )(f: HTTPKubernetesAPI => IO[ExitCode]): IO[ExitCode] =
    HTTPKubernetesAPI
      .asResource()
      .use(f(_))

  private def loadConfig: Config =
    ConfigSource.default.loadOrThrow[Config]
}
