package kube

import cats.data.OptionT
import cats.effect.IO
import skuber.Scale
import skuber.apps.v1.Deployment

trait KubernetesAPI {
  def getDeploymentByName(
      name: String,
      nameSpace: Option[String]
  ): OptionT[IO, Deployment]

  def getCurrentReplicasByName(
      name: String,
      nameSpace: Option[String]
  ): OptionT[IO, Int]

  def scaleUp(deployment: Deployment): OptionT[IO, Scale]

  def scaleDown(deployment: Deployment): OptionT[IO, Scale]
}
