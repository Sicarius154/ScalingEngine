package domain

import skuber.apps.v1.Deployment

case class TargetAndDeployment(target: ScalingCandidate, deployment: Deployment)
