package servicegraph

import domain.{ ServiceDefinition, ScalingTargetDefinition}

class ServiceDependencyGraph(relationships: Map[String, Seq[ScalingTargetDefinition]]) {
  def inferTargets(serviceName: String): Option[Seq[ScalingTargetDefinition]] =
    relationships.get(serviceName)
}

object ServiceDependencyGraph {
  def apply(
      serviceDefinitions: Seq[ServiceDefinition]
  ): ServiceDependencyGraph = {
    val relationships: Map[String, Seq[ScalingTargetDefinition]] =
      mapServicesToDependencies(serviceDefinitions)

    new ServiceDependencyGraph(relationships)
  }

  def mapServicesToDependencies(
      serviceDefinitions: Seq[ServiceDefinition]
  ): Map[String, Seq[ScalingTargetDefinition]] =
    serviceDefinitions.map { service =>
      service.serviceName -> service.dependencies.map(dependency =>
        ScalingTargetDefinition(dependency.serviceName, dependency.scaleFactor)
      )
    }.toMap
}
