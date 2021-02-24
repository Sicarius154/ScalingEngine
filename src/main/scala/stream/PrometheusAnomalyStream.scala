package stream

import config.{Config, StreamConfig}
import fs2.kafka.{
  ConsumerSettings,
  AutoOffsetReset,
  CommittableConsumerRecord,
  KafkaConsumer
}
import cats.effect.{ContextShift, Async, Timer, ExitCode, IO, Sync}
import cats.syntax._
import cats.implicits._
import domain.AnomalyMessage
import fs2.Stream
import io.circe
import org.slf4j.{Logger, LoggerFactory}
import io.circe.parser._
import io.circe.generic.auto._
import io.circe.syntax._

import scala.concurrent.duration._

class PrometheusAnomalyStream(streamConfig: StreamConfig)(implicit
    cs: ContextShift[IO],
    timer: Timer[IO],
    sync: Sync[IO],
    async: Async[IO]
) extends AnomalyStream {
  private val log: Logger =
    LoggerFactory.getLogger(getClass.getSimpleName)

  val consumerSettings: ConsumerSettings[IO, String, String] =
    ConsumerSettings[IO, String, String]
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers(streamConfig.bootstrapServer)
      .withGroupId(streamConfig.consumerGroup)

  override def runForever(): IO[ExitCode] =
    anomalyStream().compile.drain.as(ExitCode.Success)

  private[stream] def anomalyStream(): Stream[IO, Unit] =
    KafkaConsumer
      .stream(consumerSettings)
      .evalTap(_.subscribeTo(streamConfig.topic))
      .flatMap(_.stream)
      .mapAsync(streamConfig.streamParallelismMax)(processKafkaRecord)
      .filter(_.isDefined)
      .parEvalMap(streamConfig.streamParallelismMax)(messageOption => {
        val message = messageOption.get
        log.info(s"Got message: $message")
        IO(message)
      })

  private[stream] def processKafkaRecord(
      committableRecord: CommittableConsumerRecord[IO, String, String]
  ): IO[Option[AnomalyMessage]] = {
    decode[AnomalyMessage](committableRecord.record.value) match {
      case Right(value) => IO.pure(Some(value))
      case Left(_) => {
        log.error(
          s"Error decoding record with key ${committableRecord.record.key} from topic ${streamConfig.topic}"
        )
        IO.pure(None)
      }
    }

  }
}
