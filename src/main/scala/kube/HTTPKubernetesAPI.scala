package kube

import skuber.apps.v1.Deployment
import cats.data.OptionT
import cats.effect.IO
import skuber.Scale

class HTTPKubernetesAPI extends KubernetesAPI {
  override def getDeploymentByName(name: String, nameSpace: Option[String]): OptionT[IO, Deployment] = ???

  override def getCurrentReplicasByName(name: String, nameSpace: Option[String]): OptionT[IO, Int] = ???

  override def scaleUp(deployment: Deployment): OptionT[IO, Scale] = ???

  override def scaleDown(deployment: Deployment): OptionT[IO, Scale] = ???
}
