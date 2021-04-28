package domain

case class Anomaly(target: String, dependencies: Seq[TargetDependency], maxReplicas: Int, minReplicas: Int, function: String)
