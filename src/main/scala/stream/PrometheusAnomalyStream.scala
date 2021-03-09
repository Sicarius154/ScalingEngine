package stream

import config.StreamConfig
import fs2.kafka.{AutoOffsetReset, CommittableConsumerRecord, ConsumerSettings, KafkaConsumer}
import cats.effect.{Async, ContextShift, ExitCode, IO, Sync, Timer}
import cats._
import cats.syntax._
import cats.implicits._
import domain.{AnomalyMessage, KeyedAnomalyMessage, Target, TargetAndDeployment}
import fs2.Stream
import io.circe
import org.slf4j.{Logger, LoggerFactory}
import io.circe.parser._
import io.circe.generic.auto._
import io.circe.syntax._
import kube.KubernetesAPI
import servicegraph.ServiceDependencyGraph

class PrometheusAnomalyStream(
  serviceGraph: ServiceDependencyGraph,
  streamConfig: StreamConfig,
  kubernetesAPI: KubernetesAPI,
  serviceMaxReplicaMap: Map[String, Int]
)(implicit cs: ContextShift[IO], timer: Timer[IO], sync: Sync[IO], async: Async[IO])
    extends AnomalyStream {
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
      .mapAsync(streamConfig.streamParallelismMax)(getCurrentDeployment)
      .mapAsync(streamConfig.streamParallelismMax)(scale)

  private[stream] def scale(
    targetAndDeploymentOpt: Option[TargetAndDeployment]
  ): IO[Unit] =
    targetAndDeploymentOpt match {
      case Some(targetAndDeployment) =>
        IO(
          log.info(s"Scaling ${targetAndDeployment.target.target} up")
        ) >> kubernetesAPI.scaleUp(
          targetAndDeployment.deployment,
          targetAndDeployment.deployment.spec.get.replicas.get
        )
      case None => IO.unit
    }

  private[stream] def getCurrentDeployment(
    targetOpt: Option[Target]
  ): IO[Option[TargetAndDeployment]] =
    targetOpt match {
      case Some(target) => {
        kubernetesAPI
          .getDeploymentByName(target.target)
          .flatMap { deployment =>
            IO.pure((target.some, deployment).mapN((target, deployment) => TargetAndDeployment(target, deployment)))
          }
      }
      case None => IO.pure(None)
    }

  private[stream] def processKafkaRecord(
    committableRecord: CommittableConsumerRecord[IO, String, String]
  ): IO[Option[KeyedAnomalyMessage]] = {
    decode[AnomalyMessage](committableRecord.record.value) match {
      case Right(value) =>
        IO.pure(
          Option(KeyedAnomalyMessage(committableRecord.record.key, value))
        )
      case Left(_) =>
        IO {
          log.error(s"Error decoding record with key ${committableRecord.record.key} from topic ${streamConfig.topic}")
          None
        }
    }
  }

  private[stream] def inferDependencies(
    keyedAnomalyMessageOpt: Option[KeyedAnomalyMessage]
  ): IO[Option[Target]] =
    IO {
      keyedAnomalyMessageOpt
        .map { keyedAnomalyMessage =>
          Target(
            keyedAnomalyMessage.message.targetAppName,
            serviceGraph.inferTargets(
              keyedAnomalyMessage.message.targetAppName
            ),
            maxReplicaByService(keyedAnomalyMessage.message.targetAppName)
          )
        }
    }

  private def maxReplicaByService(service: String): Int =
    serviceMaxReplicaMap.getOrElse(
      service, {
        log.error(
          s"Could not retrieve maximum replicas for $service. Defaulting to 1"
        )
        1
      }
    )
}

object PrometheusAnomalyStream {
  def apply(
    serviceGraph: ServiceDependencyGraph,
    streamConfig: StreamConfig,
    kubernetesAPI: KubernetesAPI,
    serviceMaxReplicaMap: Map[String, Int]
  )(implicit cs: ContextShift[IO], timer: Timer[IO]): PrometheusAnomalyStream =
    new PrometheusAnomalyStream(serviceGraph, streamConfig, kubernetesAPI)
}
