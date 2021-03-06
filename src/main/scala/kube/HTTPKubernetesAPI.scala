package kube

import skuber.apps.v1.Deployment
import cats.data.OptionT
import cats.effect.{IO, Resource, ContextShift}
import skuber.Scale
import skuber._
import skuber.json.format._
import akka.actor.ActorSystem
import akka.dispatch.Dispatcher

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Success, Failure}

class HTTPKubernetesAPI(implicit
    cs: ContextShift[IO],
    k8s: K8SRequestContext,
    system: ActorSystem,
    dispatcher: ExecutionContextExecutor
) extends KubernetesAPI {
  private val maxReplicas = 3 // TODO: Load this from target definitions

  override def getDeploymentByName(
      name: String,
      namespace: String = "default"
  ): IO[Deployment] =
    IO.fromFuture(IO(k8s.getInNamespace[Deployment](name, namespace)))

  override def getCurrentReplicasByName(
      name: String,
      namespace: String = "default"
  ): OptionT[IO, Int] =
    OptionT(
      getDeploymentByName(name, namespace).map(_.status.map(_.replicas))
    ).map(replicas => replicas)

  override def scaleUp(deployment: Deployment): OptionT[IO, Scale] =
    OptionT.fromOption(None)

  override def scaleDown(deployment: Deployment): OptionT[IO, Scale] =
    OptionT.fromOption(None)
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
