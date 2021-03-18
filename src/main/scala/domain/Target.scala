package domain

case class Target(target: String, dependencies: Option[Seq[ScalingTargetDefinition]], maxReplicas: Int, minReplicas: Int, function: String)
