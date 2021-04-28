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

  //Implict values for Akka and Skuber
  implicit private val system: ActorSystem = ActorSystem()
  implicit private val dispatcher: ExecutionContextExecutor = system.dispatcher
  implicit private val k8s: KubernetesClient = k8sInit


  /**
   * Starts the application
   *
   * @return
   */
  def execute(): IO[ExitCode] =
    withKubernetesAPI() { kubernetesAPI =>
      val conf = loadConfig
      for {
        serviceDefinitions <-
          HardcodedServiceDefinitionLoader.loadAll(conf.serviceDefinitions.path)
        generatedServiceMaxReplicaMap = serviceMaxReplicaMap(serviceDefinitions)
        generatedServiceMinReplicaMap = serviceMinReplicaMap(serviceDefinitions)
        serviceDependencyGraph = ServiceDependencyGraph(
          serviceDefinitions,
          generatedServiceMaxReplicaMap,
          generatedServiceMinReplicaMap
        )
        res <- PrometheusAnomalyStream(
          serviceDependencyGraph,
          conf.streamConfig,
          kubernetesAPI,
          generatedServiceMaxReplicaMap,
          generatedServiceMinReplicaMap
        ).runForever()
      } yield res
    }

  /**
   * Maps each service to the maximum number of replicas
   * @param definitions
   * @return
   */
  private def serviceMaxReplicaMap(definitions: Seq[ServiceDefinition]): Map[String, Int] =
    definitions.map { service =>
      service.serviceName -> service.maxReplicas
    }.toMap

  /**
   * Maps each service to the minimum number of replicas
   * @param definitions
   * @return
   */
  private def serviceMinReplicaMap(definitions: Seq[ServiceDefinition]): Map[String, Int] =
    definitions.map { service =>
      service.serviceName -> service.minReplicas
    }.toMap

  /**
   * Executes a given function in the context of a kubernetesAPI using HOF's
   * @param f
   * @return
   */
  private def withKubernetesAPI(
  )(f: HTTPKubernetesAPI => IO[ExitCode]): IO[ExitCode] =
    HTTPKubernetesAPI
      .asResource()
      .use(f(_))

  private def loadConfig: Config =
    ConfigSource.default.loadOrThrow[Config]
}
