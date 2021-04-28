package domain

case class TargetDependency(target: String, maxReplicas: Int, minReplicas: Int)