package servicegraph

import domain.{ServiceDefinition, TargetDependency}
import org.slf4j.{Logger, LoggerFactory}

class ServiceDependencyGraph(
  relationships: Map[String, Seq[TargetDependency]]
) {
  def inferTargets(serviceName: String): Seq[TargetDependency] =
    relationships.getOrElse(serviceName, Seq.empty[TargetDependency])
}

object ServiceDependencyGraph {
  private val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)
  def apply(
    serviceDefinitions: Seq[ServiceDefinition],
    serviceMaxReplicaMap: Map[String, Int],
    serviceMinReplicaMap: Map[String, Int]
  ): ServiceDependencyGraph = {
    val relationships: Map[String, Seq[TargetDependency]] =
      mapServicesToDependencies(serviceDefinitions, serviceMaxReplicaMap, serviceMinReplicaMap)

    new ServiceDependencyGraph(relationships)
  }

  def mapServicesToDependencies(
    serviceDefinitions: Seq[ServiceDefinition],
    serviceMaxReplicaMap: Map[String, Int],
    serviceMinReplicaMap: Map[String, Int]
  ): Map[String, Seq[TargetDependency]] =
    serviceDefinitions.map { service =>
      service.serviceName -> service.dependencies.map { dependency =>
        val maxReplicas = serviceMaxReplicaMap.get(dependency.serviceName) match {
          case Some(value) => value
          case None =>
            log.error(
              s"Could not retrieve maximum replicas for ${dependency.serviceName}. Defaulting to 1"
            )
            1
        }

        val minReplicas = serviceMinReplicaMap.get(dependency.serviceName) match {
          case Some(value) => value
          case None =>
            log.error(
              s"Could not retrieve minimum replicas for ${dependency.serviceName}. Defaulting to 1"
            )
            1
        }

        TargetDependency(dependency.serviceName, maxReplicas, minReplicas)
      }
    }.toMap
}
