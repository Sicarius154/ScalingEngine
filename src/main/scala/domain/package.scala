import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}
import io.circe.{Encoder, Decoder}

package object domain {
  implicit val anomalyMessageDecoder: Decoder[AnomalyMessage] =
    deriveDecoder

  implicit val anomalyMessageEncoder: Encoder[AnomalyMessage] =
    deriveEncoder

  implicit val anomalyMessageMetaDataDecoder: Decoder[AnomalyMessageMetaData] =
    deriveDecoder

  implicit val anomalyMessageMetaDataEncoder: Encoder[AnomalyMessageMetaData] =
    deriveEncoder

  implicit val serviceDefinitionDecoder: Decoder[ServiceDefinition] =
    deriveDecoder

  implicit val serviceDefinitionEncoder: Encoder[ServiceDefinition] =
    deriveEncoder

  implicit val dependencyDecoder: Decoder[Dependency] =
    deriveDecoder

  implicit val dependencyEncoder: Encoder[Dependency] =
    deriveEncoder
}
