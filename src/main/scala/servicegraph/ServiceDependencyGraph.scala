package servicegraph

import domain.{ScalingTarget, ServiceDefinition}

class ServiceDependencyGraph(relationships: Map[String, Seq[ScalingTarget]]) {
  def inferTargets(serviceName: String): Option[Seq[ScalingTarget]] =
    relationships.get(serviceName)
}

object ServiceDependencyGraph {
  def apply(
      serviceDefinitions: Seq[ServiceDefinition]
  ): ServiceDependencyGraph = {
    val relationships: Map[String, Seq[ScalingTarget]] =
      mapServicesToDependencies(serviceDefinitions)

    new ServiceDependencyGraph(relationships)
  }

  def mapServicesToDependencies(
      serviceDefinitions: Seq[ServiceDefinition]
  ): Map[String, Seq[ScalingTarget]] =
    serviceDefinitions.map { service =>
      service.serviceName -> service.dependencies.map(dependency =>
        ScalingTarget(dependency.serviceName, dependency.scaleFactor)
      )
    }.toMap
}
