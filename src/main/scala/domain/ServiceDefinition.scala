package domain

case class ServiceDefinition (serviceName: String, dependencies: Seq[Dependency])

case class Dependency(serviceName: String, scaleFactor: Int)
