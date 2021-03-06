package stream

import config.StreamConfig
import fs2.kafka.{
  ConsumerSettings,
  AutoOffsetReset,
  CommittableConsumerRecord,
  KafkaConsumer
}
import cats.effect.{ContextShift, Async, Timer, ExitCode, IO, Sync}
import cats.syntax._
import cats.implicits._
import domain.{
  ScalingTarget,
  TargetWithDependencies,
  AnomalyMessage,
  KeyedAnomalyMessage
}
import fs2.Stream
import io.circe
import org.slf4j.{Logger, LoggerFactory}
import io.circe.parser._
import io.circe.generic.auto._
import io.circe.syntax._
import kube.KubernetesAPI
import servicegraph.ServiceDependencyGraph

import scala.concurrent.duration._

class PrometheusAnomalyStream(
    serviceGraph: ServiceDependencyGraph,
    streamConfig: StreamConfig,
    kubernetesAPI: KubernetesAPI
)(implicit
    cs: ContextShift[IO],
    timer: Timer[IO],
    sync: Sync[IO],
    async: Async[IO]
) extends AnomalyStream {
  private val log: Logger =
    LoggerFactory.getLogger(getClass.getSimpleName)

  val consumerSettings: ConsumerSettings[IO, String, String] =
    ConsumerSettings[IO, String, String]
      .withAutoOffsetReset(AutoOffsetReset.Latest)
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
      .mapAsync(streamConfig.streamParallelismMax)(inferDependencies)
      .mapAsync(streamConfig.streamParallelismMax)(scaleTargets)

  private[stream] def scaleTargets(
      targetOpt: Option[TargetWithDependencies]
  ): IO[Unit] =
    targetOpt match {
      case Some(target: TargetWithDependencies) => {
        for {
          currentReplicas <-
            kubernetesAPI.getCurrentReplicasByName(target.target).value
          _ = currentReplicas match {
            case Some(value) =>
              log.info(
                s"Scaling ${target.target} which currently has ${value} replicas."
              )
            case None =>
              log.error(
                s"Unable to retrieve number of replicas for ${target.target}"
              )
          }
        } yield ()
      }
      case None => {
        log.error(
          s"Error scaling target, TargetWithDependencies was None, ensure Kafka messages are valid."
        )
        IO.unit
      }
    }

  private[stream] def processKafkaRecord(
      committableRecord: CommittableConsumerRecord[IO, String, String]
  ): IO[Option[KeyedAnomalyMessage]] = {
    decode[AnomalyMessage](committableRecord.record.value) match {
      case Right(value) =>
        IO.pure(
          Option(KeyedAnomalyMessage(committableRecord.record.key, value))
        )
      case Left(_) => {
        log.error(
          s"Error decoding record with key ${committableRecord.record.key} from topic ${streamConfig.topic}"
        )
        IO.pure(None)
      }
    }
  }

  private[stream] def inferDependencies(
      keyedAnomalyMessageOpt: Option[KeyedAnomalyMessage]
  ): IO[Option[TargetWithDependencies]] =
    IO {
      keyedAnomalyMessageOpt
        .map { keyedAnomalyMessage =>
          TargetWithDependencies(
            keyedAnomalyMessage.message.targetAppName,
            serviceGraph.inferTargets(keyedAnomalyMessage.message.targetAppName)
          )
        }
    }
}

object PrometheusAnomalyStream {
  def apply(
      serviceGraph: ServiceDependencyGraph,
      streamConfig: StreamConfig,
      kubernetesAPI: KubernetesAPI
  )(implicit cs: ContextShift[IO], timer: Timer[IO]): PrometheusAnomalyStream =
    new PrometheusAnomalyStream(serviceGraph, streamConfig, kubernetesAPI)
}
