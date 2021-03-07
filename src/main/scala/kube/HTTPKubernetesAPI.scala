package kube

import skuber.apps.v1.Deployment
import cats.data.OptionT
import cats.effect.{IO, Resource, ContextShift}
import skuber.Scale
import skuber._
import skuber.json.format._
import akka.actor.ActorSystem
import akka.dispatch.Dispatcher
import cats.implicits.catsSyntaxFlatMapOps
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Success, Failure}

class HTTPKubernetesAPI(implicit
    cs: ContextShift[IO],
    k8s: K8SRequestContext,
    system: ActorSystem,
    dispatcher: ExecutionContextExecutor
) extends KubernetesAPI {
  private val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)
  private val maxReplicas = 3 // TODO: Load this from target definitions

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
        deployment => IO.pure(Some(deployment))
      )

  override def getCurrentReplicasByName(
      name: String,
      namespace: String = "default"
  ): OptionT[IO, Int] =
    OptionT(
      getDeploymentByName(name, namespace).map {
        case Some(deployment) => deployment.status.map(_.replicas)
        case None => {
          log.error(s"Error retrieving replicas for deployment ${name}")
          None
        }
      }
    )

  //TODO: DO not need to update when condition not true.
  override def scaleUp(
      deployment: Deployment,
      currentReplicas: Int
  ): IO[Unit] =
    IO.fromFuture {
        IO {
          val newScale =
            if (currentReplicas < maxReplicas) currentReplicas + 1
            else currentReplicas

          val updatedDeployment = deployment.withReplicas(newScale)

          k8s.update(updatedDeployment).map(_ => ())
        }
      }
      .redeemWith(
        _ =>
          IO(log.error(s"Error when scaling ${deployment.name} up")) >> IO.unit,
        _ => IO.unit
      )

  override def scaleDown(
      deployment: Deployment,
      currentReplicas: Int
  ): IO[Unit] =
    IO.fromFuture {
        IO {
          val newScale =
            if (currentReplicas > 1) currentReplicas - 1
            else currentReplicas

          val updatedDeployment = deployment.withReplicas(newScale)

          k8s.update(updatedDeployment).map(_ => ())
        }
      }
      .redeemWith(
        _ =>
          IO(log.error(s"Error when scaling ${deployment.name} up")) >> IO.unit,
        _ => IO.unit
      )
}

object HTTPKubernetesAPI {
  private[kube] def apply()(implicit
      cs: ContextShift[IO],
      k8s: K8SRequestContext,
      system: ActorSystem,
      dispatcher: ExecutionContextExecutor
  ): HTTPKubernetesAPI = new HTTPKubernetesAPI()

  def asResource()(implicit
      cs: ContextShift[IO],
      k8s: K8SRequestContext,
      system: ActorSystem,
      dispatcher: ExecutionContextExecutor
  ): Resource[IO, HTTPKubernetesAPI] =
    Resource.make { IO(HTTPKubernetesAPI()) } { _ => IO(k8s.close) }
}
