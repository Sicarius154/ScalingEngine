package kube

import cats.data.OptionT
import cats.effect.IO
import skuber.Scale
import skuber.apps.v1.Deployment

trait KubernetesAPI {
  def getDeploymentByName(
      name: String,
      namespace: String = "default"
  ): IO[Option[Deployment]]

  def getCurrentReplicasByName(
      name: String,
      namespace: String = "default"
  ): OptionT[IO, Int]

  def scaleUp(deployment: Deployment, currentReplicas: Int): IO[Unit]

  def scaleDown(deployment: Deployment, currentReplicas: Int): IO[Unit]
}
