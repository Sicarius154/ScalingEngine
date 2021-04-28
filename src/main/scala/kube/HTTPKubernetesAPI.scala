package kube

import skuber.apps.v1.Deployment
import cats.data.OptionT
import cats.effect.{ContextShift, IO, Resource}
import skuber.Scale
import skuber._
import skuber.json.format._
import akka.actor.ActorSystem
import akka.dispatch.Dispatcher
import cats.implicits.catsSyntaxFlatMapOps
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}


class HTTPKubernetesAPI(implicit
  cs: ContextShift[IO],
  k8s: K8SRequestContext,
  system: ActorSystem,
  dispatcher: ExecutionContextExecutor
) extends KubernetesAPI {
  private val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

  /**
   * Retrieves a Kubernetes deployment using Skuber. Wrapped in IO to capture side effects
   * @param name name of the deployment
   * @param namespace namespace of the deployment
   * @return
   */
  override def getDeploymentByName(
    name: String,
    namespace: String = "default"
  ): IO[Option[Deployment]] =
    IO.fromFuture(IO(k8s.getInNamespace[Deployment](name, namespace)))
      .redeemWith(
        _ =>
          IO(
            log.error(s"Error retrieving deployment with name \'$name\'.")
          ) >> IO.pure(None),
        deployment => IO(Option(deployment))
      )

  /**
   * Increases the number of replicas for a given deployment using Skuber
   * @param deployment Deployment object
   * @param currentReplicas The current number of replicas
   * @return
   */
  override def scaleUp(
    deployment: Deployment,
    currentReplicas: Int
  ): IO[Unit] =
    IO.fromFuture(IO(k8s.update(deployment.withReplicas(currentReplicas + 1)).map(_ => ())))
      .redeemWith(
        _ => IO(log.error(s"Error when scaling ${deployment.name} up")) >> IO.unit,
        _ => IO.unit //Return Unit as we do not care about a result that is not an error
      )

  /**
   * Decreases the number of replicas for a given deployment using Skuber
   * @param deployment Deployment object
   * @param currentReplicas The current number of replicas
   * @return
   */
  override def scaleDown(
    deployment: Deployment,
    currentReplicas: Int
  ): IO[Unit] =
    IO.fromFuture(IO(k8s.update(deployment.withReplicas(currentReplicas - 1)).map(_ => ())))
      .redeemWith(
        _ => IO(log.error(s"Error when scaling ${deployment.name} down")) >> IO.unit,
        _ => IO.unit //Return Unit as we do not care about a result that is not an error
      )
}

object HTTPKubernetesAPI {
  private[kube] def apply()(implicit
    cs: ContextShift[IO],
    k8s: K8SRequestContext,
    system: ActorSystem,
    dispatcher: ExecutionContextExecutor
  ): HTTPKubernetesAPI = new HTTPKubernetesAPI()

  /**
   * Creates and returns a HTTPKubernetesAPI in the form of a Resource
   * @param cs
   * @param k8s
   * @param system
   * @param dispatcher
   * @return
   */
  def asResource()(implicit
    cs: ContextShift[IO],
    k8s: K8SRequestContext,
    system: ActorSystem,
    dispatcher: ExecutionContextExecutor
  ): Resource[IO, HTTPKubernetesAPI] =
    Resource.make(IO(HTTPKubernetesAPI()))(_ => IO(k8s.close))
}
