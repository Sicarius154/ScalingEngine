package application
import cats.effect.IO
import domain.ServiceDefinition
import io.circe.parser.decode
import org.slf4j.{Logger, LoggerFactory}

import scala.io.Source

object HardcodedServiceDefinitionLoader extends ServiceDefinitionLoader {
  private val log: Logger = LoggerFactory.getLogger(getClass.getSimpleName)

  /**
   * Loads all service definitions
   * @param source resource source file
   * @return
   */
  override def loadAll(source: String): IO[Seq[ServiceDefinition]] =
    IO {
      Source
        .fromResource(source)
        .getLines()
        .map(decode[ServiceDefinition](_))
        .toSeq
        .filter(_.isRight)
        .map(item => item.toOption.get)
    }
}

