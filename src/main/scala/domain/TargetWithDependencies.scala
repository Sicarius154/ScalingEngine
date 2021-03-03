package domain

case class TargetWithDependencies (target: String, dependencies: Option[Seq[ScalingTarget]])
