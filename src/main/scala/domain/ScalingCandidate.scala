package domain

case class ScalingCandidate(serviceName: String, maxReplicas: Int, minReplicas: Int, function: String)

