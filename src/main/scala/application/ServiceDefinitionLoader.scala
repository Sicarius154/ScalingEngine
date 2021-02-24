package application

import domain.ServiceDefinition
import cats.effect.IO

trait ServiceDefinitionLoader {
  def loadAll(source: String): IO[Seq[ServiceDefinition]]
}
